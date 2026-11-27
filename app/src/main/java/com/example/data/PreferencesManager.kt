package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.backup.PrefValueType
import com.example.backup.TypedPrefEntry
import com.example.model.CameraAutofocusMode
import com.example.model.CameraCropMode
import com.example.model.CameraGridMode
import com.example.model.CameraMode
import com.example.model.CameraOutputFormat
import com.example.model.CameraSettings
import com.example.model.CaptureMode
import com.example.model.CaptureTimer
import com.example.model.Chainage
import com.example.model.ChainageVoiceAssistantSettings
import com.example.model.CoordinateFormat
import com.example.model.CtecsWidgetSettings
import com.example.model.DefinedProject
import com.example.model.HomeScreensaverSettings
import com.example.model.KmStoneAlertSettings
import com.example.model.KmzManualCalibration
import com.example.model.LogoPosition
import com.example.model.LogoShape
import com.example.model.MiniMapStyleConfig
import com.example.model.NavigationNotificationSettings
import com.example.model.OutputQualityPreset
import com.example.model.OverlayConfig
import com.example.model.OverlayTemplate
import com.example.model.PreviewRatio
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.SmartDirectionPreferences
import com.example.model.SpeedUnit
import com.example.model.VideoQualityOption
import com.example.model.VideoStabilizationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

private val Context.dataStore by preferencesDataStore(name = "ctecs_user_preferences")

class PreferencesManager(context: Context) {

    private val appContext: Context = context.applicationContext ?: context

    companion object {
        /**
         * Single-threaded FIFO coroutine dispatcher & process-wide scope for preference persistence.
         * Guarantees strict write ordering and prevents closing an Activity or clearing a ViewModel
         * from cancelling or reordering in-flight DataStore transactions.
         */
        private val persistenceDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "ctecs-prefs-persistence").apply { isDaemon = true }
        }.asCoroutineDispatcher()

        val persistenceScope = CoroutineScope(SupervisorJob() + persistenceDispatcher)
        private val persistenceMutex = Mutex()
        private val activeEditCount = AtomicInteger(0)

        @Volatile
        private var cachedPreferences: Preferences? = null

        /**
         * Test-only hook for simulating local storage write failures (always null in production).
         */
        @Volatile
        var debugWriteFailureInterceptor: ((String) -> Throwable?)? = null

        /**
         * Clears the in-memory preferences snapshot cache (used to simulate cold process death in tests).
         */
        fun clearMemoryCacheForProcessRestart() {
            cachedPreferences = null
        }

        /**
         * Suspends until all queued/in-flight DataStore write transactions have finished committing.
         */
        suspend fun awaitPendingWrites() {
            while (activeEditCount.get() > 0) {
                delay(10L)
            }
            withContext(persistenceDispatcher) {
                persistenceMutex.withLock { }
            }
        }

        val KEY_HIGHWAY_ID = stringPreferencesKey("highway_id")
        val KEY_PACKAGE_SECTION = stringPreferencesKey("package_section")
        val KEY_PROJECT_START_CHAINAGE = stringPreferencesKey("project_start_chainage")
        val KEY_PROJECT_END_CHAINAGE = stringPreferencesKey("project_end_chainage")
        val KEY_WORK_TYPE = stringPreferencesKey("work_type")
        val KEY_ROAD_SIDE = stringPreferencesKey("road_side")
        val KEY_LANE = stringPreferencesKey("lane")
        val KEY_ACTIVITY = stringPreferencesKey("activity")
        val KEY_SUB_ACTIVITY = stringPreferencesKey("sub_activity")
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

        // Additional OverlayConfig keys previously missing from persistence
        val KEY_SAVE_ORIGINAL_IMAGE = booleanPreferencesKey("save_original_image")
        val KEY_TIMESTAMP_MARGIN_MODE = stringPreferencesKey("timestamp_margin_mode")
        val KEY_SHOW_INDEX_NUMBER = booleanPreferencesKey("show_index_number")

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

        // Independent Photo & Video Mini Map Overlay JSON keys
        val KEY_PHOTO_MINI_MAP_JSON = stringPreferencesKey("photo_mini_map_config_json")
        val KEY_VIDEO_MINI_MAP_JSON = stringPreferencesKey("video_mini_map_config_json")

        // Camera Timestamp & Watermark -> Weather Information Keys
        val KEY_SHOW_WEATHER_INFO = booleanPreferencesKey("overlay_show_weather_info")
        val KEY_WEATHER_SHOW_TEMPERATURE = booleanPreferencesKey("overlay_weather_show_temp")
        val KEY_WEATHER_SHOW_CONDITION = booleanPreferencesKey("overlay_weather_show_condition")
        val KEY_WEATHER_SHOW_LOCATION_NAME = booleanPreferencesKey("overlay_weather_show_location")
        val KEY_WEATHER_SHOW_COORDINATES = booleanPreferencesKey("overlay_weather_show_coords")
        val KEY_WEATHER_SHOW_HUMIDITY = booleanPreferencesKey("overlay_weather_show_humidity")
        val KEY_WEATHER_SHOW_WIND_SPEED = booleanPreferencesKey("overlay_weather_show_wind_speed")
        val KEY_WEATHER_SHOW_WIND_DIRECTION = booleanPreferencesKey("overlay_weather_show_wind_dir")
        val KEY_WEATHER_DISPLAY_FORMAT = stringPreferencesKey("overlay_weather_display_format")
        val KEY_WEATHER_OMIT_IF_STALE = booleanPreferencesKey("overlay_weather_omit_if_stale")
        val KEY_LAST_WEATHER_OBSERVATION_JSON = stringPreferencesKey("last_weather_observation_json")

        // DPR PDF Default Logo & Signature keys
        val KEY_PDF_LOGO_URI = stringPreferencesKey("pdf_logo_uri")
        val KEY_INCLUDE_PDF_LOGO_DEFAULT = booleanPreferencesKey("include_pdf_logo_default")
        val KEY_PDF_SIGNATURE_URI = stringPreferencesKey("pdf_signature_uri")
        val KEY_INCLUDE_PDF_SIGNATURE_DEFAULT = booleanPreferencesKey("include_pdf_signature_default")
        val KEY_PDF_DEFAULT_PREPARED_BY = stringPreferencesKey("pdf_default_prepared_by")

        val KEY_ACTIVE_ROUTE_ID = stringPreferencesKey("active_route_id")
        val KEY_SELECTED_ALIGNMENT_ID = stringPreferencesKey("selected_alignment_id")

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
        val KEY_DEFAULT_LENS_ZOOM = floatPreferencesKey("camera_default_lens_zoom")

        // Persisted Camera hardware & capture mode state
        val KEY_IS_FLASH_ON = booleanPreferencesKey("camera_is_flash_on")
        val KEY_IS_FRONT_CAMERA = booleanPreferencesKey("camera_is_front_camera")
        val KEY_CAPTURE_MODE = stringPreferencesKey("camera_capture_mode")

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
        val KEY_CAMERA_CROP_MODE = stringPreferencesKey("camera_crop_mode")
        val KEY_PHOTO_OUTPUT_FORMAT = stringPreferencesKey("camera_photo_output_format")
        val KEY_VIDEO_OUTPUT_FORMAT = stringPreferencesKey("camera_video_output_format")
        val KEY_PHOTO_QUALITY_PRESET = stringPreferencesKey("camera_photo_quality_preset")
        val KEY_VIDEO_QUALITY_PRESET = stringPreferencesKey("camera_video_quality_preset")
        val KEY_VIDEO_STABILIZATION_MODE = stringPreferencesKey("camera_video_stabilization_mode")
        val KEY_AUTOFOCUS_MODE = stringPreferencesKey("camera_autofocus_mode")
        val KEY_PHOTO_OUTPUT_SIZE_OPTIMIZATION = booleanPreferencesKey("camera_photo_output_size_optimization")
        val KEY_PHOTO_ENHANCEMENT_ENABLED = booleanPreferencesKey("camera_photo_enhancement_enabled")
        val KEY_VIDEO_OUTPUT_SIZE_OPTIMIZATION = booleanPreferencesKey("camera_video_output_size_optimization")
        val KEY_VIDEO_NOISE_REDUCTION_ENABLED = booleanPreferencesKey("camera_video_noise_reduction_enabled")
        val KEY_CAMERA_AUTO_BRIGHTNESS = booleanPreferencesKey("camera_auto_brightness_enabled")
        val KEY_CAMERA_MANUAL_BRIGHTNESS = floatPreferencesKey("camera_manual_preview_brightness")
        val KEY_GALLERY_VIEW_MODE = stringPreferencesKey("gallery_view_mode")
        val KEY_GALLERY_SORT_OPTION = stringPreferencesKey("gallery_sort_option")
        val KEY_GALLERY_GROUP_OPTION = stringPreferencesKey("gallery_group_option")

        // KMZ/KML Manual Reference Calibration & Manual Chainage Override Keys
        val KEY_KMZ_MANUAL_CALIBRATIONS_JSON = stringPreferencesKey("kmz_manual_calibrations_json")
        val KEY_MANUAL_CHAINAGE_OVERRIDE = stringPreferencesKey("manual_chainage_override")
        val KEY_MANUAL_CHAINAGE_CUSTOM_TEXT = stringPreferencesKey("manual_chainage_custom_text")
        val KEY_MANUAL_CHAINAGE_CALIBRATION_JSON = stringPreferencesKey("manual_chainage_calibration_json")
        val KEY_LAST_RELIABLE_CHAINAGE_JSON = stringPreferencesKey("last_reliable_chainage_snapshot_json")
        val KEY_SAVED_CUSTOM_CHAINAGES = stringPreferencesKey("saved_custom_chainages")
        val KEY_HOME_SCREENSAVER_JSON = stringPreferencesKey("home_screensaver_settings_json")
        val KEY_NAV_NOTIFICATION_JSON = stringPreferencesKey("nav_notification_settings_json")
        val KEY_WIDGET_SETTINGS_JSON = stringPreferencesKey("widget_settings_config_json")
        val KEY_KM_STONE_ALERT_JSON = stringPreferencesKey("km_stone_alert_settings_json")
        val KEY_CHAINAGE_VOICE_ASSISTANT_JSON = stringPreferencesKey("chainage_voice_assistant_settings_json")
        val KEY_SMART_DIRECTION_PREFS_JSON = stringPreferencesKey("smart_direction_preferences_json")

        // Navigation & Survey persistent user preferences
        val KEY_PREFERRED_EXTERNAL_NAV_APP_ID = stringPreferencesKey("preferred_external_nav_app_id")
        val KEY_SURVEY_SHOW_200M_LABELS = booleanPreferencesKey("survey_show_200m_labels")
        val KEY_SURVEY_SHOW_CENTRELINE = booleanPreferencesKey("survey_show_centreline")
        val KEY_SURVEY_SHOW_GPS_TRACK = booleanPreferencesKey("survey_show_gps_track")
        val KEY_SURVEY_SHOW_LANE_TRACK = booleanPreferencesKey("survey_show_lane_track")
        val KEY_SURVEY_SHOW_MARKERS = booleanPreferencesKey("survey_show_markers")
        val KEY_SURVEY_MAP_TYPE = stringPreferencesKey("survey_map_type")

        // Offline Mini Map Preloading Preferences
        val KEY_OFFLINE_MAP_AUTO_PRELOAD = booleanPreferencesKey("offline_map_auto_preload")
        val KEY_OFFLINE_MAP_WIFI_ONLY = booleanPreferencesKey("offline_map_wifi_only")
        val KEY_MINI_MAP_VIEW_DISTANCE_MODE = stringPreferencesKey("mini_map_top_view_distance_mode")
        val KEY_MINI_MAP_CUSTOM_DISTANCE_METRES = doublePreferencesKey("mini_map_custom_distance_metres")

        // Advanced Weather Forecast & IRC/MoRTH Highway Work Intelligence Keys
        val KEY_LAST_WEATHER_FORECAST_BUNDLE_JSON = stringPreferencesKey("last_weather_forecast_bundle_json")
        val KEY_WEATHER_INTELLIGENCE_SETTINGS_JSON = stringPreferencesKey("weather_intelligence_settings_json")
        val KEY_ACTUAL_SITE_MEASUREMENTS_JSON = stringPreferencesKey("weather_actual_site_measurements_json")
        val KEY_WEATHER_EXPANDED_SECTIONS = stringPreferencesKey("weather_expanded_sections_csv")
    }

    private val observedDataFlow: Flow<Preferences> = appContext.dataStore.data.onEach { prefs ->
        cachedPreferences = prefs
    }

    /**
     * Synchronously returns the current persisted DataStore preferences snapshot.
     * Ensures that startup StateFlows and ViewModels immediately receive persisted user settings
     * instead of emitting temporary defaults that could overwrite saved data.
     */
    fun getInitialPreferences(): Preferences {
        cachedPreferences?.let { return it }
        return try {
            runBlocking(Dispatchers.IO) {
                appContext.dataStore.data.first()
            }.also { cachedPreferences = it }
        } catch (_: Exception) {
            emptyPreferences()
        }
    }

    /**
     * Executes a DataStore edit inside the process-wide single-threaded non-cancellable scope,
     * serialized by persistenceMutex so concurrent callers never write out of order.
     */
    private suspend fun safeEdit(block: (MutablePreferences) -> Unit): Preferences {
        activeEditCount.incrementAndGet()
        return try {
            withContext(persistenceDispatcher + NonCancellable) {
                persistenceMutex.withLock {
                    debugWriteFailureInterceptor?.invoke("safeEdit")?.let { simulatedError ->
                        throw simulatedError
                    }
                    val updated = appContext.dataStore.edit { prefs ->
                        block(prefs)
                    }
                    cachedPreferences = updated
                    updated
                }
            }
        } finally {
            activeEditCount.decrementAndGet()
        }
    }

    /**
     * Suspends until the preference persistence operation completes on the serialized persistence
     * dispatcher, propagating any storage failure to the caller so auto-save confirmation badges
     * ONLY display SAVED after real local storage persistence succeeds.
     */
    suspend fun executeSafeEdit(block: suspend () -> Unit) {
        activeEditCount.incrementAndGet()
        try {
            withContext(persistenceDispatcher + NonCancellable) {
                block()
            }
        } finally {
            activeEditCount.decrementAndGet()
        }
    }

    /**
     * Launches a fire-and-forget preference edit on the process-wide persistenceScope so it
     * survives ViewModel clearing or Activity destruction and executes in strict FIFO order.
     */
    fun launchSafeEdit(block: suspend () -> Unit) {
        activeEditCount.incrementAndGet()
        persistenceScope.launch {
            try {
                withContext(NonCancellable) {
                    block()
                }
            } finally {
                activeEditCount.decrementAndGet()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Synchronous Initial Getters for Immediate Startup Restoration
    // -------------------------------------------------------------------------

    fun getInitialGalleryViewMode(): String = getInitialPreferences()[KEY_GALLERY_VIEW_MODE] ?: "grid"
    fun getInitialGallerySortOption(): String = getInitialPreferences()[KEY_GALLERY_SORT_OPTION] ?: "Newest first"
    fun getInitialGalleryGroupOption(): String = getInitialPreferences()[KEY_GALLERY_GROUP_OPTION] ?: "None"
    fun getInitialThemeMode(): String = getInitialPreferences()[KEY_THEME_MODE] ?: "system"
    fun getInitialMapTheme(): String = getInitialPreferences()[KEY_MAP_THEME] ?: "system"
    fun getInitialMapType(): String = getInitialPreferences()[KEY_MAP_TYPE] ?: "standard"
    fun getInitialActiveRouteId(): String? = getInitialPreferences()[KEY_ACTIVE_ROUTE_ID]
    fun getInitialSelectedAlignmentId(): String? =
        getInitialPreferences()[KEY_SELECTED_ALIGNMENT_ID]?.takeIf { it.isNotBlank() }

    fun getInitialHomeScreensaverSettings(): HomeScreensaverSettings =
        HomeScreensaverSettings.fromJsonString(getInitialPreferences()[KEY_HOME_SCREENSAVER_JSON])

    fun getInitialNavigationNotificationSettings(): NavigationNotificationSettings =
        NavigationNotificationSettings.fromJsonString(getInitialPreferences()[KEY_NAV_NOTIFICATION_JSON])

    fun getInitialWidgetSettings(): CtecsWidgetSettings =
        CtecsWidgetSettings.fromJsonString(getInitialPreferences()[KEY_WIDGET_SETTINGS_JSON])

    fun getInitialKmStoneAlertSettings(): KmStoneAlertSettings =
        KmStoneAlertSettings.fromJsonString(getInitialPreferences()[KEY_KM_STONE_ALERT_JSON])

    fun getInitialChainageVoiceAssistantSettings(): ChainageVoiceAssistantSettings =
        ChainageVoiceAssistantSettings.fromJsonString(getInitialPreferences()[KEY_CHAINAGE_VOICE_ASSISTANT_JSON])

    fun getInitialSmartDirectionPreferences(): SmartDirectionPreferences =
        SmartDirectionPreferences.fromJsonString(getInitialPreferences()[KEY_SMART_DIRECTION_PREFS_JSON])

    fun getInitialWeatherIntelligenceSettings(): com.example.model.WeatherIntelligenceSettings =
        com.example.model.WeatherIntelligenceSettings.fromJsonString(
            getInitialPreferences()[KEY_WEATHER_INTELLIGENCE_SETTINGS_JSON]
        )

    fun getInitialActualSiteMeasurements(): com.example.model.ActualSiteMeasurements =
        com.example.model.ActualSiteMeasurements.fromJsonString(
            getInitialPreferences()[KEY_ACTUAL_SITE_MEASUREMENTS_JSON]
        )

    fun getInitialCachedWeatherForecastBundle(): com.example.model.WeatherForecastBundle? =
        com.example.model.WeatherForecastBundle.fromJsonString(
            getInitialPreferences()[KEY_LAST_WEATHER_FORECAST_BUNDLE_JSON]
        )

    fun getInitialWeatherExpandedSections(): Set<String> =
        parseCsvSet(getInitialPreferences()[KEY_WEATHER_EXPANDED_SECTIONS])

    fun getInitialCameraSettings(): CameraSettings =
        parseCameraSettingsFromPrefs(getInitialPreferences())

    fun getInitialProjectProfile(): ProjectProfile =
        parseProjectProfileFromPrefs(getInitialPreferences())

    fun isProjectRoadSideExplicitlySaved(): Boolean =
        !getInitialPreferences()[KEY_ROAD_SIDE].isNullOrBlank()

    fun getInitialOverlayConfig(): OverlayConfig =
        parseOverlayConfigFromPrefs(getInitialPreferences())

    fun getInitialDefinedProjects(): List<DefinedProject> =
        parseDefinedProjects(getInitialPreferences()[KEY_DEFINED_PROJECTS])

    fun getInitialKmzCalibrations(): Map<String, KmzManualCalibration> =
        parseKmzCalibrations(getInitialPreferences()[KEY_KMZ_MANUAL_CALIBRATIONS_JSON])

    fun getInitialManualChainageOverride(): Chainage? {
        val raw = getInitialPreferences()[KEY_MANUAL_CHAINAGE_OVERRIDE]
        return if (!raw.isNullOrBlank()) Chainage.parse(raw) else null
    }

    fun getInitialManualChainageText(): String? {
        val prefs = getInitialPreferences()
        val custom = prefs[KEY_MANUAL_CHAINAGE_CUSTOM_TEXT]
        val raw = prefs[KEY_MANUAL_CHAINAGE_OVERRIDE]
        return when {
            !custom.isNullOrBlank() -> custom
            !raw.isNullOrBlank() -> raw
            else -> null
        }
    }

    fun getInitialManualChainageCalibration(): com.example.model.ManualChainageCalibrationState {
        return parseManualChainageCalibration(getInitialPreferences())
    }

    fun getInitialLastReliableChainageSnapshot(): com.example.model.LastReliableChainageSnapshot? {
        val json = getInitialPreferences()[KEY_LAST_RELIABLE_CHAINAGE_JSON]
        return com.example.model.LastReliableChainageSnapshot.fromJsonString(json)
    }

    private fun parseManualChainageCalibration(prefs: Preferences): com.example.model.ManualChainageCalibrationState {
        val json = prefs[KEY_MANUAL_CHAINAGE_CALIBRATION_JSON]
        if (!json.isNullOrBlank()) {
            return com.example.model.ManualChainageCalibrationState.fromJsonString(json)
        }
        val legacyText = prefs[KEY_MANUAL_CHAINAGE_CUSTOM_TEXT]?.takeIf { it.isNotBlank() }
            ?: prefs[KEY_MANUAL_CHAINAGE_OVERRIDE]?.takeIf { it.isNotBlank() }
        val legacyParsed = legacyText?.let { Chainage.parse(it) }
        return if (legacyParsed != null) {
            com.example.model.ManualChainageCalibrationState(
                isActive = true,
                mode = com.example.model.ManualChainageMode.CONTINUE_STABLE,
                anchorChainageMetres = legacyParsed.metres,
                anchorFormattedText = legacyParsed.format(),
                currentChainageMetres = legacyParsed.metres
            )
        } else {
            com.example.model.ManualChainageCalibrationState()
        }
    }

    fun getInitialSavedCustomChainages(): List<String> {
        val raw = getInitialPreferences()[KEY_SAVED_CUSTOM_CHAINAGES].orEmpty()
        return if (raw.isBlank()) emptyList()
        else raw.split("||").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun getInitialIsFlashOn(): Boolean = getInitialPreferences()[KEY_IS_FLASH_ON] ?: false
    fun getInitialIsFrontCamera(): Boolean = getInitialPreferences()[KEY_IS_FRONT_CAMERA] ?: false
    fun getInitialCaptureMode(): CaptureMode {
        val raw = getInitialPreferences()[KEY_CAPTURE_MODE]
        return CaptureMode.entries.find { it.name.equals(raw, ignoreCase = true) } ?: CaptureMode.PHOTO
    }

    fun getInitialPreferredExternalNavAppId(): String? =
        getInitialPreferences()[KEY_PREFERRED_EXTERNAL_NAV_APP_ID]?.takeIf { it.isNotBlank() }

    fun getInitialSurveyShow200mLabels(): Boolean =
        getInitialPreferences()[KEY_SURVEY_SHOW_200M_LABELS] ?: true

    fun getInitialSurveyShowCentreline(): Boolean =
        getInitialPreferences()[KEY_SURVEY_SHOW_CENTRELINE] ?: true

    fun getInitialSurveyShowGpsTrack(): Boolean =
        getInitialPreferences()[KEY_SURVEY_SHOW_GPS_TRACK] ?: true

    fun getInitialSurveyShowLaneTrack(): Boolean =
        getInitialPreferences()[KEY_SURVEY_SHOW_LANE_TRACK] ?: true

    fun getInitialSurveyShowMarkers(): Boolean =
        getInitialPreferences()[KEY_SURVEY_SHOW_MARKERS] ?: true

    fun getInitialSurveyMapType(): String =
        getInitialPreferences()[KEY_SURVEY_MAP_TYPE] ?: "standard"

    fun getInitialOfflineMapAutoPreload(): Boolean =
        getInitialPreferences()[KEY_OFFLINE_MAP_AUTO_PRELOAD] ?: true

    fun getInitialOfflineMapWifiOnly(): Boolean =
        getInitialPreferences()[KEY_OFFLINE_MAP_WIFI_ONLY] ?: false

    // -------------------------------------------------------------------------
    // Reactive Flows & Safe Mutations
    // -------------------------------------------------------------------------

    val galleryViewModeFlow: Flow<String> = observedDataFlow.map { prefs ->
        prefs[KEY_GALLERY_VIEW_MODE] ?: "grid"
    }.distinctUntilChanged()

    val gallerySortOptionFlow: Flow<String> = observedDataFlow.map { prefs ->
        prefs[KEY_GALLERY_SORT_OPTION] ?: "Newest first"
    }.distinctUntilChanged()

    val galleryGroupOptionFlow: Flow<String> = observedDataFlow.map { prefs ->
        prefs[KEY_GALLERY_GROUP_OPTION] ?: "None"
    }.distinctUntilChanged()

    suspend fun updateGalleryPreferences(viewMode: String? = null, sortOption: String? = null, groupOption: String? = null) {
        safeEdit { prefs ->
            if (viewMode != null) prefs[KEY_GALLERY_VIEW_MODE] = viewMode
            if (sortOption != null) prefs[KEY_GALLERY_SORT_OPTION] = sortOption
            if (groupOption != null) prefs[KEY_GALLERY_GROUP_OPTION] = groupOption
        }
    }

    val themeModeFlow: Flow<String> = observedDataFlow.map { prefs ->
        prefs[KEY_THEME_MODE] ?: "system"
    }.distinctUntilChanged()

    suspend fun updateThemeMode(mode: String) {
        safeEdit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    val mapThemeFlow: Flow<String> = observedDataFlow.map { prefs ->
        prefs[KEY_MAP_THEME] ?: "system"
    }.distinctUntilChanged()

    suspend fun updateMapTheme(theme: String) {
        safeEdit { prefs ->
            prefs[KEY_MAP_THEME] = theme
        }
    }

    val mapTypeFlow: Flow<String> = observedDataFlow.map { prefs ->
        prefs[KEY_MAP_TYPE] ?: "standard"
    }.distinctUntilChanged()

    suspend fun updateMapType(type: String) {
        safeEdit { prefs ->
            prefs[KEY_MAP_TYPE] = type
        }
    }

    val offlineMapAutoPreloadFlow: Flow<Boolean> = observedDataFlow.map { prefs ->
        prefs[KEY_OFFLINE_MAP_AUTO_PRELOAD] ?: true
    }.distinctUntilChanged()

    val offlineMapWifiOnlyFlow: Flow<Boolean> = observedDataFlow.map { prefs ->
        prefs[KEY_OFFLINE_MAP_WIFI_ONLY] ?: false
    }.distinctUntilChanged()

    suspend fun updateOfflineMapPreferences(
        autoPreload: Boolean? = null,
        wifiOnly: Boolean? = null
    ) {
        safeEdit { prefs ->
            if (autoPreload != null) prefs[KEY_OFFLINE_MAP_AUTO_PRELOAD] = autoPreload
            if (wifiOnly != null) prefs[KEY_OFFLINE_MAP_WIFI_ONLY] = wifiOnly
        }
    }

    val miniMapViewDistanceFlow: Flow<com.example.ui.map.MiniMapViewDistancePreference> = observedDataFlow.map { prefs ->
        parseMiniMapViewDistance(prefs)
    }.distinctUntilChanged()

    fun getInitialMiniMapViewDistance(): com.example.ui.map.MiniMapViewDistancePreference {
        return parseMiniMapViewDistance(getInitialPreferences())
    }

    private fun parseMiniMapViewDistance(prefs: Preferences): com.example.ui.map.MiniMapViewDistancePreference {
        val mode = com.example.ui.map.MiniMapViewDistanceMode.fromStorageKey(prefs[KEY_MINI_MAP_VIEW_DISTANCE_MODE])
        val customM = com.example.ui.map.MiniMapViewDistancePreference.clampCustomDistanceMetres(
            prefs[KEY_MINI_MAP_CUSTOM_DISTANCE_METRES]
                ?: com.example.ui.map.MiniMapViewportMath.DEFAULT_GROUND_VIEW_RANGE_METRES
        )
        return com.example.ui.map.MiniMapViewDistancePreference(
            mode = mode,
            customDistanceMetres = customM
        )
    }

    suspend fun updateMiniMapViewDistance(preference: com.example.ui.map.MiniMapViewDistancePreference) {
        val clampedCustom = preference.validatedCustomMetres
        safeEdit { prefs ->
            prefs[KEY_MINI_MAP_VIEW_DISTANCE_MODE] = preference.mode.storageKey
            prefs[KEY_MINI_MAP_CUSTOM_DISTANCE_METRES] = clampedCustom
        }
    }

    val homeScreensaverSettingsFlow: Flow<HomeScreensaverSettings> = observedDataFlow.map { prefs ->
        HomeScreensaverSettings.fromJsonString(prefs[KEY_HOME_SCREENSAVER_JSON])
    }.distinctUntilChanged()

    suspend fun updateHomeScreensaverSettings(settings: HomeScreensaverSettings) {
        safeEdit { prefs ->
            prefs[KEY_HOME_SCREENSAVER_JSON] = settings.toJsonString()
        }
    }

    suspend fun editHomeScreensaverSettings(transform: (HomeScreensaverSettings) -> HomeScreensaverSettings): HomeScreensaverSettings {
        var updated = HomeScreensaverSettings()
        safeEdit { prefs ->
            val current = HomeScreensaverSettings.fromJsonString(prefs[KEY_HOME_SCREENSAVER_JSON])
            updated = transform(current)
            prefs[KEY_HOME_SCREENSAVER_JSON] = updated.toJsonString()
        }
        return updated
    }

    val navigationNotificationSettingsFlow: Flow<NavigationNotificationSettings> =
        observedDataFlow.map { prefs ->
            NavigationNotificationSettings.fromJsonString(prefs[KEY_NAV_NOTIFICATION_JSON])
        }.distinctUntilChanged()

    suspend fun updateNavigationNotificationSettings(settings: NavigationNotificationSettings) {
        safeEdit { prefs ->
            prefs[KEY_NAV_NOTIFICATION_JSON] = settings.toJsonString()
        }
    }

    suspend fun editNavigationNotificationSettings(
        transform: (NavigationNotificationSettings) -> NavigationNotificationSettings
    ): NavigationNotificationSettings {
        var updated = NavigationNotificationSettings()
        safeEdit { prefs ->
            val current = NavigationNotificationSettings.fromJsonString(prefs[KEY_NAV_NOTIFICATION_JSON])
            updated = transform(current)
            prefs[KEY_NAV_NOTIFICATION_JSON] = updated.toJsonString()
        }
        return updated
    }

    val widgetSettingsFlow: Flow<CtecsWidgetSettings> =
        observedDataFlow.map { prefs ->
            CtecsWidgetSettings.fromJsonString(prefs[KEY_WIDGET_SETTINGS_JSON])
        }.distinctUntilChanged()

    suspend fun updateWidgetSettings(settings: CtecsWidgetSettings) {
        safeEdit { prefs ->
            prefs[KEY_WIDGET_SETTINGS_JSON] = settings.toJsonString()
        }
    }

    suspend fun editWidgetSettings(transform: (CtecsWidgetSettings) -> CtecsWidgetSettings): CtecsWidgetSettings {
        var updated = CtecsWidgetSettings()
        safeEdit { prefs ->
            val current = CtecsWidgetSettings.fromJsonString(prefs[KEY_WIDGET_SETTINGS_JSON])
            updated = transform(current)
            prefs[KEY_WIDGET_SETTINGS_JSON] = updated.toJsonString()
        }
        return updated
    }

    val kmStoneAlertSettingsFlow: Flow<KmStoneAlertSettings> =
        observedDataFlow.map { prefs ->
            KmStoneAlertSettings.fromJsonString(prefs[KEY_KM_STONE_ALERT_JSON])
        }.distinctUntilChanged()

    suspend fun updateKmStoneAlertSettings(settings: KmStoneAlertSettings) {
        safeEdit { prefs ->
            prefs[KEY_KM_STONE_ALERT_JSON] = settings.toJsonString()
        }
    }

    suspend fun editKmStoneAlertSettings(transform: (KmStoneAlertSettings) -> KmStoneAlertSettings): KmStoneAlertSettings {
        var updated = KmStoneAlertSettings()
        safeEdit { prefs ->
            val current = KmStoneAlertSettings.fromJsonString(prefs[KEY_KM_STONE_ALERT_JSON])
            updated = transform(current)
            prefs[KEY_KM_STONE_ALERT_JSON] = updated.toJsonString()
        }
        return updated
    }

    val chainageVoiceAssistantSettingsFlow: Flow<ChainageVoiceAssistantSettings> =
        observedDataFlow.map { prefs ->
            ChainageVoiceAssistantSettings.fromJsonString(prefs[KEY_CHAINAGE_VOICE_ASSISTANT_JSON])
        }.distinctUntilChanged()

    suspend fun updateChainageVoiceAssistantSettings(settings: ChainageVoiceAssistantSettings) {
        safeEdit { prefs ->
            prefs[KEY_CHAINAGE_VOICE_ASSISTANT_JSON] = settings.toJsonString()
        }
    }

    val smartDirectionPreferencesFlow: Flow<SmartDirectionPreferences> =
        observedDataFlow.map { prefs ->
            SmartDirectionPreferences.fromJsonString(prefs[KEY_SMART_DIRECTION_PREFS_JSON])
        }.distinctUntilChanged()

    suspend fun updateSmartDirectionPreferences(preferences: SmartDirectionPreferences) {
        safeEdit { prefs ->
            prefs[KEY_SMART_DIRECTION_PREFS_JSON] = preferences.toJsonString()
        }
    }

    val weatherIntelligenceSettingsFlow: Flow<com.example.model.WeatherIntelligenceSettings> =
        observedDataFlow.map { prefs ->
            com.example.model.WeatherIntelligenceSettings.fromJsonString(
                prefs[KEY_WEATHER_INTELLIGENCE_SETTINGS_JSON]
            )
        }.distinctUntilChanged()

    suspend fun updateWeatherIntelligenceSettings(settings: com.example.model.WeatherIntelligenceSettings) {
        safeEdit { prefs ->
            prefs[KEY_WEATHER_INTELLIGENCE_SETTINGS_JSON] = settings.toJsonString()
        }
    }

    val actualSiteMeasurementsFlow: Flow<com.example.model.ActualSiteMeasurements> =
        observedDataFlow.map { prefs ->
            com.example.model.ActualSiteMeasurements.fromJsonString(prefs[KEY_ACTUAL_SITE_MEASUREMENTS_JSON])
        }.distinctUntilChanged()

    suspend fun updateActualSiteMeasurements(measurements: com.example.model.ActualSiteMeasurements) {
        safeEdit { prefs ->
            prefs[KEY_ACTUAL_SITE_MEASUREMENTS_JSON] = measurements.toJsonString()
        }
    }

    val weatherExpandedSectionsFlow: Flow<Set<String>> =
        observedDataFlow.map { prefs ->
            parseCsvSet(prefs[KEY_WEATHER_EXPANDED_SECTIONS])
        }.distinctUntilChanged()

    suspend fun updateWeatherExpandedSections(sections: Set<String>) {
        safeEdit { prefs ->
            prefs[KEY_WEATHER_EXPANDED_SECTIONS] = sections.filter { it.isNotBlank() }.sorted().joinToString(",")
        }
    }

    private fun parseCsvSet(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    val cameraSettingsFlow: Flow<CameraSettings> = observedDataFlow.map { prefs ->
        parseCameraSettingsFromPrefs(prefs)
    }.distinctUntilChanged()

    private fun parseCameraSettingsFromPrefs(prefs: Preferences): CameraSettings {
        val saveOriginalDual = prefs[KEY_SAVE_ORIGINAL_AND_STAMPED] ?: prefs[KEY_SAVE_ORIGINAL_IMAGE] ?: false
        return CameraSettings(
            captureTimer = CaptureTimer.fromSeconds(prefs[KEY_CAPTURE_TIMER] ?: 0),
            gridMode = CameraGridMode.fromString(prefs[KEY_GRID_MODE]),
            showHorizonLevel = prefs[KEY_SHOW_HORIZON_LEVEL] ?: true,
            previewRatio = PreviewRatio.fromString(prefs[KEY_PREVIEW_RATIO]),
            captureSound = prefs[KEY_CAPTURE_SOUND] ?: true,
            volumeButtonShutter = prefs[KEY_VOLUME_SHUTTER] ?: true,
            frontCameraMirror = prefs[KEY_FRONT_MIRROR] ?: false,
            jpegQuality = prefs[KEY_JPEG_QUALITY] ?: 92,
            saveOriginalAndStamped = saveOriginalDual,
            preserveExif = prefs[KEY_PRESERVE_EXIF] ?: true,
            videoQuality = VideoQualityOption.fromString(prefs[KEY_VIDEO_QUALITY]),
            videoFps = prefs[KEY_VIDEO_FPS] ?: 30,
            recordAudio = prefs[KEY_RECORD_AUDIO] ?: true,
            gpsUpdateIntervalSec = prefs[KEY_GPS_INTERVAL] ?: 1,
            gpsAccuracyThresholdMeters = prefs[KEY_GPS_ACCURACY_THRESHOLD] ?: 15f,
            routeMatchingRadiusMeters = (prefs[KEY_ROUTE_RADIUS] ?: 100f).toDouble(),
            coordinateFormat = CoordinateFormat.fromString(prefs[KEY_COORD_FORMAT]),
            is24HourFormat = prefs[KEY_IS_24H] ?: false,
            exposureCompensationStep = prefs[KEY_EXPOSURE_COMP] ?: 0,
            cameraMode = CameraMode.fromString(prefs[KEY_CAMERA_MODE]),
            speedUnit = SpeedUnit.fromString(prefs[KEY_SPEED_UNIT]),
            defaultLensZoom = prefs[KEY_DEFAULT_LENS_ZOOM] ?: 1.0f,
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
            videoAspectRatio = prefs[KEY_VIDEO_ASPECT_RATIO] ?: "16:9",
            cropMode = CameraCropMode.fromString(
                prefs[KEY_CAMERA_CROP_MODE],
                fallback = CameraCropMode.FULL
            ),
            photoOutputFormat = CameraOutputFormat.fromString(
                prefs[KEY_PHOTO_OUTPUT_FORMAT],
                fallback = CameraOutputFormat.PORTRAIT_FULL
            ),
            videoOutputFormat = CameraOutputFormat.fromString(
                prefs[KEY_VIDEO_OUTPUT_FORMAT],
                fallback = CameraOutputFormat.LANDSCAPE_FULL
            ),
            photoQualityPreset = OutputQualityPreset.fromString(
                prefs[KEY_PHOTO_QUALITY_PRESET],
                fallback = OutputQualityPreset.HIGH
            ),
            videoQualityPreset = OutputQualityPreset.fromString(
                prefs[KEY_VIDEO_QUALITY_PRESET],
                fallback = OutputQualityPreset.HIGH
            ),
            videoStabilizationMode = VideoStabilizationMode.fromString(prefs[KEY_VIDEO_STABILIZATION_MODE]),
            autofocusMode = CameraAutofocusMode.fromString(prefs[KEY_AUTOFOCUS_MODE]),
            photoOutputSizeOptimization = prefs[KEY_PHOTO_OUTPUT_SIZE_OPTIMIZATION] ?: true,
            photoEnhancementEnabled = prefs[KEY_PHOTO_ENHANCEMENT_ENABLED] ?: true,
            videoOutputSizeOptimization = prefs[KEY_VIDEO_OUTPUT_SIZE_OPTIMIZATION] ?: false,
            videoNoiseReductionEnabled = prefs[KEY_VIDEO_NOISE_REDUCTION_ENABLED] ?: true,
            autoBrightnessEnabled = prefs[KEY_CAMERA_AUTO_BRIGHTNESS] ?: true,
            manualPreviewBrightness = (prefs[KEY_CAMERA_MANUAL_BRIGHTNESS] ?: 0.5f).coerceIn(0.0f, 1.0f)
        )
    }

    val projectProfileFlow: Flow<ProjectProfile> = observedDataFlow.map { prefs ->
        parseProjectProfileFromPrefs(prefs)
    }.distinctUntilChanged()

    private fun parseProjectProfileFromPrefs(prefs: Preferences): ProjectProfile {
        val savedActivity = prefs[KEY_ACTIVITY]
        val rawWorkType = prefs[KEY_WORK_TYPE] ?: if (
            savedActivity == "MM" || savedActivity == "RM" || savedActivity == "LIE"
        ) savedActivity else ""
        val rawActivity = when {
            savedActivity == null -> "DBM"
            savedActivity == "MM" || savedActivity == "RM" || savedActivity == "LIE" -> "DBM"
            else -> savedActivity
        }
        return ProjectProfile(
            highwayId = prefs[KEY_HIGHWAY_ID] ?: "",
            packageSection = prefs[KEY_PACKAGE_SECTION] ?: "",
            startChainage = prefs[KEY_PROJECT_START_CHAINAGE] ?: "0+000",
            endChainage = prefs[KEY_PROJECT_END_CHAINAGE] ?: "50+000",
            workType = rawWorkType,
            roadSide = RoadSide.fromString(prefs[KEY_ROAD_SIDE]),
            lane = prefs[KEY_LANE] ?: "I/L",
            activity = rawActivity,
            subActivity = prefs[KEY_SUB_ACTIVITY] ?: "",
            customDescription = prefs[KEY_DESCRIPTION] ?: "",
            inspectorName = prefs[KEY_INSPECTOR] ?: "",
            separator = prefs[KEY_SEPARATOR] ?: "||"
        )
    }

    val overlayConfigFlow: Flow<OverlayConfig> = observedDataFlow.map { prefs ->
        parseOverlayConfigFromPrefs(prefs)
    }.distinctUntilChanged()

    private fun parseOverlayConfigFromPrefs(prefs: Preferences): OverlayConfig {
        val templateName = prefs[KEY_TEMPLATE] ?: OverlayTemplate.HIGHWAY_CONSTRUCTION.name
        val template = try { OverlayTemplate.valueOf(templateName) } catch (_: Exception) { OverlayTemplate.HIGHWAY_CONSTRUCTION }
        val saveOriginalDual = prefs[KEY_SAVE_ORIGINAL_IMAGE] ?: prefs[KEY_SAVE_ORIGINAL_AND_STAMPED] ?: false
        return OverlayConfig(
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
            saveOriginalImage = saveOriginalDual,
            timestampMarginMode = prefs[KEY_TIMESTAMP_MARGIN_MODE] ?: "One line",
            showIndexNumber = prefs[KEY_SHOW_INDEX_NUMBER] ?: true,
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
            videoPaddingRightDp = prefs[KEY_VIDEO_PADDING_RIGHT_DP] ?: 16,
            pdfLogoUri = prefs[KEY_PDF_LOGO_URI],
            includePdfLogoDefault = prefs[KEY_INCLUDE_PDF_LOGO_DEFAULT] ?: true,
            pdfSignatureUri = prefs[KEY_PDF_SIGNATURE_URI],
            includePdfSignatureDefault = prefs[KEY_INCLUDE_PDF_SIGNATURE_DEFAULT] ?: true,
            pdfDefaultPreparedBy = prefs[KEY_PDF_DEFAULT_PREPARED_BY] ?: "Site Engineer",
            photoMiniMap = MiniMapStyleConfig.fromJsonString(prefs[KEY_PHOTO_MINI_MAP_JSON]),
            videoMiniMap = MiniMapStyleConfig.fromJsonString(prefs[KEY_VIDEO_MINI_MAP_JSON]),
            showWeatherInfo = prefs[KEY_SHOW_WEATHER_INFO] ?: false,
            weatherShowTemperature = prefs[KEY_WEATHER_SHOW_TEMPERATURE] ?: true,
            weatherShowCondition = prefs[KEY_WEATHER_SHOW_CONDITION] ?: false,
            weatherShowLocationName = prefs[KEY_WEATHER_SHOW_LOCATION_NAME] ?: true,
            weatherShowCoordinates = prefs[KEY_WEATHER_SHOW_COORDINATES] ?: true,
            weatherShowHumidity = prefs[KEY_WEATHER_SHOW_HUMIDITY] ?: false,
            weatherShowWindSpeed = prefs[KEY_WEATHER_SHOW_WIND_SPEED] ?: false,
            weatherShowWindDirection = prefs[KEY_WEATHER_SHOW_WIND_DIRECTION] ?: false,
            weatherDisplayFormat = com.example.model.WeatherDisplayFormat.fromString(prefs[KEY_WEATHER_DISPLAY_FORMAT]),
            weatherOmitIfStale = prefs[KEY_WEATHER_OMIT_IF_STALE] ?: true
        )
    }

    val activeRouteIdFlow: Flow<String?> = observedDataFlow.map { prefs ->
        prefs[KEY_ACTIVE_ROUTE_ID]
    }.distinctUntilChanged()

    val definedProjectsFlow: Flow<List<DefinedProject>> = observedDataFlow.map { prefs ->
        val jsonStr = prefs[KEY_DEFINED_PROJECTS]
        parseDefinedProjects(jsonStr)
    }.distinctUntilChanged()

    suspend fun updateDefinedProjects(projects: List<DefinedProject>) {
        safeEdit { prefs ->
            prefs[KEY_DEFINED_PROJECTS] = serializeDefinedProjects(projects)
        }
    }

    private fun serializeDefinedProjects(projects: List<DefinedProject>): String {
        val array = JSONArray()
        for (p in projects) {
            val obj = JSONObject()
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
            val array = JSONArray(jsonStr)
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun updateProjectProfile(profile: ProjectProfile) {
        safeEdit { prefs ->
            writeProjectProfileToPrefs(prefs, profile)
        }
    }

    suspend fun editProjectProfile(transform: (ProjectProfile) -> ProjectProfile): ProjectProfile {
        var updated = ProjectProfile()
        safeEdit { prefs ->
            val current = parseProjectProfileFromPrefs(prefs)
            updated = transform(current)
            writeProjectProfileToPrefs(prefs, updated)
        }
        return updated
    }

    private fun writeProjectProfileToPrefs(
        prefs: MutablePreferences,
        profile: ProjectProfile
    ) {
        prefs[KEY_HIGHWAY_ID] = profile.highwayId
        prefs[KEY_PACKAGE_SECTION] = profile.packageSection
        prefs[KEY_PROJECT_START_CHAINAGE] = profile.startChainage
        prefs[KEY_PROJECT_END_CHAINAGE] = profile.endChainage
        prefs[KEY_WORK_TYPE] = profile.workType
        prefs[KEY_ROAD_SIDE] = profile.roadSide.label
        prefs[KEY_LANE] = profile.lane
        prefs[KEY_ACTIVITY] = profile.activity
        prefs[KEY_SUB_ACTIVITY] = profile.subActivity
        prefs[KEY_DESCRIPTION] = profile.customDescription
        prefs[KEY_INSPECTOR] = profile.inspectorName
        prefs[KEY_SEPARATOR] = profile.separator
    }

    suspend fun updateOverlayConfig(config: OverlayConfig) {
        safeEdit { prefs ->
            writeOverlayConfigToPrefs(prefs, config)
        }
    }

    suspend fun editOverlayConfig(transform: (OverlayConfig) -> OverlayConfig): OverlayConfig {
        var updated = OverlayConfig()
        safeEdit { prefs ->
            val current = parseOverlayConfigFromPrefs(prefs)
            updated = transform(current)
            writeOverlayConfigToPrefs(prefs, updated)
        }
        return updated
    }

    suspend fun updateLastDetectedLocationName(locationName: String) {
        safeEdit { prefs ->
            prefs[KEY_LAST_DETECTED_LOCATION_NAME] = locationName
        }
    }

    fun getInitialCachedWeatherObservation(): com.example.model.WeatherObservation? {
        return com.example.model.WeatherObservation.fromJsonString(
            getInitialPreferences()[KEY_LAST_WEATHER_OBSERVATION_JSON]
        )
    }

    suspend fun saveCachedWeatherObservation(observation: com.example.model.WeatherObservation) {
        safeEdit { prefs ->
            prefs[KEY_LAST_WEATHER_OBSERVATION_JSON] = observation.toJsonString()
        }
    }

    suspend fun saveCachedWeatherForecastBundle(bundle: com.example.model.WeatherForecastBundle) {
        safeEdit { prefs ->
            prefs[KEY_LAST_WEATHER_FORECAST_BUNDLE_JSON] = bundle.toJsonString()
            prefs[KEY_LAST_WEATHER_OBSERVATION_JSON] = bundle.current.toJsonString()
        }
    }

    private fun writeOverlayConfigToPrefs(
        prefs: MutablePreferences,
        config: OverlayConfig
    ) {
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
        prefs[KEY_SAVE_ORIGINAL_IMAGE] = config.saveOriginalImage
        prefs[KEY_SAVE_ORIGINAL_AND_STAMPED] = config.saveOriginalImage
        prefs[KEY_TIMESTAMP_MARGIN_MODE] = config.timestampMarginMode
        prefs[KEY_SHOW_INDEX_NUMBER] = config.showIndexNumber
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
        if (config.pdfLogoUri != null) {
            prefs[KEY_PDF_LOGO_URI] = config.pdfLogoUri
        } else {
            prefs.remove(KEY_PDF_LOGO_URI)
        }
        prefs[KEY_INCLUDE_PDF_LOGO_DEFAULT] = config.includePdfLogoDefault
        if (config.pdfSignatureUri != null) {
            prefs[KEY_PDF_SIGNATURE_URI] = config.pdfSignatureUri
        } else {
            prefs.remove(KEY_PDF_SIGNATURE_URI)
        }
        prefs[KEY_INCLUDE_PDF_SIGNATURE_DEFAULT] = config.includePdfSignatureDefault
        prefs[KEY_PDF_DEFAULT_PREPARED_BY] = config.pdfDefaultPreparedBy
        prefs[KEY_PHOTO_MINI_MAP_JSON] = config.photoMiniMap.toJsonString()
        prefs[KEY_VIDEO_MINI_MAP_JSON] = config.videoMiniMap.toJsonString()
        prefs[KEY_SHOW_WEATHER_INFO] = config.showWeatherInfo
        prefs[KEY_WEATHER_SHOW_TEMPERATURE] = config.weatherShowTemperature
        prefs[KEY_WEATHER_SHOW_CONDITION] = config.weatherShowCondition
        prefs[KEY_WEATHER_SHOW_LOCATION_NAME] = config.weatherShowLocationName
        prefs[KEY_WEATHER_SHOW_COORDINATES] = config.weatherShowCoordinates
        prefs[KEY_WEATHER_SHOW_HUMIDITY] = config.weatherShowHumidity
        prefs[KEY_WEATHER_SHOW_WIND_SPEED] = config.weatherShowWindSpeed
        prefs[KEY_WEATHER_SHOW_WIND_DIRECTION] = config.weatherShowWindDirection
        prefs[KEY_WEATHER_DISPLAY_FORMAT] = config.weatherDisplayFormat.name
        prefs[KEY_WEATHER_OMIT_IF_STALE] = config.weatherOmitIfStale
    }

    suspend fun updateCameraSettings(settings: CameraSettings) {
        safeEdit { prefs ->
            writeCameraSettingsToPrefs(prefs, settings)
        }
    }

    suspend fun editCameraSettings(transform: (CameraSettings) -> CameraSettings): CameraSettings {
        var updated = CameraSettings()
        safeEdit { prefs ->
            val current = parseCameraSettingsFromPrefs(prefs)
            updated = transform(current)
            writeCameraSettingsToPrefs(prefs, updated)
        }
        return updated
    }

    private fun writeCameraSettingsToPrefs(
        prefs: MutablePreferences,
        settings: CameraSettings
    ) {
        prefs[KEY_CAPTURE_TIMER] = settings.captureTimer.seconds
        prefs[KEY_GRID_MODE] = settings.gridMode.name
        prefs[KEY_SHOW_HORIZON_LEVEL] = settings.showHorizonLevel
        prefs[KEY_PREVIEW_RATIO] = settings.previewRatio.name
        prefs[KEY_CAPTURE_SOUND] = settings.captureSound
        prefs[KEY_VOLUME_SHUTTER] = settings.volumeButtonShutter
        prefs[KEY_FRONT_MIRROR] = settings.frontCameraMirror
        prefs[KEY_JPEG_QUALITY] = settings.jpegQuality
        prefs[KEY_SAVE_ORIGINAL_AND_STAMPED] = settings.saveOriginalAndStamped
        prefs[KEY_SAVE_ORIGINAL_IMAGE] = settings.saveOriginalAndStamped
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
        prefs[KEY_DEFAULT_LENS_ZOOM] = settings.defaultLensZoom

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
        prefs[KEY_CAMERA_CROP_MODE] = settings.cropMode.name
        prefs[KEY_PHOTO_OUTPUT_FORMAT] = settings.photoOutputFormat.name
        prefs[KEY_VIDEO_OUTPUT_FORMAT] = settings.videoOutputFormat.name
        prefs[KEY_PHOTO_QUALITY_PRESET] = settings.photoQualityPreset.name
        prefs[KEY_VIDEO_QUALITY_PRESET] = settings.videoQualityPreset.name
        prefs[KEY_VIDEO_STABILIZATION_MODE] = settings.videoStabilizationMode.name
        prefs[KEY_AUTOFOCUS_MODE] = settings.autofocusMode.name
        prefs[KEY_PHOTO_OUTPUT_SIZE_OPTIMIZATION] = settings.photoOutputSizeOptimization
        prefs[KEY_PHOTO_ENHANCEMENT_ENABLED] = settings.photoEnhancementEnabled
        prefs[KEY_VIDEO_OUTPUT_SIZE_OPTIMIZATION] = settings.videoOutputSizeOptimization
        prefs[KEY_VIDEO_NOISE_REDUCTION_ENABLED] = settings.videoNoiseReductionEnabled
        prefs[KEY_CAMERA_AUTO_BRIGHTNESS] = settings.autoBrightnessEnabled
        prefs[KEY_CAMERA_MANUAL_BRIGHTNESS] = settings.manualPreviewBrightness.coerceIn(0.0f, 1.0f)
    }

    suspend fun updateIsFlashOn(enabled: Boolean) {
        safeEdit { prefs ->
            prefs[KEY_IS_FLASH_ON] = enabled
        }
    }

    suspend fun updateIsFrontCamera(isFront: Boolean) {
        safeEdit { prefs ->
            prefs[KEY_IS_FRONT_CAMERA] = isFront
        }
    }

    suspend fun updateCaptureMode(mode: CaptureMode) {
        safeEdit { prefs ->
            prefs[KEY_CAPTURE_MODE] = mode.name
        }
    }

    val preferredExternalNavAppIdFlow: Flow<String?> = observedDataFlow.map { prefs ->
        prefs[KEY_PREFERRED_EXTERNAL_NAV_APP_ID]?.takeIf { it.isNotBlank() }
    }.distinctUntilChanged()

    suspend fun updatePreferredExternalNavAppId(appId: String?) {
        safeEdit { prefs ->
            if (!appId.isNullOrBlank()) {
                prefs[KEY_PREFERRED_EXTERNAL_NAV_APP_ID] = appId
            } else {
                prefs.remove(KEY_PREFERRED_EXTERNAL_NAV_APP_ID)
            }
        }
    }

    val surveyShow200mLabelsFlow: Flow<Boolean> = observedDataFlow.map { prefs ->
        prefs[KEY_SURVEY_SHOW_200M_LABELS] ?: true
    }.distinctUntilChanged()

    val surveyShowCentrelineFlow: Flow<Boolean> = observedDataFlow.map { prefs ->
        prefs[KEY_SURVEY_SHOW_CENTRELINE] ?: true
    }.distinctUntilChanged()

    val surveyShowGpsTrackFlow: Flow<Boolean> = observedDataFlow.map { prefs ->
        prefs[KEY_SURVEY_SHOW_GPS_TRACK] ?: true
    }.distinctUntilChanged()

    val surveyShowLaneTrackFlow: Flow<Boolean> = observedDataFlow.map { prefs ->
        prefs[KEY_SURVEY_SHOW_LANE_TRACK] ?: true
    }.distinctUntilChanged()

    val surveyShowMarkersFlow: Flow<Boolean> = observedDataFlow.map { prefs ->
        prefs[KEY_SURVEY_SHOW_MARKERS] ?: true
    }.distinctUntilChanged()

    val surveyMapTypeFlow: Flow<String> = observedDataFlow.map { prefs ->
        prefs[KEY_SURVEY_MAP_TYPE] ?: "standard"
    }.distinctUntilChanged()

    suspend fun updateSurveyMapPreferences(
        show200mLabels: Boolean? = null,
        showCentreline: Boolean? = null,
        showGpsTrack: Boolean? = null,
        showLaneTrack: Boolean? = null,
        showMarkers: Boolean? = null,
        mapType: String? = null
    ) {
        safeEdit { prefs ->
            if (show200mLabels != null) prefs[KEY_SURVEY_SHOW_200M_LABELS] = show200mLabels
            if (showCentreline != null) prefs[KEY_SURVEY_SHOW_CENTRELINE] = showCentreline
            if (showGpsTrack != null) prefs[KEY_SURVEY_SHOW_GPS_TRACK] = showGpsTrack
            if (showLaneTrack != null) prefs[KEY_SURVEY_SHOW_LANE_TRACK] = showLaneTrack
            if (showMarkers != null) prefs[KEY_SURVEY_SHOW_MARKERS] = showMarkers
            if (mapType != null) prefs[KEY_SURVEY_MAP_TYPE] = mapType
        }
    }

    suspend fun resetAllSettings() {
        safeEdit { prefs ->
            prefs.clear()
        }
    }

    suspend fun setActiveRouteId(routeId: String?) {
        safeEdit { prefs ->
            if (routeId != null) {
                prefs[KEY_ACTIVE_ROUTE_ID] = routeId
            } else {
                prefs.remove(KEY_ACTIVE_ROUTE_ID)
            }
        }
    }

    suspend fun updateSelectedAlignmentId(alignmentId: String?) {
        safeEdit { prefs ->
            if (!alignmentId.isNullOrBlank()) {
                prefs[KEY_SELECTED_ALIGNMENT_ID] = alignmentId
            } else {
                prefs.remove(KEY_SELECTED_ALIGNMENT_ID)
            }
        }
    }

    val kmzCalibrationsFlow: Flow<Map<String, KmzManualCalibration>> = observedDataFlow.map { prefs ->
        prefs[KEY_KMZ_MANUAL_CALIBRATIONS_JSON]
    }.distinctUntilChanged().map { rawJson ->
        parseKmzCalibrations(rawJson)
    }

    val manualChainageOverrideFlow: Flow<Chainage?> = observedDataFlow.map { prefs ->
        val raw = prefs[KEY_MANUAL_CHAINAGE_OVERRIDE]
        if (!raw.isNullOrBlank()) Chainage.parse(raw) else null
    }.distinctUntilChanged()

    val manualChainageTextFlow: Flow<String?> = observedDataFlow.map { prefs ->
        val custom = prefs[KEY_MANUAL_CHAINAGE_CUSTOM_TEXT]
        val raw = prefs[KEY_MANUAL_CHAINAGE_OVERRIDE]
        when {
            !custom.isNullOrBlank() -> custom
            !raw.isNullOrBlank() -> raw
            else -> null
        }
    }.distinctUntilChanged()

    val manualChainageCalibrationFlow: Flow<com.example.model.ManualChainageCalibrationState> =
        observedDataFlow.map { prefs ->
            parseManualChainageCalibration(prefs)
        }.distinctUntilChanged()

    val savedCustomChainagesFlow: Flow<List<String>> = observedDataFlow.map { prefs ->
        prefs[KEY_SAVED_CUSTOM_CHAINAGES].orEmpty()
    }.distinctUntilChanged().map { raw ->
        if (raw.isBlank()) emptyList()
        else raw.split("||").map { it.trim() }.filter { it.isNotBlank() }
    }

    suspend fun saveKmzCalibration(calibration: KmzManualCalibration) {
        safeEdit { prefs ->
            val current = parseKmzCalibrations(prefs[KEY_KMZ_MANUAL_CALIBRATIONS_JSON]).toMutableMap()
            val key = com.example.geo.KmzCalibrationEngine.storageKey(
                calibration.routeId,
                calibration.alignmentId
            )
            current[key] = calibration
            prefs[KEY_KMZ_MANUAL_CALIBRATIONS_JSON] = serializeKmzCalibrations(current)
        }
    }

    suspend fun removeKmzCalibration(routeId: String, alignmentId: String? = null) {
        safeEdit { prefs ->
            val current = parseKmzCalibrations(prefs[KEY_KMZ_MANUAL_CALIBRATIONS_JSON]).toMutableMap()
            if (!alignmentId.isNullOrBlank()) {
                val exactKey = com.example.geo.KmzCalibrationEngine.storageKey(routeId, alignmentId)
                current.remove(exactKey)
                // Also clean any fallback key stored under routeId alone if matching route
                val routeFallbackKey = com.example.geo.KmzCalibrationEngine.storageKey(routeId, routeId)
                if (current[routeFallbackKey]?.alignmentId == alignmentId ||
                    current[routeFallbackKey]?.alignmentId.isNullOrBlank()
                ) {
                    current.remove(routeFallbackKey)
                }
            } else {
                val keysToRemove = current.entries
                    .filter { it.value.routeId == routeId }
                    .map { it.key }
                keysToRemove.forEach { current.remove(it) }
            }
            if (current.isEmpty()) {
                prefs.remove(KEY_KMZ_MANUAL_CALIBRATIONS_JSON)
            } else {
                prefs[KEY_KMZ_MANUAL_CALIBRATIONS_JSON] = serializeKmzCalibrations(current)
            }
        }
    }

    fun parseKmzCalibrations(rawJson: String?): Map<String, KmzManualCalibration> {
        if (rawJson.isNullOrBlank()) return emptyMap()
        val result = linkedMapOf<String, KmzManualCalibration>()
        try {
            val root = JSONObject(rawJson)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val obj = root.optJSONObject(key) ?: continue
                val cal = KmzManualCalibration.fromJson(obj) ?: continue
                val normalizedKey = com.example.geo.KmzCalibrationEngine.storageKey(cal.routeId, cal.alignmentId)
                result[normalizedKey] = cal
            }
        } catch (_: Exception) {}
        return result
    }

    fun serializeKmzCalibrations(map: Map<String, KmzManualCalibration>): String {
        val root = JSONObject()
        for ((k, cal) in map) {
            root.put(k, cal.toJson())
        }
        return root.toString()
    }

    suspend fun setManualChainageOverride(chainage: Chainage?, customText: String? = null) {
        safeEdit { prefs ->
            val formatted = customText?.trim()?.takeIf { it.isNotBlank() } ?: chainage?.format()
            if (formatted != null) {
                prefs[KEY_MANUAL_CHAINAGE_CUSTOM_TEXT] = formatted
                if (chainage != null) {
                    prefs[KEY_MANUAL_CHAINAGE_OVERRIDE] = chainage.format()
                } else {
                    prefs.remove(KEY_MANUAL_CHAINAGE_OVERRIDE)
                }
            } else {
                prefs.remove(KEY_MANUAL_CHAINAGE_CUSTOM_TEXT)
                prefs.remove(KEY_MANUAL_CHAINAGE_OVERRIDE)
            }
        }
    }

    suspend fun updateManualChainageCalibration(state: com.example.model.ManualChainageCalibrationState) {
        safeEdit { prefs ->
            prefs[KEY_MANUAL_CHAINAGE_CALIBRATION_JSON] = state.toJsonString()
            if (state.isActive) {
                val formatted = state.effectiveDisplayChainage
                if (!formatted.isNullOrBlank()) {
                    prefs[KEY_MANUAL_CHAINAGE_CUSTOM_TEXT] = formatted
                    val ch = state.effectiveCurrentMetres?.let { Chainage(it) } ?: Chainage.parse(formatted)
                    if (ch != null) {
                        prefs[KEY_MANUAL_CHAINAGE_OVERRIDE] = ch.format()
                    }
                }
            } else {
                prefs.remove(KEY_MANUAL_CHAINAGE_CUSTOM_TEXT)
                prefs.remove(KEY_MANUAL_CHAINAGE_OVERRIDE)
            }
        }
    }

    suspend fun updateLastReliableChainageSnapshot(snapshot: com.example.model.LastReliableChainageSnapshot?) {
        safeEdit { prefs ->
            if (snapshot != null) {
                prefs[KEY_LAST_RELIABLE_CHAINAGE_JSON] = snapshot.toJsonString()
            } else {
                prefs.remove(KEY_LAST_RELIABLE_CHAINAGE_JSON)
            }
        }
    }

    suspend fun saveCustomChainagesList(items: List<String>) {
        safeEdit { prefs ->
            val cleaned = items.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (cleaned.isEmpty()) {
                prefs.remove(KEY_SAVED_CUSTOM_CHAINAGES)
            } else {
                prefs[KEY_SAVED_CUSTOM_CHAINAGES] = cleaned.joinToString("||")
            }
        }
    }


    /**
     * Reads every persisted entry from ctecs_user_preferences DataStore dynamically with runtime type.
     */
    suspend fun readRawPreferencesSnapshot(): Map<String, Pair<PrefValueType, Any>> {
        val prefs = appContext.dataStore.data.first().also { cachedPreferences = it }
        val rawMap = prefs.asMap()
        val snapshot = linkedMapOf<String, Pair<PrefValueType, Any>>()
        rawMap.forEach { (key, value) ->
            val name = key.name
            when (value) {
                is String -> snapshot[name] = PrefValueType.STRING to value
                is Boolean -> snapshot[name] = PrefValueType.BOOLEAN to value
                is Int -> snapshot[name] = PrefValueType.INT to value
                is Float -> snapshot[name] = PrefValueType.FLOAT to value
                is Long -> snapshot[name] = PrefValueType.LONG to value
                is Double -> snapshot[name] = PrefValueType.DOUBLE to value
                is Set<*> -> {
                    val strSet = value.mapNotNull { it?.toString() }.toSet()
                    snapshot[name] = PrefValueType.STRING_SET to strSet
                }
            }
        }
        return snapshot
    }

    /**
     * Atomically rolls back ctecs_user_preferences to a previously captured raw snapshot.
     */
    suspend fun restoreRawPreferencesSnapshot(snapshot: Map<String, Pair<PrefValueType, Any>>) {
        safeEdit { prefs ->
            prefs.clear()
            snapshot.forEach { (keyName, typedPair) ->
                val (type, value) = typedPair
                writeTypedEntry(prefs, keyName, type, value)
            }
        }
    }

    /**
     * Atomically applies restored backup mutations in a single DataStore transaction.
     */
    suspend fun applyBackupEntriesTransaction(
        entries: List<TypedPrefEntry>,
        keysToRemove: Set<String> = emptySet(),
        beforeCommitHook: (() -> Unit)? = null
    ) {
        safeEdit { prefs ->
            keysToRemove.forEach { keyName ->
                removeAnyTypedKey(prefs, keyName)
            }
            entries.forEach { entry ->
                removeAnyTypedKey(prefs, entry.key)
                writeTypedEntry(prefs, entry.key, entry.type, entry.value)
            }
            beforeCommitHook?.invoke()
        }
    }

    private fun removeAnyTypedKey(
        prefs: MutablePreferences,
        keyName: String
    ) {
        prefs.remove(stringPreferencesKey(keyName))
        prefs.remove(booleanPreferencesKey(keyName))
        prefs.remove(intPreferencesKey(keyName))
        prefs.remove(floatPreferencesKey(keyName))
        prefs.remove(longPreferencesKey(keyName))
        prefs.remove(doublePreferencesKey(keyName))
        prefs.remove(stringSetPreferencesKey(keyName))
    }

    private fun writeTypedEntry(
        prefs: MutablePreferences,
        keyName: String,
        type: PrefValueType,
        value: Any
    ) {
        when (type) {
            PrefValueType.STRING -> prefs[stringPreferencesKey(keyName)] = value.toString()
            PrefValueType.BOOLEAN -> prefs[booleanPreferencesKey(keyName)] =
                value as? Boolean ?: value.toString().toBoolean()
            PrefValueType.INT -> prefs[intPreferencesKey(keyName)] =
                (value as? Number)?.toInt() ?: value.toString().toIntOrNull() ?: 0
            PrefValueType.FLOAT -> prefs[floatPreferencesKey(keyName)] =
                (value as? Number)?.toFloat() ?: value.toString().toFloatOrNull() ?: 0f
            PrefValueType.LONG -> prefs[longPreferencesKey(keyName)] =
                (value as? Number)?.toLong() ?: value.toString().toLongOrNull() ?: 0L
            PrefValueType.DOUBLE -> prefs[doublePreferencesKey(keyName)] =
                (value as? Number)?.toDouble() ?: value.toString().toDoubleOrNull() ?: 0.0
            PrefValueType.STRING_SET -> {
                @Suppress("UNCHECKED_CAST")
                val setVal = (value as? Set<*>)?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
                prefs[stringSetPreferencesKey(keyName)] = setVal
            }
        }
    }
}
