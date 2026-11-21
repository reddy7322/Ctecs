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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * Dedicated Weather Dashboard with live highway chainage integration, compact smart
 * expandable/collapsible sections, original animated weather graphics, IRC/MoRTH Highway
 * Work Suitability, and Work Target & Delay Prediction.
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
    nowMs: Long = System.currentTimeMillis(),
    liveHighwayLocation: LiveHighwayLocationSnapshot = LiveHighwayLocationSnapshot(),
    expandedSections: Set<String>? = null,
    onToggleSection: ((String) -> Unit)? = null
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
    var localExpandedSections by remember { mutableStateOf<Set<String>>(emptySet()) }
    val effectiveExpanded = expandedSections ?: localExpandedSections
    val toggleSection: (String) -> Unit = { key ->
        if (onToggleSection != null) {
            onToggleSection(key)
        } else {
            localExpandedSections = if (key in localExpandedSections) {
                localExpandedSections - key
            } else {
                localExpandedSections + key
            }
        }
    }

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
        .ifBlank { "Current Location" }

    val dateLabel = remember(nowMs) {
        SimpleDateFormat("EEE, d MMM yyyy", Locale.US).format(Date(nowMs))
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
            // Minimalist Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
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
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = displayLocation,
                                color = c.textPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("weather_dashboard_location")
                            )
                        }
                        Text(
                            text = dateLabel,
                            color = c.textMuted,
                            fontSize = 10.5.sp,
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

            // Compact Segmented Tab Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp)
                    .smartFadedBorderSurface(surfaceColor = c.surface, cornerRadius = 12.dp)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
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
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                if (selected) atmosphereColor.copy(alpha = if (isDark) 0.20f else 0.14f)
                                else Color.Transparent
                            )
                            .clickable { activeTab = tab }
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                            .testTag("weather_tab_${tab.name.lowercase()}"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = tabIcon,
                            contentDescription = null,
                            tint = if (selected) atmosphereColor else c.textMuted,
                            modifier = Modifier.size(13.dp)
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
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "weather_dashboard_tab_transition",
                modifier = Modifier.weight(1f)
            ) { tab ->
                when (tab) {
                    WeatherDashboardTab.FORECAST -> ForecastDashboardTabContent(
                        observation = activeObservation,
                        bundle = forecastBundle,
                        alerts = actionableAlerts.ifEmpty { forecastBundle?.alerts.orEmpty() },
                        liveHighwayLocation = liveHighwayLocation,
                        isStale = isStale,
                        isOffline = weatherUiState is WeatherUiState.Unavailable,
                        is24HourFormat = is24HourFormat,
                        settings = settings,
                        atmosphereColor = atmosphereColor,
                        isDark = isDark,
                        shouldAnimate = shouldAnimate,
                        expandedSections = effectiveExpanded,
                        onToggleSection = toggleSection,
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
                        nowMs = nowMs,
                        forecastBundle = forecastBundle,
                        workTargetPrediction = workTargetPrediction,
                        expandedSections = effectiveExpanded,
                        onToggleSection = toggleSection
                    )

                    WeatherDashboardTab.TARGETS -> WorkTargetAndRiskSection(
                        workTargetInput = workTargetInput,
                        workTargetPrediction = workTargetPrediction,
                        delayRiskSummary = delayRiskSummary,
                        onUpdateWorkTargetInput = onUpdateWorkTargetInput,
                        onSelectActivity = onSelectActivity,
                        expandedSections = effectiveExpanded,
                        onToggleSection = toggleSection
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
    liveHighwayLocation: LiveHighwayLocationSnapshot,
    isStale: Boolean,
    isOffline: Boolean,
    is24HourFormat: Boolean,
    settings: WeatherIntelligenceSettings,
    atmosphereColor: Color,
    isDark: Boolean,
    shouldAnimate: Boolean,
    expandedSections: Set<String>,
    onToggleSection: (String) -> Unit,
    nowMs: Long
) {
    val c = CtecsTheme.colors
    val unit = settings.temperatureUnit
    val next24 = remember(bundle, nowMs) { bundle?.next24Hours(nowMs).orEmpty() }
    val daily7 = remember(bundle) { bundle?.daily.orEmpty() }
    val timeFmt = remember(is24HourFormat) {
        SimpleDateFormat(if (is24HourFormat) "HH:mm" else "h:mm a", Locale.US)
    }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxSize()
            .testTag("weather_forecast_tab_list")
    ) {
        // 1. Compact Hero Overview + Provider Freshness Status
        item(key = "hero_overview") {
            val updatedFmt = remember(observation?.observedAtMs, is24HourFormat) {
                observation?.let {
                    val fmt = if (is24HourFormat) "dd MMM HH:mm" else "dd MMM h:mm a"
                    SimpleDateFormat(fmt, Locale.US).format(Date(it.observedAtMs))
                } ?: "Not loaded"
            }
            if (observation != null) {
                CompactHeroWeatherCard(
                    observation = observation,
                    temperatureUnit = unit,
                    updatedLabel = updatedFmt,
                    isStale = isStale,
                    isOffline = isOffline,
                    atmosphereColor = atmosphereColor,
                    isDark = isDark,
                    shouldAnimate = shouldAnimate
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .smartFadedBorderSurface(surfaceColor = c.surface, cornerRadius = 16.dp)
                        .padding(14.dp)
                        .testTag("weather_dashboard_hero_card"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AnimatedWeatherIcon(
                        condition = WeatherConditionType.PARTLY_CLOUDY,
                        isDark = isDark,
                        isAnimating = shouldAnimate,
                        size = 44.dp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Awaiting Live GPS Weather",
                            color = c.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Refresh once GPS & network are active.",
                            color = c.textMuted,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // 2. Weather Alerts (Important warnings stay visible even while collapsed)
        item(key = "section_weather_alerts") {
            val topSeverityColor = when (alerts.firstOrNull()?.severity) {
                WeatherAlertSeverity.SEVERE -> c.error
                WeatherAlertSeverity.WARNING -> c.warning
                WeatherAlertSeverity.ADVISORY -> c.accent
                null -> c.textMuted
            }
            WeatherSmartSection(
                sectionKey = WeatherSectionKeys.WEATHER_ALERTS,
                title = "Weather Alerts",
                summaryText = if (alerts.isEmpty()) "No active warnings" else "${alerts.size} active",
                badgeText = alerts.firstOrNull()?.severity?.label?.uppercase(Locale.US),
                badgeColor = topSeverityColor,
                icon = Icons.Rounded.Warning,
                iconTint = if (alerts.isEmpty()) c.textMuted else topSeverityColor,
                isExpanded = WeatherSectionKeys.WEATHER_ALERTS in expandedSections,
                onToggle = { onToggleSection(WeatherSectionKeys.WEATHER_ALERTS) },
                modifier = Modifier.testTag("weather_severe_alerts_section"),
                alwaysVisibleContent = if (alerts.isNotEmpty()) {
                    {
                        alerts.take(2).forEach { alert ->
                            val accent = when (alert.severity) {
                                WeatherAlertSeverity.SEVERE -> c.error
                                WeatherAlertSeverity.WARNING -> c.warning
                                WeatherAlertSeverity.ADVISORY -> c.accent
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(accent.copy(alpha = if (isDark) 0.16f else 0.10f))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Rounded.Warning, contentDescription = alert.severity.label, tint = accent, modifier = Modifier.size(13.dp))
                                Text(
                                    text = "${alert.severity.label.uppercase(Locale.US)}: ${alert.title}",
                                    color = c.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else null
            ) {
                if (alerts.isEmpty()) {
                    Text("No severe weather alerts in current 24h forecast.", color = c.textMuted, fontSize = 11.sp)
                } else {
                    alerts.forEach { alert ->
                        val accent = when (alert.severity) {
                            WeatherAlertSeverity.SEVERE -> c.error
                            WeatherAlertSeverity.WARNING -> c.warning
                            WeatherAlertSeverity.ADVISORY -> c.accent
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(accent.copy(alpha = if (isDark) 0.14f else 0.08f))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${alert.severity.label.uppercase(Locale.US)} • ${alert.title}",
                                color = c.textPrimary,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(alert.description, color = c.textSecondary, fontSize = 10.5.sp)
                        }
                    }
                }
            }
        }

        // 3. Hourly Forecast + LIVE HIGHWAY LOCATION (Chainage | LHS/RHS | Project | GPS)
        item(key = "section_hourly_forecast") {
            val peakRain = next24.maxOfOrNull { it.rainProbabilityPercent } ?: observation?.rainProbabilityPercent ?: 0
            val hourlySummary = if (next24.isNotEmpty()) {
                val nowPt = next24.first()
                "Now: ${unit.format(nowPt.temperatureC)} • Peak Rain: $peakRain% • 24h"
            } else {
                "24-hour forecast unavailable"
            }
            WeatherSmartSection(
                sectionKey = WeatherSectionKeys.HOURLY_FORECAST,
                title = "Hourly Forecast",
                summaryText = hourlySummary,
                icon = Icons.Rounded.Schedule,
                iconTint = atmosphereColor,
                isExpanded = WeatherSectionKeys.HOURLY_FORECAST in expandedSections,
                onToggle = { onToggleSection(WeatherSectionKeys.HOURLY_FORECAST) },
                modifier = Modifier.testTag("weather_dashboard_24h_section"),
                alwaysVisibleContent = {
                    LiveHighwayLocationBanner(
                        snapshot = liveHighwayLocation,
                        accentColor = atmosphereColor
                    )
                }
            ) {
                if (next24.isEmpty()) {
                    Text("Refresh live weather for hourly breakdown.", color = c.textMuted, fontSize = 11.sp)
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(next24, key = { it.epochMs }) { pt ->
                            Column(
                                modifier = Modifier
                                    .widthIn(min = 68.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(c.secondarySurface)
                                    .padding(horizontal = 7.dp, vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
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
                                    isAnimating = shouldAnimate && pt.epochMs == next24.first().epochMs,
                                    isHeavyRain = pt.isHeavyRain,
                                    size = 24.dp
                                )
                                Text(
                                    text = unit.format(pt.temperatureC),
                                    color = c.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${pt.rainProbabilityPercent}% • ${pt.formatRainfall()}",
                                    color = if (pt.rainProbabilityPercent >= 40) Color(0xFF38BDF8) else c.textMuted,
                                    fontSize = 8.5.sp
                                )
                                Text(
                                    text = "${pt.windSpeedKmh.roundToInt()} km/h",
                                    color = c.textMuted,
                                    fontSize = 8.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Daily Forecast (7-Day)
        if (daily7.isNotEmpty()) {
            item(key = "section_daily_forecast") {
                val today = daily7.first()
                val dailySummary = "Today: ${unit.format(today.maxTempC)} / ${unit.format(today.minTempC)} • ${daily7.size} Days"
                WeatherSmartSection(
                    sectionKey = WeatherSectionKeys.DAILY_FORECAST,
                    title = "Daily Forecast",
                    summaryText = dailySummary,
                    badgeText = if (settings.showSevenDayUncertaintyIndicators) "D4–7 ±" else null,
                    badgeColor = c.warning,
                    icon = Icons.Rounded.CalendarMonth,
                    iconTint = atmosphereColor,
                    isExpanded = WeatherSectionKeys.DAILY_FORECAST in expandedSections,
                    onToggle = { onToggleSection(WeatherSectionKeys.DAILY_FORECAST) },
                    modifier = Modifier.testTag("weather_dashboard_7day_section")
                ) {
                    val dayFmt = remember { SimpleDateFormat("EEE, d MMM", Locale.US) }
                    daily7.forEach { day ->
                        DailyForecastRow(
                            day = day,
                            dayLabel = if (day.dayIndexFromToday == 0) "Today" else dayFmt.format(Date(day.dateEpochMs)),
                            temperatureUnit = unit,
                            showUncertainty = settings.showSevenDayUncertaintyIndicators,
                            isDark = isDark
                        )
                    }
                }
            }
        }

        // 5. Temperature Trends
        if (next24.size >= 4) {
            item(key = "section_temperature_trends") {
                val trendPts = remember(next24) { next24.take(12) }
                val minT = trendPts.minOf { it.temperatureC }
                val maxT = trendPts.maxOf { it.temperatureC }
                WeatherSmartSection(
                    sectionKey = WeatherSectionKeys.TEMPERATURE_TRENDS,
                    title = "Temperature Trends",
                    summaryText = "Range: ${unit.format(minT)} – ${unit.format(maxT)} (Next 12h)",
                    icon = Icons.AutoMirrored.Rounded.ShowChart,
                    iconTint = atmosphereColor,
                    isExpanded = WeatherSectionKeys.TEMPERATURE_TRENDS in expandedSections,
                    onToggle = { onToggleSection(WeatherSectionKeys.TEMPERATURE_TRENDS) }
                ) {
                    TemperatureTrendChart(
                        points = trendPts,
                        temperatureUnit = unit,
                        accentColor = atmosphereColor,
                        textColor = c.textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .testTag("weather_temp_trend_chart")
                    )
                }
            }
        }

        // 6. Current Weather Details (Atmospheric details without duplicating Wind/Humidity/UV/Sun)
        if (observation != null) {
            item(key = "section_current_details") {
                val pressureStr = observation.pressureHpa?.let { "${it.roundToInt()} hPa" } ?: "—"
                val visStr = observation.visibilityKm?.let { String.format(Locale.US, "%.1f km", it) } ?: "—"
                WeatherSmartSection(
                    sectionKey = WeatherSectionKeys.CURRENT_DETAILS,
                    title = "Current Weather Details",
                    summaryText = "Feels: ${observation.formatFeelsLike(unit)} • $pressureStr • Vis $visStr",
                    icon = Icons.Rounded.Thermostat,
                    iconTint = atmosphereColor,
                    isExpanded = WeatherSectionKeys.CURRENT_DETAILS in expandedSections,
                    onToggle = { onToggleSection(WeatherSectionKeys.CURRENT_DETAILS) },
                    modifier = Modifier.testTag("weather_details_grid_card")
                ) {
                    CompactMetricRow("Temperature", observation.formatTemperature(unit))
                    CompactMetricRow("Feels Like", observation.formatFeelsLike(unit))
                    CompactMetricRow("Condition", observation.conditionLabel)
                    CompactMetricRow("Pressure", pressureStr)
                    CompactMetricRow("Visibility", visStr)
                    CompactMetricRow("Cloud Cover", observation.cloudCoverPercent?.let { "$it%" } ?: "—")
                }
            }

            // 7. Rainfall Forecast
            item(key = "section_rainfall_forecast") {
                val curRainProb = observation.rainProbabilityPercent ?: next24.firstOrNull()?.rainProbabilityPercent ?: 0
                val curRainMm = observation.precipitationMm ?: next24.firstOrNull()?.precipitationMm ?: 0.0
                val total24Mm = next24.sumOf { it.precipitationMm }
                val peakRainHour = next24.maxByOrNull { it.rainProbabilityPercent }
                val rainAccent = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

                WeatherSmartSection(
                    sectionKey = WeatherSectionKeys.RAINFALL_FORECAST,
                    title = "Rainfall Forecast",
                    summaryText = "Rain Probability: $curRainProb% • ${String.format(Locale.US, "%.1f mm", curRainMm)}",
                    badgeText = if (curRainProb >= 50 || total24Mm >= 2.0) "RAIN RISK" else null,
                    badgeColor = rainAccent,
                    icon = Icons.Rounded.Umbrella,
                    iconTint = rainAccent,
                    isExpanded = WeatherSectionKeys.RAINFALL_FORECAST in expandedSections,
                    onToggle = { onToggleSection(WeatherSectionKeys.RAINFALL_FORECAST) }
                ) {
                    CompactMetricRow("Current Rain Probability", "$curRainProb%", valueColor = if (curRainProb >= 40) rainAccent else c.textPrimary)
                    CompactMetricRow("Current Rainfall Rate", String.format(Locale.US, "%.1f mm/h", curRainMm))
                    CompactMetricRow("24h Total Accumulation", String.format(Locale.US, "%.1f mm", total24Mm))
                    peakRainHour?.let {
                        CompactMetricRow(
                            "Peak Rain Hour",
                            "${it.formatCompactHourAmPm()} (${it.rainProbabilityPercent}% • ${it.formatRainfall()})"
                        )
                    }
                }
            }

            // 8. Wind Information
            item(key = "section_wind_info") {
                val windAccent = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                val gustStr = observation.windGustsKmh?.let { " • Gust ${it.roundToInt()} km/h" } ?: ""
                val highWind = observation.windSpeedKmh > 40.0 || (observation.windGustsKmh ?: 0.0) > 40.0
                WeatherSmartSection(
                    sectionKey = WeatherSectionKeys.WIND_INFO,
                    title = "Wind Information",
                    summaryText = "Wind: ${observation.formatWindSpeed()} ${observation.windDirectionCompass}$gustStr",
                    badgeText = if (highWind) ">40 KM/H" else null,
                    badgeColor = c.warning,
                    icon = Icons.Rounded.Air,
                    iconTint = if (highWind) c.warning else windAccent,
                    isExpanded = WeatherSectionKeys.WIND_INFO in expandedSections,
                    onToggle = { onToggleSection(WeatherSectionKeys.WIND_INFO) }
                ) {
                    CompactMetricRow("Sustained Speed (10m)", observation.formatWindSpeed())
                    CompactMetricRow("Wind Direction", "${observation.windDirectionCompass} (${observation.windDirectionDegrees}°)")
                    CompactMetricRow("Wind Gusts", observation.windGustsKmh?.let { "${it.roundToInt()} km/h" } ?: "—")
                    CompactMetricRow(
                        "MoRTH Cl. 501.5.1 (2m Limit)",
                        if (highWind) "Exceeds 40 km/h — verify at 2m" else "Within 40 km/h limit",
                        valueColor = if (highWind) c.warning else c.success
                    )
                }
            }

            // 9. Humidity
            item(key = "section_humidity_info") {
                val dewStr = observation.dewPointC?.let { unit.format(it) } ?: "—"
                val humAccent = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
                WeatherSmartSection(
                    sectionKey = WeatherSectionKeys.HUMIDITY_INFO,
                    title = "Humidity",
                    summaryText = "Humidity: ${observation.formatHumidity()} • Dew Point: $dewStr",
                    icon = Icons.Rounded.WaterDrop,
                    iconTint = humAccent,
                    isExpanded = WeatherSectionKeys.HUMIDITY_INFO in expandedSections,
                    onToggle = { onToggleSection(WeatherSectionKeys.HUMIDITY_INFO) }
                ) {
                    CompactMetricRow("Relative Humidity", observation.formatHumidity())
                    CompactMetricRow("Dew Point", dewStr)
                    val spread = observation.dewPointC?.let { observation.temperatureC - it }
                    if (spread != null) {
                        CompactMetricRow(
                            "Temp–Dew Spread",
                            String.format(Locale.US, "%.1f°C (%s)", spread, if (spread <= 2.5) "Condensation Risk" else "Dry Margin"),
                            valueColor = if (spread <= 2.5) c.warning else c.success
                        )
                    }
                }
            }

            // 10. UV Index
            item(key = "section_uv_index") {
                val uv = observation.uvIndex
                val uvCat = when {
                    uv == null -> "Unavailable"
                    uv < 3.0 -> "Low"
                    uv < 6.0 -> "Moderate"
                    uv < 8.0 -> "High"
                    uv < 11.0 -> "Very High"
                    else -> "Extreme"
                }
                val uvColor = when {
                    uv == null -> c.textMuted
                    uv < 3.0 -> c.success
                    uv < 6.0 -> c.warning
                    else -> c.error
                }
                val uvVal = uv?.let { String.format(Locale.US, "%.1f", it) } ?: "—"
                WeatherSmartSection(
                    sectionKey = WeatherSectionKeys.UV_INDEX,
                    title = "UV Index",
                    summaryText = "UV: $uvVal • $uvCat",
                    icon = Icons.Rounded.WbSunny,
                    iconTint = uvColor,
                    isExpanded = WeatherSectionKeys.UV_INDEX in expandedSections,
                    onToggle = { onToggleSection(WeatherSectionKeys.UV_INDEX) }
                ) {
                    CompactMetricRow("Current UV Index", "$uvVal ($uvCat)", valueColor = uvColor)
                    val peakUv = next24.mapNotNull { it.uvIndex }.maxOrNull()
                    peakUv?.let {
                        CompactMetricRow("24h Peak UV", String.format(Locale.US, "%.1f", it))
                    }
                    CompactMetricRow(
                        "Field Crew Guidance",
                        when {
                            uv == null -> "No UV reading"
                            uv >= 8.0 -> "High sun exposure — shade & hydration"
                            uv >= 6.0 -> "Use sun protection during midday"
                            else -> "Normal outdoor exposure"
                        }
                    )
                }
            }

            // 11. Sunrise and Sunset
            item(key = "section_sunrise_sunset") {
                val sunriseStr = observation.sunriseMs?.let { timeFmt.format(Date(it)) } ?: "—"
                val sunsetStr = observation.sunsetMs?.let { timeFmt.format(Date(it)) } ?: "—"
                val daylightStr = if (observation.sunriseMs != null && observation.sunsetMs != null && observation.sunsetMs > observation.sunriseMs) {
                    val mins = ((observation.sunsetMs - observation.sunriseMs) / 60_000L).toInt()
                    "${mins / 60}h ${mins % 60}m"
                } else "—"

                WeatherSmartSection(
                    sectionKey = WeatherSectionKeys.SUNRISE_SUNSET,
                    title = "Sunrise & Sunset",
                    summaryText = "$sunriseStr / $sunsetStr • Daylight $daylightStr",
                    icon = Icons.Rounded.WbTwilight,
                    iconTint = if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
                    isExpanded = WeatherSectionKeys.SUNRISE_SUNSET in expandedSections,
                    onToggle = { onToggleSection(WeatherSectionKeys.SUNRISE_SUNSET) }
                ) {
                    CompactMetricRow("Sunrise", sunriseStr)
                    CompactMetricRow("Sunset", sunsetStr)
                    CompactMetricRow("Daylight Window", daylightStr)
                }
            }
        }

        item(key = "bottom_spacer") {
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun CompactHeroWeatherCard(
    observation: WeatherObservation,
    temperatureUnit: TemperatureUnit,
    updatedLabel: String,
    isStale: Boolean,
    isOffline: Boolean,
    atmosphereColor: Color,
    isDark: Boolean,
    shouldAnimate: Boolean
) {
    val c = CtecsTheme.colors
    val statusColor = if (isOffline || isStale) c.warning else c.success
    val statusText = when {
        isOffline -> "OFFLINE"
        isStale -> "STALE"
        else -> "LIVE"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .smartFadedBorderSurface(
                surfaceColor = c.surface,
                accentColor = atmosphereColor,
                cornerRadius = 18.dp
            )
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            atmosphereColor.copy(alpha = if (isDark) 0.22f else 0.14f),
                            atmosphereColor.copy(alpha = if (isDark) 0.05f else 0.03f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.82f, size.height * 0.30f),
                        radius = size.width * 0.65f
                    )
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("weather_dashboard_hero_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weather_provider_status_bar"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${observation.source} • $updatedLabel",
                    color = c.textMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = statusText,
                    color = statusColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(statusColor.copy(alpha = if (isDark) 0.18f else 0.12f))
                        .padding(horizontal = 6.dp, vertical = 1.5.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = observation.formatTemperature(temperatureUnit),
                            color = c.textPrimary,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.5).sp,
                            modifier = Modifier.testTag("weather_dashboard_temp")
                        )
                        Text(
                            text = observation.conditionLabel,
                            color = c.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    val hiLo = if (observation.todayMinTempC != null && observation.todayMaxTempC != null) {
                        "H ${temperatureUnit.format(observation.todayMaxTempC)} • L ${temperatureUnit.format(observation.todayMinTempC)}"
                    } else {
                        "Feels ${observation.formatFeelsLike(temperatureUnit)}"
                    }
                    Text(
                        text = "$hiLo • Rain ${observation.rainProbabilityPercent?.let { "$it%" } ?: "—"} • Wind ${observation.formatWindSpeed()}",
                        color = c.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                AnimatedWeatherIcon(
                    condition = observation.visualCondition,
                    isDark = isDark,
                    isAnimating = shouldAnimate,
                    isHeavyRain = observation.isHeavyRain,
                    size = 64.dp
                )
            }
        }
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
                text = "${points.first().formatCompactHourAmPm()} – ${points.last().formatCompactHourAmPm()}",
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
            val padH = 12f
            val padV = 12f
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
                    colors = listOf(accentColor.copy(alpha = 0.26f), Color.Transparent)
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
                style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            coords.forEachIndexed { idx, pt ->
                if (idx == 0 || idx == coords.lastIndex || idx % 3 == 0) {
                    drawCircle(color = accentColor, radius = 4.5f, center = pt)
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
            .clip(RoundedCornerShape(10.dp))
            .background(c.secondarySurface)
            .padding(horizontal = 9.dp, vertical = 7.dp)
            .testTag("weather_daily_row_${day.dayIndexFromToday}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1.25f)) {
            Text(
                text = dayLabel,
                color = c.textPrimary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                text = if (showUncertainty && day.dayIndexFromToday >= 3) {
                    "${day.conditionLabel} • ${day.forecastConfidence.label}"
                } else {
                    day.conditionLabel
                },
                color = if (showUncertainty && day.dayIndexFromToday >= 3) confColor else c.textMuted,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f)
        ) {
            AnimatedWeatherIcon(
                condition = day.condition,
                isDark = isDark,
                isAnimating = false,
                size = 22.dp
            )
            Text(
                text = "${day.rainProbabilityPercent}% • ${day.windSpeedMaxKmh.roundToInt()} km/h",
                color = if (day.rainProbabilityPercent >= 40) Color(0xFF38BDF8) else c.textMuted,
                fontSize = 9.5.sp,
                maxLines = 1
            )
        }

        Text(
            text = "${temperatureUnit.format(day.maxTempC)} / ${temperatureUnit.format(day.minTempC)}",
            color = c.textPrimary,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
