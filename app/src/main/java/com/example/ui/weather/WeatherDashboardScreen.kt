package com.example.ui.weather

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.ActivitySuitabilityAssessment
import com.example.model.ActualSiteMeasurements
import com.example.model.ContractSpecificationConfig
import com.example.model.DailyWeatherPoint
import com.example.model.DelayRiskSummary
import com.example.model.HighwayWorkActivity
import com.example.model.HourlyWeatherPoint
import com.example.model.HourlyWorkWindow
import com.example.model.TemperatureUnit
import com.example.model.WeatherAlertItem
import com.example.model.WeatherAlertSeverity
import com.example.model.WeatherConditionType
import com.example.model.WeatherForecastBundle
import com.example.model.WeatherIntelligenceSettings
import com.example.model.WeatherObservation
import com.example.model.WeatherUiState
import com.example.ui.home.AnimatedWeatherIcon
import com.example.ui.home.weatherAtmosphereColor
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import com.example.weather.IrcMorthWeatherRuleEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class EssentialWeatherMetric(
    val key: String,
    val label: String,
    val value: String,
    val accent: Color? = null
)

/**
 * Minimalist, high-clarity CTECS Weather Page with Smart Hourly Highway Work Prediction.
 *
 * Exact Section Order (zero duplicated metrics or redundant cards):
 * 1. Main Weather Card (Current temperature, weather condition & icon, feels-like, location / chainage,
 *    and one important weather alert inline if applicable — hidden completely when none)
 * 2. Smart Hourly Highway Activity Prediction (immediately below Main Weather Card:
 *    activity selector, best working window, 12–24h prediction cards with MoRTH/IRC engineering reasons)
 * 3. Essential Weather Details (Rain %, Wind, Humidity, UV Index, Visibility, Sunrise/Sunset — only when available)
 * 4. Compact General Hourly Forecast (next 12 important hours)
 * 5. Seven-Day Forecast (one compact row per day)
 *
 * Responsive across portrait and landscape layouts and supports both dark and light CTECS themes.
 */
@Composable
fun WeatherDashboardScreen(
    weatherUiState: WeatherUiState,
    forecastBundle: WeatherForecastBundle?,
    resolvedLocationName: String,
    is24HourFormat: Boolean,
    settings: WeatherIntelligenceSettings,
    assessments: List<ActivitySuitabilityAssessment>,
    selectedActivity: HighwayWorkActivity,
    hourlyWorkTimeline: List<HourlyWorkWindow>,
    siteMeasurements: ActualSiteMeasurements,
    delayRiskSummary: DelayRiskSummary,
    actionableAlerts: List<WeatherAlertItem>,
    onSelectActivity: (HighwayWorkActivity) -> Unit,
    onUpdateContractSpec: (ContractSpecificationConfig) -> Unit,
    onUpdateSiteMeasurements: (ActualSiteMeasurements) -> Unit,
    onRefreshWeather: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    nowMs: Long = System.currentTimeMillis(),
    liveHighwayLocation: LiveHighwayLocationSnapshot = LiveHighwayLocationSnapshot(),
    expandedSections: Set<String>? = null,
    onToggleSection: ((String) -> Unit)? = null,
    onUpdateWeatherSettings: (((WeatherIntelligenceSettings) -> WeatherIntelligenceSettings) -> Unit)? = null
) {
    val c = CtecsTheme.colors
    val isDark = c.isDark
    val lifecycleOwner = LocalLifecycleOwner.current
    var isLifecycleResumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isLifecycleResumed = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isLifecycleResumed = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var localExpanded by remember { mutableStateOf<Set<String>>(emptySet()) }
    val effectiveExpanded = expandedSections ?: localExpanded
    val toggleSection: (String) -> Unit = { key ->
        if (onToggleSection != null) {
            onToggleSection(key)
        } else {
            localExpanded = if (key in localExpanded) localExpanded - key else localExpanded + key
        }
    }

    var showWeatherSettingsPopup by remember { mutableStateOf(false) }
    var localWeatherSettingsOverride by remember(settings) { mutableStateOf(settings) }
    val activeSettings = if (onUpdateWeatherSettings != null) settings else localWeatherSettingsOverride
    val handleUpdateWeatherSettings: ((WeatherIntelligenceSettings) -> WeatherIntelligenceSettings) -> Unit = { transform ->
        if (onUpdateWeatherSettings != null) {
            onUpdateWeatherSettings(transform)
        } else {
            localWeatherSettingsOverride = transform(localWeatherSettingsOverride)
        }
    }

    val shouldAnimate = isLifecycleResumed && activeSettings.animationsEnabled
    val unit = activeSettings.temperatureUnit
    val settingsGearRotation by animateFloatAsState(
        targetValue = if (showWeatherSettingsPopup) 90f else 0f,
        animationSpec = tween(if (shouldAnimate) 220 else 0),
        label = "weather_settings_gear_rot"
    )

    val activeObservation: WeatherObservation? = forecastBundle?.current ?: when (weatherUiState) {
        is WeatherUiState.Available -> weatherUiState.observation
        is WeatherUiState.Loading -> weatherUiState.previous
        is WeatherUiState.Unavailable -> weatherUiState.cachedObservation
        WeatherUiState.WaitingForGps -> null
    }
    val isRefreshing = weatherUiState is WeatherUiState.Loading ||
        (weatherUiState is WeatherUiState.Available && weatherUiState.isRefreshing)
    val isOffline = weatherUiState is WeatherUiState.Unavailable
    val staleMs = activeSettings.staleDataThresholdMinutes.toLong() * 60_000L
    val isStale = activeObservation != null && !activeObservation.isFreshAt(nowMs, staleMs)

    val atmosphereColor = weatherAtmosphereColor(
        activeObservation?.visualCondition ?: WeatherConditionType.PARTLY_CLOUDY,
        isDark
    )

    val displayLocation = resolvedLocationName
        .ifBlank { liveHighwayLocation.gpsLocationName }
        .ifBlank { activeObservation?.locationName.orEmpty() }
        .ifBlank { "Current Location" }

    val timeFmt = remember(is24HourFormat) {
        SimpleDateFormat(if (is24HourFormat) "HH:mm" else "h:mm a", Locale.US)
    }

    val updatedLabel = remember(activeObservation?.observedAtMs, is24HourFormat) {
        activeObservation?.let { timeFmt.format(Date(it.observedAtMs)) }.orEmpty()
    }

    // Deduplicated active warnings — strictly hidden when empty or disabled in Weather Settings
    val uniqueAlerts = remember(actionableAlerts, forecastBundle?.alerts, activeSettings.weatherAlertsEnabled) {
        if (!activeSettings.weatherAlertsEnabled) return@remember emptyList()
        val combined = actionableAlerts + forecastBundle?.alerts.orEmpty()
        val seen = LinkedHashSet<String>()
        combined.filter { alert ->
            val key = "${alert.category.trim().lowercase(Locale.US)}|${alert.title.trim().lowercase(Locale.US)}"
            alert.title.isNotBlank() && seen.add(key)
        }.sortedByDescending { it.severity.ordinal }
    }

    val effectiveSelectedActivity = if (onUpdateWeatherSettings == null && localWeatherSettingsOverride != settings) {
        activeSettings.primaryActivity
    } else {
        selectedActivity
    }

    val effectiveHourlyTimeline = remember(
        hourlyWorkTimeline,
        forecastBundle,
        effectiveSelectedActivity,
        activeSettings.contractSpec,
        activeSettings.bituminousConfig,
        siteMeasurements,
        nowMs,
        onUpdateWeatherSettings,
        localWeatherSettingsOverride,
        settings
    ) {
        if (onUpdateWeatherSettings == null && localWeatherSettingsOverride != settings && forecastBundle != null) {
            IrcMorthWeatherRuleEngine.generateHourlyWorkTimeline(
                activity = effectiveSelectedActivity,
                hourlyPoints = forecastBundle.next24Hours(nowMs),
                contractSpec = activeSettings.contractSpec,
                siteMeasurements = siteMeasurements,
                bituminousConfig = activeSettings.bituminousConfig
            )
        } else {
            hourlyWorkTimeline
        }
    }

    val filteredHourlyTimeline = remember(effectiveHourlyTimeline, activeSettings.predictionIntervalHours) {
        val step = activeSettings.predictionIntervalHours.coerceIn(1, 3)
        if (step <= 1) effectiveHourlyTimeline
        else effectiveHourlyTimeline.filterIndexed { idx, _ -> idx % step == 0 }
    }

    val nextHours = remember(forecastBundle, nowMs, activeSettings.compactForecastLayout) {
        val count = if (activeSettings.compactForecastLayout) 12 else 24
        forecastBundle?.next24Hours(nowMs).orEmpty().take(count)
    }

    val sevenDayForecast = remember(forecastBundle, activeSettings.forecastDaysCount) {
        forecastBundle?.daily.orEmpty().take(activeSettings.forecastDaysCount.coerceIn(1, 7))
    }

    val statusBadgeText = when {
        activeObservation == null && weatherUiState is WeatherUiState.WaitingForGps -> "GPS"
        activeObservation == null -> "OFFLINE"
        isOffline -> "CACHED"
        isStale -> "STALE"
        else -> "LIVE"
    }
    val statusBadgeColor = when (statusBadgeText) {
        "LIVE" -> c.success
        "CACHED", "STALE" -> c.warning
        else -> c.textMuted
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("weather_dashboard_screen")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Minimal Top Bar: Back + Status indicator + Refresh
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("weather_dashboard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back to Home",
                            tint = c.textPrimary
                        )
                    }
                    Text(
                        text = "Weather",
                        color = c.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusBadgeColor.copy(alpha = if (isDark) 0.16f else 0.11f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .semantics(mergeDescendants = true) {}
                            .testTag("weather_provider_status_bar")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(statusBadgeColor)
                        )
                        Text(
                            text = if (updatedLabel.isNotBlank()) "$statusBadgeText · $updatedLabel" else statusBadgeText,
                            color = statusBadgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }

                    IconButton(
                        onClick = onRefreshWeather,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("weather_dashboard_refresh_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = atmosphereColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh weather",
                                tint = c.textPrimary
                            )
                        }
                    }

                    IconButton(
                        onClick = { showWeatherSettingsPopup = true },
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("weather_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Weather Settings",
                            tint = c.textPrimary,
                            modifier = Modifier.rotate(settingsGearRotation)
                        )
                    }
                }
            }

            // Responsive Portrait / Landscape Content
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val isWideLayout = maxWidth >= 640.dp
                val isAlertsExpanded = WeatherSectionKeys.WEATHER_ALERTS in effectiveExpanded

                if (isWideLayout) {
                    // Responsive Landscape / Tablet 2-Column Layout
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 1040.dp)
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(0.92f)
                                .fillMaxHeight()
                                .testTag("weather_left_column_list"),
                            contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item(key = "hero") {
                                CurrentWeatherMainSection(
                                    observation = activeObservation,
                                    weatherUiState = weatherUiState,
                                    locationLabel = displayLocation,
                                    liveHighwayLocation = liveHighwayLocation,
                                    temperatureUnit = unit,
                                    atmosphereColor = atmosphereColor,
                                    isDark = isDark,
                                    shouldAnimate = shouldAnimate,
                                    alerts = uniqueAlerts,
                                    isAlertsExpanded = isAlertsExpanded,
                                    onToggleAlerts = { toggleSection(WeatherSectionKeys.WEATHER_ALERTS) }
                                )
                            }
                            if (activeObservation != null) {
                                item(key = "essential_details") {
                                    EssentialWeatherDetailsGrid(
                                        observation = activeObservation,
                                        fallbackRainProbability = nextHours.firstOrNull()?.rainProbabilityPercent,
                                        windSpeedUnit = activeSettings.windSpeedUnit,
                                        timeFmt = timeFmt,
                                        isDark = isDark
                                    )
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .weight(1.08f)
                                .fillMaxHeight()
                                .testTag("weather_forecast_tab_list"),
                            contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (activeSettings.activityPredictionEnabled) {
                                item(key = "smart_hourly_prediction") {
                                    SmartHourlyHighwayPredictionSection(
                                        selectedActivity = effectiveSelectedActivity,
                                        assessments = assessments,
                                        hourlyWorkTimeline = filteredHourlyTimeline,
                                        contractSpec = activeSettings.contractSpec,
                                        temperatureUnit = unit,
                                        is24HourFormat = is24HourFormat,
                                        isDark = isDark,
                                        shouldAnimate = shouldAnimate,
                                        isCachedForecast = isOffline && activeObservation != null,
                                        isStaleForecast = isStale,
                                        lastUpdatedLabel = updatedLabel,
                                        onSelectActivity = onSelectActivity,
                                        bituminousConfig = activeSettings.bituminousConfig,
                                        windSpeedUnit = activeSettings.windSpeedUnit,
                                        showBestWorkingWindow = activeSettings.showBestWorkingWindow,
                                        showSuitabilityIndicators = activeSettings.showSuitabilityIndicators,
                                        siteMeasurements = siteMeasurements
                                    )
                                }
                            }
                            if (activeSettings.showHourlyForecastSection && nextHours.isNotEmpty()) {
                                item(key = "hourly") {
                                    CompactHourlyForecastSection(
                                        hours = nextHours,
                                        temperatureUnit = unit,
                                        is24HourFormat = is24HourFormat,
                                        isDark = isDark,
                                        shouldAnimate = shouldAnimate
                                    )
                                }
                            }
                            if (activeSettings.showDailyForecastSection && sevenDayForecast.isNotEmpty()) {
                                item(key = "daily") {
                                    CompactSevenDayForecastSection(
                                        days = sevenDayForecast,
                                        temperatureUnit = unit,
                                        isDark = isDark
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Clean Portrait Single-Column Layout in Exact Minimal Section Order:
                    // 1. Main Weather Card (with inline alert if applicable)
                    // 2. Smart Hourly Highway Activity Prediction & Best Available Working Window
                    // 3. Essential Weather Details
                    // 4. Compact General Hourly Forecast
                    // 5. Seven-Day Forecast
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 600.dp)
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .testTag("weather_forecast_tab_list"),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "hero") {
                            CurrentWeatherMainSection(
                                observation = activeObservation,
                                weatherUiState = weatherUiState,
                                locationLabel = displayLocation,
                                liveHighwayLocation = liveHighwayLocation,
                                temperatureUnit = unit,
                                atmosphereColor = atmosphereColor,
                                isDark = isDark,
                                shouldAnimate = shouldAnimate,
                                alerts = uniqueAlerts,
                                isAlertsExpanded = isAlertsExpanded,
                                onToggleAlerts = { toggleSection(WeatherSectionKeys.WEATHER_ALERTS) }
                            )
                        }

                        if (activeSettings.activityPredictionEnabled) {
                            item(key = "smart_hourly_prediction") {
                                SmartHourlyHighwayPredictionSection(
                                    selectedActivity = effectiveSelectedActivity,
                                    assessments = assessments,
                                    hourlyWorkTimeline = filteredHourlyTimeline,
                                    contractSpec = activeSettings.contractSpec,
                                    temperatureUnit = unit,
                                    is24HourFormat = is24HourFormat,
                                    isDark = isDark,
                                    shouldAnimate = shouldAnimate,
                                    isCachedForecast = isOffline && activeObservation != null,
                                    isStaleForecast = isStale,
                                    lastUpdatedLabel = updatedLabel,
                                    onSelectActivity = onSelectActivity,
                                    bituminousConfig = activeSettings.bituminousConfig,
                                    windSpeedUnit = activeSettings.windSpeedUnit,
                                    showBestWorkingWindow = activeSettings.showBestWorkingWindow,
                                    showSuitabilityIndicators = activeSettings.showSuitabilityIndicators,
                                    siteMeasurements = siteMeasurements
                                )
                            }
                        }

                        if (activeObservation != null) {
                            item(key = "essential_details") {
                                EssentialWeatherDetailsGrid(
                                    observation = activeObservation,
                                    fallbackRainProbability = nextHours.firstOrNull()?.rainProbabilityPercent,
                                    windSpeedUnit = activeSettings.windSpeedUnit,
                                    timeFmt = timeFmt,
                                    isDark = isDark
                                )
                            }
                        }

                        if (activeSettings.showHourlyForecastSection && nextHours.isNotEmpty()) {
                            item(key = "hourly") {
                                CompactHourlyForecastSection(
                                    hours = nextHours,
                                    temperatureUnit = unit,
                                    is24HourFormat = is24HourFormat,
                                    isDark = isDark,
                                    shouldAnimate = shouldAnimate
                                )
                            }
                        }

                        if (activeSettings.showDailyForecastSection && sevenDayForecast.isNotEmpty()) {
                            item(key = "daily") {
                                CompactSevenDayForecastSection(
                                    days = sevenDayForecast,
                                    temperatureUnit = unit,
                                    isDark = isDark
                                )
                            }
                        }

                        item(key = "bottom_space") {
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }

    // Contextual Animated Weather Settings Popup (ONLY Weather Page preferences)
    WeatherSettingsPopup(
        visible = showWeatherSettingsPopup,
        settings = activeSettings,
        lastUpdatedLabel = updatedLabel,
        isOfflineCached = isOffline,
        onUpdateSettings = handleUpdateWeatherSettings,
        onManualRefresh = onRefreshWeather,
        onDismiss = { showWeatherSettingsPopup = false }
    )
}

/**
 * Section 1: Main Weather Card.
 * Displays: Current temperature, weather condition & icon, feels-like temperature,
 * current location / chainage, and one important weather alert inline if applicable.
 */
@Composable
private fun CurrentWeatherMainSection(
    observation: WeatherObservation?,
    weatherUiState: WeatherUiState,
    locationLabel: String,
    liveHighwayLocation: LiveHighwayLocationSnapshot,
    temperatureUnit: TemperatureUnit,
    atmosphereColor: Color,
    isDark: Boolean,
    shouldAnimate: Boolean,
    alerts: List<WeatherAlertItem>,
    isAlertsExpanded: Boolean,
    onToggleAlerts: () -> Unit
) {
    val c = CtecsTheme.colors

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            atmosphereColor.copy(alpha = if (isDark) 0.18f else 0.11f),
                            atmosphereColor.copy(alpha = if (isDark) 0.04f else 0.02f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.85f, size.height * 0.35f),
                        radius = size.width * 0.65f
                    )
                )
            }
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("weather_dashboard_hero_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Location & optional Live Chainage line
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = atmosphereColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = locationLabel,
                        color = c.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("weather_dashboard_location")
                    )
                }

                if (liveHighwayLocation.hasHighwayLine) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.testTag("weather_live_highway_location_banner")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Route,
                            contentDescription = null,
                            tint = if (liveHighwayLocation.isChainageStale) c.warning else c.textMuted,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = liveHighwayLocation.formattedHighwayLine,
                            color = c.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("weather_live_chainage_line")
                        )
                        if (liveHighwayLocation.isChainageStale) {
                            Text(
                                text = "STALE",
                                color = c.warning,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(c.warning.copy(alpha = 0.14f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                                    .testTag("weather_live_chainage_status_badge")
                            )
                        }
                    }
                }
            }

            if (observation != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = observation.formatTemperature(temperatureUnit),
                            color = c.textPrimary,
                            fontSize = 52.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-2.0).sp,
                            lineHeight = 56.sp,
                            modifier = Modifier.testTag("weather_dashboard_temp")
                        )
                        Text(
                            text = observation.conditionLabel,
                            color = c.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("weather_condition_label")
                        )
                        Text(
                            text = "Feels like ${observation.formatFeelsLike(temperatureUnit)}",
                            color = c.textSecondary,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            modifier = Modifier.testTag("weather_feels_like")
                        )
                    }

                    AnimatedWeatherIcon(
                        condition = observation.visualCondition,
                        isDark = isDark,
                        isAnimating = shouldAnimate,
                        isHeavyRain = observation.isHeavyRain,
                        size = 72.dp
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedWeatherIcon(
                        condition = WeatherConditionType.PARTLY_CLOUDY,
                        isDark = isDark,
                        isAnimating = shouldAnimate,
                        size = 48.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val headline = when (weatherUiState) {
                            WeatherUiState.WaitingForGps -> "Waiting for GPS"
                            is WeatherUiState.Loading -> "Updating weather…"
                            is WeatherUiState.Unavailable -> "Weather unavailable"
                            is WeatherUiState.Available -> "Loading weather…"
                        }
                        val subline = when (weatherUiState) {
                            WeatherUiState.WaitingForGps -> "Move to open sky or tap refresh once GPS locks."
                            is WeatherUiState.Unavailable -> weatherUiState.reason.ifBlank { "Check network and refresh." }
                            else -> "Fetching live forecast for your location."
                        }
                        Text(
                            text = headline,
                            color = c.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = subline,
                            color = c.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (alerts.isNotEmpty()) {
                InlineMainWeatherAlertStrip(
                    alerts = alerts,
                    isExpanded = isAlertsExpanded,
                    onToggle = onToggleAlerts
                )
            }
        }
    }
}

/**
 * Section B: Essential Weather Details in ONE compact layout (no oversized cards, no duplicate Temp/Feels Like).
 * Displays only metrics backed by reliable non-null data.
 */
@Composable
private fun EssentialWeatherDetailsGrid(
    observation: WeatherObservation,
    fallbackRainProbability: Int?,
    windSpeedUnit: com.example.model.WindSpeedUnit,
    timeFmt: SimpleDateFormat,
    isDark: Boolean
) {
    val c = CtecsTheme.colors
    val rainAccent = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    val metrics = remember(observation, fallbackRainProbability, windSpeedUnit, timeFmt, isDark) {
        buildList {
            val rainProb = observation.rainProbabilityPercent ?: fallbackRainProbability
            if (rainProb != null) {
                add(
                    EssentialWeatherMetric(
                        key = "rain",
                        label = "Rain",
                        value = "${rainProb.coerceIn(0, 100)}%",
                        accent = if (rainProb >= 40) rainAccent else null
                    )
                )
            }
            if (observation.windSpeedKmh.isFinite() && observation.windSpeedKmh >= 0.0) {
                add(
                    EssentialWeatherMetric(
                        key = "wind",
                        label = "Wind",
                        value = "${windSpeedUnit.format(observation.windSpeedKmh)} ${observation.windDirectionCompass}"
                    )
                )
            }
            if (observation.humidityPercent in 0..100) {
                add(
                    EssentialWeatherMetric(
                        key = "humidity",
                        label = "Humidity",
                        value = observation.formatHumidity()
                    )
                )
            }
            observation.uvIndex?.takeIf { it.isFinite() && it >= 0.0 }?.let { uv ->
                add(
                    EssentialWeatherMetric(
                        key = "uv",
                        label = "UV Index",
                        value = String.format(Locale.US, "%.1f", uv)
                    )
                )
            }
            observation.visibilityKm?.takeIf { it.isFinite() && it >= 0.0 }?.let { vis ->
                add(
                    EssentialWeatherMetric(
                        key = "visibility",
                        label = "Visibility",
                        value = String.format(Locale.US, "%.1f km", vis)
                    )
                )
            }
            val sunriseText = observation.sunriseMs?.let { timeFmt.format(Date(it)) }
            val sunsetText = observation.sunsetMs?.let { timeFmt.format(Date(it)) }
            val sunTiming = when {
                sunriseText != null && sunsetText != null -> "$sunriseText / $sunsetText"
                sunriseText != null -> sunriseText
                sunsetText != null -> sunsetText
                else -> null
            }
            if (sunTiming != null) {
                add(
                    EssentialWeatherMetric(
                        key = "sun",
                        label = "Sunrise / Sunset",
                        value = sunTiming
                    )
                )
            }
        }
    }

    if (metrics.isEmpty()) return

    val rows = remember(metrics) { metrics.chunked(2) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("weather_details_grid_card")
    ) {
        rows.forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowItems.forEach { metric ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("weather_detail_${metric.key}"),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = metric.label,
                            color = c.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = metric.value,
                            color = metric.accent ?: c.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
            if (rowIndex < rows.lastIndex) {
                SmartFadeDivider(
                    color = if (c.isDark) c.elevatedSurface else c.subtleSeparator
                )
            }
        }
    }
}

/**
 * Single inline important weather alert inside the Main Weather Card.
 * Rendered ONLY when meaningful active warnings exist; completely hidden when empty.
 */
@Composable
private fun InlineMainWeatherAlertStrip(
    alerts: List<WeatherAlertItem>,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    if (alerts.isEmpty()) return
    val c = CtecsTheme.colors
    val topAlert = alerts.first()
    val accentColor = when (topAlert.severity) {
        WeatherAlertSeverity.SEVERE -> c.error
        WeatherAlertSeverity.WARNING -> c.warning
        WeatherAlertSeverity.ADVISORY -> c.accent
    }
    val hasExpandableDetail = alerts.size > 1 || topAlert.description.isNotBlank()
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(180),
        label = "weather_alert_chevron"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accentColor.copy(alpha = if (c.isDark) 0.14f else 0.09f))
            .then(
                if (hasExpandableDetail) Modifier.clickable(onClick = onToggle) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("weather_severe_alerts_section"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = topAlert.severity.label,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = topAlert.title,
                    color = c.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (alerts.size > 1 && !isExpanded) {
                    Text(
                        text = "+${alerts.size - 1} more alert${if (alerts.size > 2) "s" else ""}",
                        color = accentColor,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            if (hasExpandableDetail) {
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (isExpanded) "Hide alert details" else "Show alert details",
                    tint = accentColor,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(chevronRotation)
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded && hasExpandableDetail,
            enter = expandVertically(tween(180)) + fadeIn(tween(150)),
            exit = shrinkVertically(tween(150)) + fadeOut(tween(120))
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("weather_alerts_expanded_body")
            ) {
                alerts.forEachIndexed { idx, alert ->
                    if (idx > 0) {
                        SmartFadeDivider(color = accentColor.copy(alpha = 0.22f))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (idx > 0) {
                            Text(
                                text = alert.title,
                                color = c.textPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (alert.description.isNotBlank()) {
                            Text(
                                text = alert.description,
                                color = c.textSecondary,
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Section C: Compact Hourly Forecast.
 * Horizontally scrollable strip showing Time, Weather Icon, Temperature,
 * and Rain Probability only when relevant (>= 15%).
 */
@Composable
private fun CompactHourlyForecastSection(
    hours: List<HourlyWeatherPoint>,
    temperatureUnit: TemperatureUnit,
    is24HourFormat: Boolean,
    isDark: Boolean,
    shouldAnimate: Boolean
) {
    val c = CtecsTheme.colors
    val rainTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .padding(vertical = 12.dp)
            .testTag("weather_dashboard_24h_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "HOURLY",
            color = c.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(hours, key = { _, pt -> pt.epochMs }) { idx, pt ->
                Column(
                    modifier = Modifier
                        .widthIn(min = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (idx == 0) c.secondarySurface else Color.Transparent
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formatHourlyTimeLabel(pt.localHourOfDay, is24HourFormat),
                        color = if (idx == 0) c.textPrimary else c.textMuted,
                        fontSize = 11.sp,
                        fontWeight = if (idx == 0) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                    AnimatedWeatherIcon(
                        condition = pt.visualCondition,
                        isDark = isDark,
                        isAnimating = shouldAnimate && idx == 0,
                        isHeavyRain = pt.isHeavyRain,
                        size = 24.dp
                    )
                    Text(
                        text = temperatureUnit.format(pt.temperatureC),
                        color = c.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    if (pt.rainProbabilityPercent >= 15) {
                        Text(
                            text = "${pt.rainProbabilityPercent}%",
                            color = rainTint,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    } else {
                        Spacer(Modifier.height(13.dp))
                    }
                }
            }
        }
    }
}

/**
 * Section D: Seven-Day Forecast.
 * One compact row per day with Day & Date, Weather Icon, relevant Rain Probability,
 * and Minimum / Maximum temperatures. Does not repeat current weather details.
 */
@Composable
private fun CompactSevenDayForecastSection(
    days: List<DailyWeatherPoint>,
    temperatureUnit: TemperatureUnit,
    isDark: Boolean
) {
    val c = CtecsTheme.colors
    val rainTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val dayDateFmt = remember { SimpleDateFormat("EEE, d MMM", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("weather_dashboard_7day_section"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "7-DAY FORECAST",
            color = c.textMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        days.forEachIndexed { index, day ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("weather_daily_row_${day.dayIndexFromToday}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = dayDateFmt.format(Date(day.dateEpochMs)),
                    color = c.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.25f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(0.95f)
                ) {
                    AnimatedWeatherIcon(
                        condition = day.condition,
                        isDark = isDark,
                        isAnimating = false,
                        size = 22.dp
                    )
                    if (day.rainProbabilityPercent >= 15) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WaterDrop,
                                contentDescription = null,
                                tint = rainTint,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${day.rainProbabilityPercent}%",
                                color = rainTint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = temperatureUnit.format(day.minTempC),
                        color = c.textMuted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    Text(
                        text = temperatureUnit.format(day.maxTempC),
                        color = c.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            if (index < days.lastIndex) {
                SmartFadeDivider(
                    color = if (c.isDark) c.elevatedSurface else c.subtleSeparator
                )
            }
        }
    }
}
