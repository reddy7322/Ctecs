package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.ChainageLogEntry
import com.example.model.DefinedProject
import com.example.model.LogoPosition
import com.example.model.LogoShape
import com.example.model.OverlayConfig
import com.example.model.OverlayTemplate
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "ctecs_user_preferences")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_HIGHWAY_ID = stringPreferencesKey("highway_id")
        val KEY_PACKAGE_SECTION = stringPreferencesKey("package_section")
        val KEY_PROJECT_START_CHAINAGE = stringPreferencesKey("project_start_chainage")
        val KEY_PROJECT_END_CHAINAGE = stringPreferencesKey("project_end_chainage")
        val KEY_WORK_TYPE = stringPreferencesKey("work_type")
        val KEY_ROAD_SIDE = stringPreferencesKey("road_side")
        val KEY_LANE = stringPreferencesKey("lane")
        val KEY_ACTIVITY = stringPreferencesKey("activity")
        val KEY_DESCRIPTION = stringPreferencesKey("description")
        val KEY_INSPECTOR = stringPreferencesKey("inspector")
        val KEY_SEPARATOR = stringPreferencesKey("separator")
        val KEY_DEFINED_PROJECTS = stringPreferencesKey("defined_projects_list")

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

        // Speed & Direction visibility keys (default false)
        val KEY_SHOW_SPEED_PREVIEW = booleanPreferencesKey("show_speed_preview")
        val KEY_INCLUDE_SPEED_CAPTURE = booleanPreferencesKey("include_speed_capture")
        val KEY_SHOW_DIRECTION_PREVIEW = booleanPreferencesKey("show_direction_preview")
        val KEY_INCLUDE_DIRECTION_CAPTURE = booleanPreferencesKey("include_direction_capture")

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
        val KEY_LOGO_SCALE = floatPreferencesKey("logo_scale")
        val KEY_LOGO_ROTATION = floatPreferencesKey("logo_rotation")

        // Typography, Colors & Watermark styling
        val KEY_FONT_SIZE_SP = floatPreferencesKey("font_size_sp")
        val KEY_FONT_SIZE_PRESET = stringPreferencesKey("font_size_preset")
        val KEY_HAS_TEXT_SHADOW = booleanPreferencesKey("has_text_shadow")
        val KEY_TEXT_COLOR_HEX = stringPreferencesKey("text_color_hex")
        val KEY_TEXT_OPACITY = floatPreferencesKey("text_opacity")
        val KEY_FONT_FAMILY = stringPreferencesKey("font_family")
        val KEY_FONT_STYLE = stringPreferencesKey("font_style")
        val KEY_BACKGROUND_STYLE = stringPreferencesKey("background_style")
        val KEY_BACKGROUND_OPACITY = floatPreferencesKey("background_opacity")
        val KEY_BACKGROUND_COLOR_HEX = stringPreferencesKey("background_color_hex")
        val KEY_WATERMARK_POSITION = stringPreferencesKey("watermark_position")
        val KEY_TIME_POSITION = stringPreferencesKey("time_position")
        val KEY_RESOLUTION_OPTION = stringPreferencesKey("resolution_option")
        val KEY_LOCATION_ADDRESS_OPTION = stringPreferencesKey("location_address_option")
        val KEY_CUSTOM_ADDRESS_TEXT = stringPreferencesKey("custom_address_text")
        val KEY_CUSTOM_TAG = stringPreferencesKey("custom_tag")
        val KEY_GPS_ACCURACY_MARGIN_PRESET = stringPreferencesKey("gps_accuracy_margin_preset")
        val KEY_CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
        val KEY_CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")
        val KEY_TEXT_ALIGNMENT = stringPreferencesKey("text_alignment")
        val KEY_LINE_SPACING_FACTOR = floatPreferencesKey("line_spacing_factor")
        val KEY_TEXT_STROKE_WIDTH_DP = floatPreferencesKey("text_stroke_width_dp")

        // Dynamic Location Name Keys
        val KEY_SHOW_LOCATION_NAME = booleanPreferencesKey("show_location_name")
        val KEY_LAST_DETECTED_LOCATION_NAME = stringPreferencesKey("last_detected_location_name")

        // 4-Side Overlay Padding Adjustment Keys
        val KEY_USE_SAME_PADDING_PHOTO_VIDEO = booleanPreferencesKey("use_same_padding_photo_video")
        val KEY_PHOTO_PADDING_TOP_DP = intPreferencesKey("photo_padding_top_dp")
        val KEY_PHOTO_PADDING_BOTTOM_DP = intPreferencesKey("photo_padding_bottom_dp")
        val KEY_PHOTO_PADDING_LEFT_DP = intPreferencesKey("photo_padding_left_dp")
        val KEY_PHOTO_PADDING_RIGHT_DP = intPreferencesKey("photo_padding_right_dp")
        val KEY_VIDEO_PADDING_TOP_DP = intPreferencesKey("video_padding_top_dp")
        val KEY_VIDEO_PADDING_BOTTOM_DP = intPreferencesKey("video_padding_bottom_dp")
        val KEY_VIDEO_PADDING_LEFT_DP = intPreferencesKey("video_padding_left_dp")
        val KEY_VIDEO_PADDING_RIGHT_DP = intPreferencesKey("video_padding_right_dp")

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

        // New functional camera settings
        val KEY_TOUCH_TO_CAPTURE = booleanPreferencesKey("touch_to_capture")
        val KEY_ALWAYS_DISPLAY_ZOOM_SLIDER = booleanPreferencesKey("always_display_zoom_slider")
        val KEY_BATTERY_SAVE_MODE = booleanPreferencesKey("battery_save_mode")
        val KEY_BATTERY_SAVE_BRIGHTNESS = floatPreferencesKey("battery_save_brightness")
        val KEY_EASY_TOGGLE_CAMERAS = booleanPreferencesKey("easy_toggle_cameras")
        val KEY_WHITE_BALANCE = stringPreferencesKey("white_balance")
        val KEY_VIDEO_SEGMENTATION = stringPreferencesKey("video_segmentation")
        val KEY_CUSTOM_FILENAME_PREFIX = stringPreferencesKey("custom_filename_prefix")
        val KEY_FILENAME_FORMAT_PRESET = stringPreferencesKey("filename_format_preset")
        val KEY_CUSTOM_STORAGE_PATH = stringPreferencesKey("custom_storage_path")
        val KEY_AUTO_ROTATE_UI = booleanPreferencesKey("auto_rotate_ui")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_MAP_THEME = stringPreferencesKey("map_theme_preference")
        val KEY_MAP_TYPE = stringPreferencesKey("map_type_preference")
        val KEY_PHOTO_RESOLUTION = stringPreferencesKey("photo_resolution")
        val KEY_VIDEO_RESOLUTION = stringPreferencesKey("video_resolution")
        val KEY_PHOTO_ASPECT_RATIO = stringPreferencesKey("photo_aspect_ratio")
        val KEY_VIDEO_ASPECT_RATIO = stringPreferencesKey("video_aspect_ratio")
        val KEY_GALLERY_VIEW_MODE = stringPreferencesKey("gallery_view_mode")
        val KEY_GALLERY_SORT_OPTION = stringPreferencesKey("gallery_sort_option")
        val KEY_GALLERY_GROUP_OPTION = stringPreferencesKey("gallery_group_option")

        // Smart KMZ Chainage Correction & Manual Override Keys
        val KEY_ROUTE_CORRECTIONS_JSON = stringPreferencesKey("route_chainage_corrections_json")
        val KEY_ROUTE_CORRECTIONS_ENABLED = booleanPreferencesKey("route_chainage_corrections_enabled")
        val KEY_MANUAL_CHAINAGE_OVERRIDE = stringPreferencesKey("manual_chainage_override")
        val KEY_CHAINAGE_AUDIT_LOG_JSON = stringPreferencesKey("chainage_audit_log_json")
    }

    val galleryViewModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GALLERY_VIEW_MODE] ?: "grid"
    }

    val gallerySortOptionFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GALLERY_SORT_OPTION] ?: "Newest first"
    }

    val galleryGroupOptionFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GALLERY_GROUP_OPTION] ?: "None"
    }

    suspend fun updateGalleryPreferences(viewMode: String? = null, sortOption: String? = null, groupOption: String? = null) {
        context.dataStore.edit { prefs ->
            if (viewMode != null) prefs[KEY_GALLERY_VIEW_MODE] = viewMode
            if (sortOption != null) prefs[KEY_GALLERY_SORT_OPTION] = sortOption
            if (groupOption != null) prefs[KEY_GALLERY_GROUP_OPTION] = groupOption
        }
    }

    val themeModeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "system"
    }

    suspend fun updateThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    val mapThemeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_MAP_THEME] ?: "system"
    }

    suspend fun updateMapTheme(theme: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MAP_THEME] = theme
        }
    }

    val mapTypeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_MAP_TYPE] ?: "standard"
    }

    suspend fun updateMapType(type: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MAP_TYPE] = type
        }
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
            speedUnit = com.example.model.SpeedUnit.fromString(prefs[KEY_SPEED_UNIT]),
            touchToCapture = prefs[KEY_TOUCH_TO_CAPTURE] ?: false,
            alwaysDisplayZoomSlider = prefs[KEY_ALWAYS_DISPLAY_ZOOM_SLIDER] ?: true,
            batterySaveMode = prefs[KEY_BATTERY_SAVE_MODE] ?: false,
            batterySaveBrightness = prefs[KEY_BATTERY_SAVE_BRIGHTNESS] ?: 0.5f,
            easyToggleCameras = prefs[KEY_EASY_TOGGLE_CAMERAS] ?: true,
            whiteBalance = prefs[KEY_WHITE_BALANCE] ?: "auto",
            videoSegmentation = prefs[KEY_VIDEO_SEGMENTATION] ?: "None",
            customFilenamePrefix = prefs[KEY_CUSTOM_FILENAME_PREFIX] ?: "",
            filenameFormatPreset = prefs[KEY_FILENAME_FORMAT_PRESET] ?: "TimePhoto_YYYYMMDD_HHMMSS",
            customStoragePath = prefs[KEY_CUSTOM_STORAGE_PATH] ?: "/storage/emulated/0/DCIM/Camera",
            autoRotateUI = prefs[KEY_AUTO_ROTATE_UI] ?: true,
            photoResolution = prefs[KEY_PHOTO_RESOLUTION] ?: "1920 x 1080",
            videoResolution = prefs[KEY_VIDEO_RESOLUTION] ?: "1920 x 1080",
            photoAspectRatio = prefs[KEY_PHOTO_ASPECT_RATIO] ?: "4:3",
            videoAspectRatio = prefs[KEY_VIDEO_ASPECT_RATIO] ?: "16:9"
        )
    }

    val projectProfileFlow: Flow<ProjectProfile> = context.dataStore.data.map { prefs ->
        val rawWorkType = prefs[KEY_WORK_TYPE] ?: if (prefs[KEY_ACTIVITY] == "MM" || prefs[KEY_ACTIVITY] == "RM" || prefs[KEY_ACTIVITY] == "LIE" || prefs[KEY_ACTIVITY] == "None") prefs[KEY_ACTIVITY] ?: "" else ""
        val rawActivity = if (prefs[KEY_ACTIVITY] == "MM" || prefs[KEY_ACTIVITY] == "RM" || prefs[KEY_ACTIVITY] == "LIE" || prefs[KEY_ACTIVITY] == "None") "DBM" else prefs[KEY_ACTIVITY] ?: "DBM"
        ProjectProfile(
            highwayId = prefs[KEY_HIGHWAY_ID] ?: "",
            packageSection = prefs[KEY_PACKAGE_SECTION] ?: "",
            startChainage = prefs[KEY_PROJECT_START_CHAINAGE] ?: "0+000",
            endChainage = prefs[KEY_PROJECT_END_CHAINAGE] ?: "50+000",
            workType = rawWorkType,
            roadSide = RoadSide.fromString(prefs[KEY_ROAD_SIDE]),
            lane = prefs[KEY_LANE] ?: "I/L",
            activity = rawActivity,
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

            showSpeedOnPreview = prefs[KEY_SHOW_SPEED_PREVIEW] ?: false,
            includeSpeedInCapture = prefs[KEY_INCLUDE_SPEED_CAPTURE] ?: false,
            showDirectionOnPreview = prefs[KEY_SHOW_DIRECTION_PREVIEW] ?: false,
            includeDirectionInCapture = prefs[KEY_INCLUDE_DIRECTION_CAPTURE] ?: false,

            // Logo manual settings (No prefill, default disabled)
            showLogo = prefs[KEY_SHOW_LOGO] ?: false,
            logoPreset = prefs[KEY_LOGO_PRESET] ?: "custom",
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
            logoScale = prefs[KEY_LOGO_SCALE] ?: 1.0f,
            logoRotationDegrees = prefs[KEY_LOGO_ROTATION] ?: 0f,

            fontSizeSp = prefs[KEY_FONT_SIZE_SP] ?: 24f,
            fontSizePreset = prefs[KEY_FONT_SIZE_PRESET] ?: "Medium",
            hasTextShadow = prefs[KEY_HAS_TEXT_SHADOW] ?: true,
            textColorHex = prefs[KEY_TEXT_COLOR_HEX] ?: "#FFFFFF",
            textOpacity = prefs[KEY_TEXT_OPACITY] ?: 1.0f,
            fontFamily = prefs[KEY_FONT_FAMILY] ?: "Default",
            fontStyle = prefs[KEY_FONT_STYLE] ?: "Bold",
            backgroundStyle = prefs[KEY_BACKGROUND_STYLE] ?: "Transparent",
            backgroundOpacity = prefs[KEY_BACKGROUND_OPACITY] ?: 0.0f,
            backgroundColorHex = prefs[KEY_BACKGROUND_COLOR_HEX] ?: "#000000",
            watermarkPosition = prefs[KEY_WATERMARK_POSITION] ?: "Bottom Right",
            timePosition = prefs[KEY_TIME_POSITION] ?: "Time in above",
            resolutionOption = prefs[KEY_RESOLUTION_OPTION] ?: "1920 x 1080",
            locationAddressOption = prefs[KEY_LOCATION_ADDRESS_OPTION] ?: "None",
            customAddressText = prefs[KEY_CUSTOM_ADDRESS_TEXT] ?: "",
            customTag = prefs[KEY_CUSTOM_TAG] ?: "",
            gpsAccuracyMarginPreset = prefs[KEY_GPS_ACCURACY_MARGIN_PRESET] ?: "±5m",
            customFontPath = prefs[KEY_CUSTOM_FONT_PATH],
            customFontName = prefs[KEY_CUSTOM_FONT_NAME],
            textAlignment = prefs[KEY_TEXT_ALIGNMENT] ?: "Right",
            lineSpacingFactor = prefs[KEY_LINE_SPACING_FACTOR] ?: 0.32f,
            textStrokeWidthDp = prefs[KEY_TEXT_STROKE_WIDTH_DP] ?: 1.5f,
            showLocationName = prefs[KEY_SHOW_LOCATION_NAME] ?: true,
            lastDetectedLocationName = prefs[KEY_LAST_DETECTED_LOCATION_NAME] ?: "",
            useSamePaddingForPhotoAndVideo = prefs[KEY_USE_SAME_PADDING_PHOTO_VIDEO] ?: true,
            photoPaddingTopDp = prefs[KEY_PHOTO_PADDING_TOP_DP] ?: 16,
            photoPaddingBottomDp = prefs[KEY_PHOTO_PADDING_BOTTOM_DP] ?: 20,
            photoPaddingLeftDp = prefs[KEY_PHOTO_PADDING_LEFT_DP] ?: 16,
            photoPaddingRightDp = prefs[KEY_PHOTO_PADDING_RIGHT_DP] ?: 16,
            videoPaddingTopDp = prefs[KEY_VIDEO_PADDING_TOP_DP] ?: 16,
            videoPaddingBottomDp = prefs[KEY_VIDEO_PADDING_BOTTOM_DP] ?: 20,
            videoPaddingLeftDp = prefs[KEY_VIDEO_PADDING_LEFT_DP] ?: 16,
            videoPaddingRightDp = prefs[KEY_VIDEO_PADDING_RIGHT_DP] ?: 16
        )
    }

    val activeRouteIdFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_ROUTE_ID]
    }

    val definedProjectsFlow: Flow<List<DefinedProject>> = context.dataStore.data.map { prefs ->
        val jsonStr = prefs[KEY_DEFINED_PROJECTS]
        parseDefinedProjects(jsonStr)
    }

    suspend fun updateDefinedProjects(projects: List<DefinedProject>) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFINED_PROJECTS] = serializeDefinedProjects(projects)
        }
    }

    private fun serializeDefinedProjects(projects: List<DefinedProject>): String {
        val array = org.json.JSONArray()
        for (p in projects) {
            val obj = org.json.JSONObject()
            obj.put("name", p.name)
            obj.put("stretch", p.stretch)
            obj.put("workType", p.workType)
            obj.put("startChainage", p.startChainage)
            obj.put("endChainage", p.endChainage)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseDefinedProjects(jsonStr: String?): List<DefinedProject> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val array = org.json.JSONArray(jsonStr)
            val list = mutableListOf<DefinedProject>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val name = obj.optString("name", "").trim()
                if (name.isNotBlank()) {
                    list.add(
                        DefinedProject(
                            name = name,
                            stretch = obj.optString("stretch", "").trim(),
                            workType = obj.optString("workType", "MM").trim(),
                            startChainage = obj.optString("startChainage", "0+000").trim(),
                            endChainage = obj.optString("endChainage", "50+000").trim()
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateProjectProfile(profile: ProjectProfile) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HIGHWAY_ID] = profile.highwayId
            prefs[KEY_PACKAGE_SECTION] = profile.packageSection
            prefs[KEY_PROJECT_START_CHAINAGE] = profile.startChainage
            prefs[KEY_PROJECT_END_CHAINAGE] = profile.endChainage
            prefs[KEY_WORK_TYPE] = profile.workType
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

            prefs[KEY_SHOW_SPEED_PREVIEW] = config.showSpeedOnPreview
            prefs[KEY_INCLUDE_SPEED_CAPTURE] = config.includeSpeedInCapture
            prefs[KEY_SHOW_DIRECTION_PREVIEW] = config.showDirectionOnPreview
            prefs[KEY_INCLUDE_DIRECTION_CAPTURE] = config.includeDirectionInCapture

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
            prefs[KEY_LOGO_SCALE] = config.logoScale
            prefs[KEY_LOGO_ROTATION] = config.logoRotationDegrees

            prefs[KEY_FONT_SIZE_SP] = config.fontSizeSp
            prefs[KEY_FONT_SIZE_PRESET] = config.fontSizePreset
            prefs[KEY_HAS_TEXT_SHADOW] = config.hasTextShadow
            prefs[KEY_TEXT_COLOR_HEX] = config.textColorHex
            prefs[KEY_TEXT_OPACITY] = config.textOpacity
            prefs[KEY_FONT_FAMILY] = config.fontFamily
            prefs[KEY_FONT_STYLE] = config.fontStyle
            prefs[KEY_BACKGROUND_STYLE] = config.backgroundStyle
            prefs[KEY_BACKGROUND_OPACITY] = config.backgroundOpacity
            prefs[KEY_BACKGROUND_COLOR_HEX] = config.backgroundColorHex
            prefs[KEY_WATERMARK_POSITION] = config.watermarkPosition
            prefs[KEY_TIME_POSITION] = config.timePosition
            prefs[KEY_RESOLUTION_OPTION] = config.resolutionOption
            prefs[KEY_LOCATION_ADDRESS_OPTION] = config.locationAddressOption
            prefs[KEY_CUSTOM_ADDRESS_TEXT] = config.customAddressText
            prefs[KEY_CUSTOM_TAG] = config.customTag
            prefs[KEY_GPS_ACCURACY_MARGIN_PRESET] = config.gpsAccuracyMarginPreset
            if (config.customFontPath != null) {
                prefs[KEY_CUSTOM_FONT_PATH] = config.customFontPath
            } else {
                prefs.remove(KEY_CUSTOM_FONT_PATH)
            }
            if (config.customFontName != null) {
                prefs[KEY_CUSTOM_FONT_NAME] = config.customFontName
            } else {
                prefs.remove(KEY_CUSTOM_FONT_NAME)
            }
            prefs[KEY_TEXT_ALIGNMENT] = config.textAlignment
            prefs[KEY_LINE_SPACING_FACTOR] = config.lineSpacingFactor
            prefs[KEY_TEXT_STROKE_WIDTH_DP] = config.textStrokeWidthDp
            prefs[KEY_SHOW_LOCATION_NAME] = config.showLocationName
            prefs[KEY_LAST_DETECTED_LOCATION_NAME] = config.lastDetectedLocationName
            prefs[KEY_USE_SAME_PADDING_PHOTO_VIDEO] = config.useSamePaddingForPhotoAndVideo
            prefs[KEY_PHOTO_PADDING_TOP_DP] = config.photoPaddingTopDp
            prefs[KEY_PHOTO_PADDING_BOTTOM_DP] = config.photoPaddingBottomDp
            prefs[KEY_PHOTO_PADDING_LEFT_DP] = config.photoPaddingLeftDp
            prefs[KEY_PHOTO_PADDING_RIGHT_DP] = config.photoPaddingRightDp
            prefs[KEY_VIDEO_PADDING_TOP_DP] = config.videoPaddingTopDp
            prefs[KEY_VIDEO_PADDING_BOTTOM_DP] = config.videoPaddingBottomDp
            prefs[KEY_VIDEO_PADDING_LEFT_DP] = config.videoPaddingLeftDp
            prefs[KEY_VIDEO_PADDING_RIGHT_DP] = config.videoPaddingRightDp
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

            prefs[KEY_TOUCH_TO_CAPTURE] = settings.touchToCapture
            prefs[KEY_ALWAYS_DISPLAY_ZOOM_SLIDER] = settings.alwaysDisplayZoomSlider
            prefs[KEY_BATTERY_SAVE_MODE] = settings.batterySaveMode
            prefs[KEY_BATTERY_SAVE_BRIGHTNESS] = settings.batterySaveBrightness
            prefs[KEY_EASY_TOGGLE_CAMERAS] = settings.easyToggleCameras
            prefs[KEY_WHITE_BALANCE] = settings.whiteBalance
            prefs[KEY_VIDEO_SEGMENTATION] = settings.videoSegmentation
            prefs[KEY_CUSTOM_FILENAME_PREFIX] = settings.customFilenamePrefix
            prefs[KEY_FILENAME_FORMAT_PRESET] = settings.filenameFormatPreset
            prefs[KEY_CUSTOM_STORAGE_PATH] = settings.customStoragePath
            prefs[KEY_AUTO_ROTATE_UI] = settings.autoRotateUI
            prefs[KEY_PHOTO_RESOLUTION] = settings.photoResolution
            prefs[KEY_VIDEO_RESOLUTION] = settings.videoResolution
            prefs[KEY_PHOTO_ASPECT_RATIO] = settings.photoAspectRatio
            prefs[KEY_VIDEO_ASPECT_RATIO] = settings.videoAspectRatio
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

    val routeCorrectionsFlow: Flow<Map<String, List<ChainageCorrectionPoint>>> = context.dataStore.data.map { prefs ->
        parseRouteCorrections(prefs[KEY_ROUTE_CORRECTIONS_JSON])
    }

    val routeCorrectionsEnabledFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_ROUTE_CORRECTIONS_ENABLED] ?: true
    }

    val manualChainageOverrideFlow: Flow<Chainage?> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY_MANUAL_CHAINAGE_OVERRIDE]
        if (!raw.isNullOrBlank()) Chainage.parse(raw) else null
    }

    suspend fun setManualChainageOverride(chainage: Chainage?) {
        context.dataStore.edit { prefs ->
            if (chainage != null) {
                prefs[KEY_MANUAL_CHAINAGE_OVERRIDE] = chainage.format()
            } else {
                prefs.remove(KEY_MANUAL_CHAINAGE_OVERRIDE)
            }
        }
    }

    suspend fun setRouteCorrectionsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ROUTE_CORRECTIONS_ENABLED] = enabled
        }
    }

    suspend fun saveRouteCorrectionPoint(point: ChainageCorrectionPoint) {
        context.dataStore.edit { prefs ->
            val currentMap = parseRouteCorrections(prefs[KEY_ROUTE_CORRECTIONS_JSON]).toMutableMap()
            val list = currentMap[point.routeId]?.toMutableList() ?: mutableListOf()
            val existingIdx = list.indexOfFirst { it.id == point.id }
            if (existingIdx != -1) {
                list[existingIdx] = point
            } else {
                list.add(point)
            }
            list.sortBy { it.originalKmzMetres }
            currentMap[point.routeId] = list
            prefs[KEY_ROUTE_CORRECTIONS_JSON] = serializeRouteCorrections(currentMap)
        }
    }

    suspend fun deleteRouteCorrectionPoint(routeId: String, pointId: String) {
        context.dataStore.edit { prefs ->
            val currentMap = parseRouteCorrections(prefs[KEY_ROUTE_CORRECTIONS_JSON]).toMutableMap()
            val list = currentMap[routeId]?.filter { it.id != pointId } ?: emptyList()
            if (list.isEmpty()) {
                currentMap.remove(routeId)
            } else {
                currentMap[routeId] = list
            }
            prefs[KEY_ROUTE_CORRECTIONS_JSON] = serializeRouteCorrections(currentMap)
        }
    }

    suspend fun toggleRouteCorrectionPoint(routeId: String, pointId: String, isEnabled: Boolean) {
        context.dataStore.edit { prefs ->
            val currentMap = parseRouteCorrections(prefs[KEY_ROUTE_CORRECTIONS_JSON]).toMutableMap()
            val list = currentMap[routeId]?.map {
                if (it.id == pointId) it.copy(isEnabled = isEnabled) else it
            } ?: emptyList()
            currentMap[routeId] = list
            prefs[KEY_ROUTE_CORRECTIONS_JSON] = serializeRouteCorrections(currentMap)
        }
    }

    suspend fun resetRouteCorrections(routeId: String) {
        context.dataStore.edit { prefs ->
            val currentMap = parseRouteCorrections(prefs[KEY_ROUTE_CORRECTIONS_JSON]).toMutableMap()
            currentMap.remove(routeId)
            prefs[KEY_ROUTE_CORRECTIONS_JSON] = serializeRouteCorrections(currentMap)
        }
    }

    private fun parseRouteCorrections(rawJson: String?): Map<String, List<ChainageCorrectionPoint>> {
        if (rawJson.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, List<ChainageCorrectionPoint>>()
        try {
            val root = JSONObject(rawJson)
            val keys = root.keys()
            while (keys.hasNext()) {
                val rId = keys.next()
                val arr = root.optJSONArray(rId) ?: continue
                val pts = mutableListOf<ChainageCorrectionPoint>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    pts.add(ChainageCorrectionPoint.fromJson(obj))
                }
                pts.sortBy { it.originalKmzMetres }
                result[rId] = pts
            }
        } catch (ignored: Exception) {}
        return result
    }

    private fun serializeRouteCorrections(map: Map<String, List<ChainageCorrectionPoint>>): String {
        val root = JSONObject()
        for ((rId, pts) in map) {
            val arr = JSONArray()
            pts.forEach { arr.put(it.toJson()) }
            root.put(rId, arr)
        }
        return root.toString()
    }

    val chainageAuditLogFlow: Flow<List<ChainageLogEntry>> = context.dataStore.data.map { prefs ->
        parseChainageLogs(prefs[KEY_CHAINAGE_AUDIT_LOG_JSON])
    }

    suspend fun addChainageLogEntry(entry: ChainageLogEntry) {
        context.dataStore.edit { prefs ->
            val list = parseChainageLogs(prefs[KEY_CHAINAGE_AUDIT_LOG_JSON]).toMutableList()
            list.add(0, entry) // Prepend newest first
            // Retain recent 50 entries
            val trimmed = if (list.size > 50) list.take(50) else list
            val arr = JSONArray()
            trimmed.forEach { arr.put(it.toJson()) }
            prefs[KEY_CHAINAGE_AUDIT_LOG_JSON] = arr.toString()
        }
    }

    suspend fun clearChainageAuditLog() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_CHAINAGE_AUDIT_LOG_JSON)
        }
    }

    private fun parseChainageLogs(raw: String?): List<ChainageLogEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val result = mutableListOf<ChainageLogEntry>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                result.add(ChainageLogEntry.fromJson(obj))
            }
        } catch (ignored: Exception) {}
        return result
    }
}
