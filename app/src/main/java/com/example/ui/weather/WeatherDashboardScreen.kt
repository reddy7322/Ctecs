package com.example.ui.weather

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
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
import com.example.model.ForecastConfidence
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
import com.example.model.WorkTargetInput
import com.example.model.WorkTargetPrediction
import com.example.ui.home.AnimatedWeatherIcon
import com.example.ui.home.weatherAtmosphereColor
import com.example.ui.theme.CtecsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

enum class WeatherDashboardTab(val label: String) {
    FORECAST("Forecast"),
    SUITABILITY("Highway Work"),
    TARGETS("Targets & Risks")
}

/**
 * Dedicated Weather Dashboard inspired by polished modern weather experiences with original
 * graphics, integrated IRC/MoRTH Highway Work Suitability, and Work Target Prediction.
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
    workTargetInput: WorkTargetInput,
    workTargetPrediction: WorkTargetPrediction,
    delayRiskSummary: DelayRiskSummary,
    actionableAlerts: List<WeatherAlertItem>,
    onSelectActivity: (HighwayWorkActivity) -> Unit,
    onUpdateContractSpec: (ContractSpecificationConfig) -> Unit,
    onUpdateSiteMeasurements: (ActualSiteMeasurements) -> Unit,
    onUpdateWorkTargetInput: (WorkTargetInput) -> Unit,
    onRefreshWeather: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    initialTab: WeatherDashboardTab = WeatherDashboardTab.FORECAST,
    nowMs: Long = System.currentTimeMillis()
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

    var activeTab by remember { mutableStateOf(initialTab) }
    val shouldAnimate = isLifecycleResumed && settings.animationsEnabled

    val activeObservation: WeatherObservation? = forecastBundle?.current ?: when (weatherUiState) {
        is WeatherUiState.Available -> weatherUiState.observation
        is WeatherUiState.Loading -> weatherUiState.previous
        is WeatherUiState.Unavailable -> weatherUiState.cachedObservation
        WeatherUiState.WaitingForGps -> null
    }
    val isRefreshing = weatherUiState is WeatherUiState.Loading ||
        (weatherUiState is WeatherUiState.Available && weatherUiState.isRefreshing)
    val staleMs = settings.staleDataThresholdMinutes.toLong() * 60_000L
    val isStale = activeObservation != null && !activeObservation.isFreshAt(nowMs, staleMs)
    val atmosphereColor = weatherAtmosphereColor(
        activeObservation?.visualCondition ?: WeatherConditionType.PARTLY_CLOUDY,
        isDark
    )
    val displayLocation = resolvedLocationName
        .ifBlank { activeObservation?.locationName.orEmpty() }
        .ifBlank { "Current Highway Location" }

    val dateLabel = remember(nowMs) {
        SimpleDateFormat("EEEE, d MMM yyyy", Locale.US).format(Date(nowMs))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .testTag("weather_dashboard_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
                .align(Alignment.TopCenter)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
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
                    Column(modifier = Modifier.padding(start = 2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocationOn,
                                contentDescription = null,
                                tint = atmosphereColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = displayLocation,
                                color = c.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("weather_dashboard_location")
                            )
                        }
                        Text(
                            text = dateLabel,
                            color = c.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = onRefreshWeather,
                    modifier = Modifier
                        .size(48.dp)
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
                            contentDescription = "Refresh weather forecast",
                            tint = c.textPrimary
                        )
                    }
                }
            }

            // Segmented Dashboard Section Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.surface)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WeatherDashboardTab.entries.forEach { tab ->
                    val selected = activeTab == tab
                    val tabIcon = when (tab) {
                        WeatherDashboardTab.FORECAST -> Icons.Rounded.WbSunny
                        WeatherDashboardTab.SUITABILITY -> Icons.Rounded.Construction
                        WeatherDashboardTab.TARGETS -> Icons.Rounded.TrackChanges
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) atmosphereColor.copy(alpha = if (isDark) 0.22f else 0.16f)
                                else Color.Transparent
                            )
                            .clickable { activeTab = tab }
                            .padding(vertical = 9.dp, horizontal = 6.dp)
                            .testTag("weather_tab_${tab.name.lowercase()}"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tabIcon,
                            contentDescription = null,
                            tint = if (selected) atmosphereColor else c.textMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = tab.label,
                            color = if (selected) c.textPrimary else c.textMuted,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Main Scrollable Content
            AnimatedContent(
                targetState = activeTab,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
                label = "weather_dashboard_tab_transition",
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    WeatherDashboardTab.FORECAST -> ForecastDashboardTabContent(
                        observation = activeObservation,
                        bundle = forecastBundle,
                        alerts = actionableAlerts.ifEmpty { forecastBundle?.alerts.orEmpty() },
                        isStale = isStale,
                        isOffline = weatherUiState is WeatherUiState.Unavailable,
                        is24HourFormat = is24HourFormat,
                        settings = settings,
                        atmosphereColor = atmosphereColor,
                        isDark = isDark,
                        shouldAnimate = shouldAnimate,
                        nowMs = nowMs
                    )

                    WeatherDashboardTab.SUITABILITY -> HighwayWorkSuitabilitySection(
                        assessments = assessments,
                        selectedActivity = selectedActivity,
                        hourlyWorkTimeline = hourlyWorkTimeline,
                        contractSpec = settings.contractSpec,
                        siteMeasurements = siteMeasurements,
                        temperatureUnit = settings.temperatureUnit,
                        onSelectActivity = onSelectActivity,
                        onUpdateContractSpec = onUpdateContractSpec,
                        onUpdateSiteMeasurements = onUpdateSiteMeasurements,
                        nowMs = nowMs
                    )

                    WeatherDashboardTab.TARGETS -> WorkTargetAndRiskSection(
                        workTargetInput = workTargetInput,
                        workTargetPrediction = workTargetPrediction,
                        delayRiskSummary = delayRiskSummary,
                        onUpdateWorkTargetInput = onUpdateWorkTargetInput,
                        onSelectActivity = onSelectActivity
                    )
                }
            }
        }
    }
}

@Composable
private fun ForecastDashboardTabContent(
    observation: WeatherObservation?,
    bundle: WeatherForecastBundle?,
    alerts: List<WeatherAlertItem>,
    isStale: Boolean,
    isOffline: Boolean,
    is24HourFormat: Boolean,
    settings: WeatherIntelligenceSettings,
    atmosphereColor: Color,
    isDark: Boolean,
    shouldAnimate: Boolean,
    nowMs: Long
) {
    val c = CtecsTheme.colors
    val next24 = remember(bundle, nowMs) { bundle?.next24Hours(nowMs).orEmpty() }
    val daily7 = remember(bundle) { bundle?.daily.orEmpty() }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("weather_forecast_tab_list")
    ) {
        // Provider + Timestamp + Stale/Offline Banner
        item(key = "data_status_header") {
            val updatedFmt = remember(observation?.observedAtMs, is24HourFormat) {
                observation?.let {
                    val fmt = if (is24HourFormat) "dd MMM HH:mm" else "dd MMM h:mm a"
                    SimpleDateFormat(fmt, Locale.US).format(Date(it.observedAtMs))
                } ?: "Not loaded"
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isStale || isOffline) c.warning.copy(alpha = 0.14f) else c.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weather_provider_status_bar")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Provider: ${observation?.source ?: WeatherObservation.DEFAULT_SOURCE} • $updatedFmt",
                        color = c.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isOffline || isStale) {
                        Text(
                            text = if (isOffline) "OFFLINE CACHED" else "STALE DATA",
                            color = c.warning,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    } else {
                        Text(
                            text = "LIVE",
                            color = c.success,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // Hero Current Weather Overview
        item(key = "hero_overview") {
            if (observation != null) {
                HeroCurrentWeatherCard(
                    observation = observation,
                    temperatureUnit = settings.temperatureUnit,
                    is24HourFormat = is24HourFormat,
                    atmosphereColor = atmosphereColor,
                    isDark = isDark,
                    shouldAnimate = shouldAnimate
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = c.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AnimatedWeatherIcon(
                            condition = WeatherConditionType.PARTLY_CLOUDY,
                            isDark = isDark,
                            isAnimating = shouldAnimate,
                            size = 64.dp
                        )
                        Text(
                            text = "Waiting for Live GPS Weather",
                            color = c.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "CTECS never displays simulated weather. Tap refresh once GPS and internet are available.",
                            color = c.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Severe Weather Alerts
        if (alerts.isNotEmpty()) {
            item(key = "severe_alerts") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("weather_severe_alerts_section")
                ) {
                    Text(
                        text = "WEATHER ALERTS",
                        color = c.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                    alerts.forEach { alert ->
                        val alertAccent = when (alert.severity) {
                            WeatherAlertSeverity.SEVERE -> c.error
                            WeatherAlertSeverity.WARNING -> c.warning
                            WeatherAlertSeverity.ADVISORY -> c.accent
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = alertAccent.copy(alpha = if (isDark) 0.16f else 0.11f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = alert.severity.label,
                                    tint = alertAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${alert.severity.label.uppercase(Locale.US)} • ${alert.title}",
                                        color = c.textPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = alert.description,
                                        color = c.textSecondary,
                                        fontSize = 11.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Complete 24-Hour Forecast + Smart Temperature Trend Chart
        if (next24.isNotEmpty()) {
            item(key = "hourly_24_section") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = c.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weather_dashboard_24h_section")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "24-HOUR FORECAST",
                            color = c.textMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )

                        if (settings.showHourlyTrendChart && next24.size >= 4) {
                            TemperatureTrendChart(
                                points = next24.take(12),
                                temperatureUnit = settings.temperatureUnit,
                                accentColor = atmosphereColor,
                                textColor = c.textSecondary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(92.dp)
                                    .testTag("weather_temp_trend_chart")
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(next24, key = { it.epochMs }) { pt ->
                                Column(
                                    modifier = Modifier
                                        .widthIn(min = 72.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(c.secondarySurface)
                                        .padding(horizontal = 8.dp, vertical = 9.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = pt.formatCompactHourAmPm(),
                                        color = c.textSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    AnimatedWeatherIcon(
                                        condition = pt.visualCondition,
                                        isDark = isDark,
                                        isAnimating = false,
                                        isHeavyRain = pt.isHeavyRain,
                                        size = 28.dp
                                    )
                                    Text(
                                        text = settings.temperatureUnit.format(pt.temperatureC),
                                        color = c.textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${pt.rainProbabilityPercent}% • ${pt.formatRainfall()}",
                                        color = if (pt.rainProbabilityPercent >= 40) Color(0xFF38BDF8) else c.textMuted,
                                        fontSize = 9.sp
                                    )
                                    Text(
                                        text = "${pt.windSpeedKmh.roundToInt()} km/h • ${pt.humidityPercent}%",
                                        color = c.textMuted,
                                        fontSize = 8.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7-Day Forecast with Explicit Uncertainty Indicators
        if (daily7.isNotEmpty()) {
            item(key = "daily_7_section") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = c.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("weather_dashboard_7day_section")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "7-DAY FORECAST",
                                color = c.textMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                            if (settings.showSevenDayUncertaintyIndicators) {
                                Text(
                                    text = "Days 4–7: Higher Uncertainty",
                                    color = c.warning,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        val dayFmt = remember { SimpleDateFormat("EEE, d MMM", Locale.US) }
                        daily7.forEach { day ->
                            DailyForecastRow(
                                day = day,
                                dayLabel = if (day.dayIndexFromToday == 0) "Today" else dayFmt.format(Date(day.dateEpochMs)),
                                temperatureUnit = settings.temperatureUnit,
                                showUncertainty = settings.showSevenDayUncertaintyIndicators,
                                isDark = isDark
                            )
                        }
                    }
                }
            }
        }

        // Weather Details Grid
        if (observation != null) {
            item(key = "weather_details_grid") {
                WeatherDetailsGridCard(
                    observation = observation,
                    temperatureUnit = settings.temperatureUnit,
                    is24HourFormat = is24HourFormat,
                    accentColor = atmosphereColor
                )
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HeroCurrentWeatherCard(
    observation: WeatherObservation,
    temperatureUnit: TemperatureUnit,
    is24HourFormat: Boolean,
    atmosphereColor: Color,
    isDark: Boolean,
    shouldAnimate: Boolean
) {
    val c = CtecsTheme.colors
    val timeFmt = remember(is24HourFormat) { SimpleDateFormat(if (is24HourFormat) "HH:mm" else "h:mm a", Locale.US) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .drawBehind {
                drawRect(color = c.surface)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            atmosphereColor.copy(alpha = if (isDark) 0.28f else 0.20f),
                            atmosphereColor.copy(alpha = if (isDark) 0.08f else 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.78f, size.height * 0.25f),
                        radius = size.width * 0.75f
                    )
                )
            }
            .padding(18.dp)
            .testTag("weather_dashboard_hero_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = observation.formatTemperature(temperatureUnit),
                        color = c.textPrimary,
                        fontSize = 54.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-2).sp,
                        modifier = Modifier.testTag("weather_dashboard_temp")
                    )
                    Text(
                        text = observation.conditionLabel,
                        color = c.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    val hiLo = if (observation.todayMinTempC != null && observation.todayMaxTempC != null) {
                        "High ${temperatureUnit.format(observation.todayMaxTempC)} • Low ${temperatureUnit.format(observation.todayMinTempC)}"
                    } else {
                        "Feels like ${observation.formatFeelsLike(temperatureUnit)}"
                    }
                    Text(
                        text = "$hiLo • Feels ${observation.formatFeelsLike(temperatureUnit)}",
                        color = c.textSecondary,
                        fontSize = 12.sp
                    )
                }

                AnimatedWeatherIcon(
                    condition = observation.visualCondition,
                    isDark = isDark,
                    isAnimating = shouldAnimate,
                    isHeavyRain = observation.isHeavyRain,
                    size = 92.dp
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HeroMiniStat(
                    label = "Rain Prob",
                    value = observation.rainProbabilityPercent?.let { "$it%" } ?: "—"
                )
                HeroMiniStat(
                    label = "Humidity",
                    value = observation.formatHumidity()
                )
                HeroMiniStat(
                    label = "Wind",
                    value = "${observation.formatWindSpeed()} ${observation.windDirectionCompass}"
                )
                val sunText = if (observation.sunriseMs != null && observation.sunsetMs != null) {
                    "${timeFmt.format(Date(observation.sunriseMs))} / ${timeFmt.format(Date(observation.sunsetMs))}"
                } else {
                    "—"
                }
                HeroMiniStat(
                    label = "Sun",
                    value = sunText
                )
            }
        }
    }
}

@Composable
private fun HeroMiniStat(label: String, value: String) {
    val c = CtecsTheme.colors
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, color = c.textMuted, fontSize = 10.sp)
        Text(value, color = c.textPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun TemperatureTrendChart(
    points: List<HourlyWeatherPoint>,
    temperatureUnit: TemperatureUnit,
    accentColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val temps = points.map { it.temperatureC }
    val minT = temps.minOrNull() ?: 0.0
    val maxT = temps.maxOrNull() ?: 1.0
    val range = (maxT - minT).coerceAtLeast(1.0)

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Temp Trend (${points.first().formatCompactHourAmPm()}–${points.last().formatCompactHourAmPm()})",
                color = textColor,
                fontSize = 10.sp
            )
            Text(
                text = "${temperatureUnit.format(minT)} – ${temperatureUnit.format(maxT)}",
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(4.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (points.size < 2) return@Canvas
            val padH = 14f
            val padV = 14f
            val w = (size.width - padH * 2f).coerceAtLeast(1f)
            val h = (size.height - padV * 2f).coerceAtLeast(1f)
            val stepX = w / (points.size - 1)

            val coords = points.mapIndexed { idx, pt ->
                val x = padH + idx * stepX
                val norm = ((pt.temperatureC - minT) / range).toFloat()
                val y = padV + h * (1f - norm)
                Offset(x, y)
            }

            val fillPath = Path().apply {
                moveTo(coords.first().x, size.height - padV)
                coords.forEach { lineTo(it.x, it.y) }
                lineTo(coords.last().x, size.height - padV)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(accentColor.copy(alpha = 0.28f), Color.Transparent)
                )
            )

            val strokePath = Path().apply {
                moveTo(coords.first().x, coords.first().y)
                for (i in 1 until coords.size) {
                    val prev = coords[i - 1]
                    val curr = coords[i]
                    val midX = (prev.x + curr.x) * 0.5f
                    cubicTo(midX, prev.y, midX, curr.y, curr.x, curr.y)
                }
            }
            drawPath(
                path = strokePath,
                color = accentColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            coords.forEachIndexed { idx, pt ->
                if (idx == 0 || idx == coords.lastIndex || idx % 3 == 0) {
                    drawCircle(color = accentColor, radius = 5.5f, center = pt)
                }
            }
        }
    }
}

@Composable
private fun DailyForecastRow(
    day: DailyWeatherPoint,
    dayLabel: String,
    temperatureUnit: TemperatureUnit,
    showUncertainty: Boolean,
    isDark: Boolean
) {
    val c = CtecsTheme.colors
    val confColor = when (day.forecastConfidence) {
        ForecastConfidence.HIGH -> c.success
        ForecastConfidence.MODERATE -> c.accent
        ForecastConfidence.LOW -> c.warning
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.secondarySurface)
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag("weather_daily_row_${day.dayIndexFromToday}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1.3f)) {
            Text(
                text = dayLabel,
                color = c.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = day.conditionLabel,
                color = c.textMuted,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showUncertainty && day.dayIndexFromToday >= 3) {
                Text(
                    text = day.forecastConfidence.label,
                    color = confColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            AnimatedWeatherIcon(
                condition = day.condition,
                isDark = isDark,
                isAnimating = false,
                size = 26.dp
            )
            Column {
                Text(
                    text = "${day.rainProbabilityPercent}% • ${String.format(Locale.US, "%.1f", day.precipitationSumMm)}mm",
                    color = if (day.rainProbabilityPercent >= 40) Color(0xFF38BDF8) else c.textMuted,
                    fontSize = 9.5.sp
                )
                Text(
                    text = "${day.windSpeedMaxKmh.roundToInt()} km/h ${day.windDirectionCompass}",
                    color = c.textMuted,
                    fontSize = 9.sp
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${temperatureUnit.format(day.maxTempC)} / ${temperatureUnit.format(day.minTempC)}",
                color = c.textPrimary,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
            if (day.windGustsMaxKmh > 40.0) {
                Text(
                    text = "Gusts ${day.windGustsMaxKmh.roundToInt()} km/h",
                    color = c.warning,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun WeatherDetailsGridCard(
    observation: WeatherObservation,
    temperatureUnit: TemperatureUnit,
    is24HourFormat: Boolean,
    accentColor: Color
) {
    val c = CtecsTheme.colors
    val timeFmt = remember(is24HourFormat) { SimpleDateFormat(if (is24HourFormat) "HH:mm" else "h:mm a", Locale.US) }

    val items = listOf(
        Triple(Icons.Rounded.Thermostat, "Feels Like", observation.formatFeelsLike(temperatureUnit)),
        Triple(Icons.Rounded.WaterDrop, "Humidity", observation.formatHumidity()),
        Triple(
            Icons.Rounded.Air,
            "Wind & Gusts",
            "${observation.formatWindSpeed()} ${observation.windDirectionCompass}" +
                (observation.windGustsKmh?.let { " (G ${it.roundToInt()})" } ?: "")
        ),
        Triple(
            Icons.Rounded.Umbrella,
            "Rain & Accum.",
            "${observation.rainProbabilityPercent?.let { "$it%" } ?: "—"} • ${observation.precipitationMm?.let { String.format(Locale.US, "%.1f mm", it) } ?: "0 mm"}"
        ),
        Triple(Icons.Rounded.Cloud, "Cloud Cover", observation.cloudCoverPercent?.let { "$it%" } ?: "—"),
        Triple(Icons.Rounded.Compress, "Pressure", observation.pressureHpa?.let { "${it.roundToInt()} hPa" } ?: "—"),
        Triple(Icons.Rounded.Visibility, "Visibility", observation.visibilityKm?.let { String.format(Locale.US, "%.1f km", it) } ?: "—"),
        Triple(Icons.Rounded.WbSunny, "UV Index", observation.uvIndex?.let { String.format(Locale.US, "%.1f", it) } ?: "—"),
        Triple(Icons.Rounded.Speed, "Dew Point", observation.dewPointC?.let { temperatureUnit.format(it) } ?: "—"),
        Triple(
            Icons.Rounded.WbTwilight,
            "Sunrise / Sunset",
            if (observation.sunriseMs != null && observation.sunsetMs != null) {
                "${timeFmt.format(Date(observation.sunriseMs))} / ${timeFmt.format(Date(observation.sunsetMs))}"
            } else "—"
        )
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = c.surface,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("weather_details_grid_card")
    ) {
        BoxWithConstraints(modifier = Modifier.padding(14.dp)) {
            val cols = if (maxWidth >= 520.dp) 3 else 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "WEATHER DETAILS",
                    color = c.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                items.chunked(cols).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (icon, title, value) ->
                            DetailGridCell(
                                icon = icon,
                                title = title,
                                value = value,
                                accentColor = accentColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(cols - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailGridCell(
    icon: ImageVector,
    title: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(c.secondarySurface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
            Text(title, color = c.textMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            text = value,
            color = c.textPrimary,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
