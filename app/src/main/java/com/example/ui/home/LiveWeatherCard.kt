package com.example.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.HourlyWeatherPoint
import com.example.model.TemperatureUnit
import com.example.model.WeatherConditionType
import com.example.model.WeatherObservation
import com.example.model.WeatherUiState
import com.example.ui.theme.CtecsTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Smart Home Screen Live Weather Card with collapsible full-day horizontal hourly forecast (12:00 AM–11:00 PM).
 *
 * Interaction contract:
 * - Default State: Collapsed (compact card showing essential current weather only).
 * - Single Tap: Smoothly expands or collapses the compact hourly forecast strip (no extra API calls).
 * - Double Tap: Opens the dedicated Weather Dashboard.
 * - Animations automatically pause when off-screen, lifecycle-paused, Screensaver/AOD active,
 *   or disabled in Weather settings.
 */
@Composable
fun HomeLiveWeatherCard(
    weatherUiState: WeatherUiState,
    resolvedLocationName: String,
    is24HourFormat: Boolean,
    isDark: Boolean,
    mainTextColor: Color,
    subTextColor: Color,
    isCompact: Boolean,
    isVisible: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    hourlyForecast: List<HourlyWeatherPoint> = emptyList(),
    currentLocalHour: Int = -1,
    temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    animationsEnabled: Boolean = true,
    showHourlyStrip: Boolean = true,
    showRainProbability: Boolean = true,
    isHourlyExpanded: Boolean = false,
    onToggleHourlyExpanded: () -> Unit = {},
    onOpenWeatherDashboard: () -> Unit = {}
) {
    val c = CtecsTheme.colors
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
    val shouldAnimate = isVisible && isLifecycleResumed && animationsEnabled

    val activeObservation: WeatherObservation? = when (weatherUiState) {
        is WeatherUiState.Available -> weatherUiState.observation
        is WeatherUiState.Loading -> weatherUiState.previous
        is WeatherUiState.Unavailable -> weatherUiState.cachedObservation
        WeatherUiState.WaitingForGps -> null
    }
    val isRefreshing = weatherUiState is WeatherUiState.Loading ||
        (weatherUiState is WeatherUiState.Available && weatherUiState.isRefreshing)

    val activeCondition = activeObservation?.visualCondition ?: WeatherConditionType.PARTLY_CLOUDY

    val targetGlowColor = weatherAtmosphereColor(activeCondition, isDark)
    val glowColor by animateColorAsState(
        targetValue = targetGlowColor,
        animationSpec = tween(durationMillis = 550),
        label = "weather_atmosphere_glow"
    )
    val targetWashBase = c.surface
    val washBase by animateColorAsState(
        targetValue = targetWashBase,
        animationSpec = tween(durationMillis = 280),
        label = "weather_wash_base"
    )

    val resolvedCurrentHour = remember(currentLocalHour) {
        if (currentLocalHour in 0..23) {
            currentLocalHour
        } else {
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0.0f to washBase.copy(alpha = if (isDark) 0.30f else 0.48f),
                        0.10f to washBase.copy(alpha = if (isDark) 0.94f else 0.96f),
                        0.90f to washBase.copy(alpha = if (isDark) 0.94f else 0.96f),
                        1.0f to washBase.copy(alpha = if (isDark) 0.30f else 0.48f)
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glowColor.copy(alpha = if (isDark) 0.18f else 0.13f),
                            glowColor.copy(alpha = if (isDark) 0.05f else 0.03f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.14f, size.height * 0.35f),
                        radius = size.width * 0.60f
                    )
                )
            }
            .pointerInput(activeObservation == null, showHourlyStrip, onRefresh, onToggleHourlyExpanded, onOpenWeatherDashboard) {
                detectTapGestures(
                    onTap = {
                        if (activeObservation == null) {
                            onRefresh()
                        } else if (showHourlyStrip) {
                            onToggleHourlyExpanded()
                        }
                    },
                    onDoubleTap = {
                        onOpenWeatherDashboard()
                    }
                )
            }
            .testTag("home_live_weather_card")
    ) {
        val isWide = maxWidth >= 420.dp
        val padH = if (isCompact) 10.dp else 13.dp
        val padV = if (isCompact) 6.dp else 9.dp

        AnimatedContent(
            targetState = activeObservation,
            contentKey = { obs -> obs != null },
            transitionSpec = { fadeIn(tween(280)) togetherWith fadeOut(tween(180)) },
            label = "weather_card_content_transition"
        ) { obs ->
            if (obs != null) {
                val displayLocation = resolvedLocationName
                    .ifBlank { obs.locationName }
                    .ifBlank { "Current Location" }
                val updatedTimeStr = remember(obs.observedAtMs, is24HourFormat) {
                    val fmt = if (is24HourFormat) "HH:mm" else "h:mm a"
                    SimpleDateFormat(fmt, Locale.US).format(Date(obs.observedAtMs))
                }
                val currentHourRainProb = obs.rainProbabilityPercent
                    ?: hourlyForecast.firstOrNull { it.localHourOfDay == resolvedCurrentHour }?.rainProbabilityPercent

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = padH, vertical = padV),
                    verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp)
                ) {
                    // Top Row: Animated Weather Icon + Temperature + Condition | Location + Updated Time + Refresh
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            AnimatedWeatherIcon(
                                condition = obs.visualCondition,
                                isDark = isDark,
                                isAnimating = shouldAnimate,
                                isHeavyRain = obs.isHeavyRain,
                                size = if (isCompact) 40.dp else 46.dp,
                                modifier = Modifier.testTag("weather_animated_icon")
                            )

                            Column(verticalArrangement = Arrangement.Center) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = obs.formatTemperature(temperatureUnit),
                                        color = mainTextColor,
                                        fontSize = if (isCompact) 21.sp else 25.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.5).sp,
                                        modifier = Modifier.testTag("weather_temp_text")
                                    )
                                    Text(
                                        text = obs.conditionLabel,
                                        color = mainTextColor.copy(alpha = 0.86f),
                                        fontSize = if (isCompact) 11.sp else 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .padding(bottom = if (isCompact) 2.dp else 3.dp)
                                            .testTag("weather_condition_text")
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.LocationOn,
                                        contentDescription = "Weather location",
                                        tint = glowColor,
                                        modifier = Modifier.size(11.dp)
                                    )
                                    Text(
                                        text = displayLocation,
                                        color = subTextColor,
                                        fontSize = if (isCompact) 9.5.sp else 10.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("weather_location_text")
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Updated $updatedTimeStr",
                                    color = subTextColor.copy(alpha = 0.85f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("weather_updated_text")
                                )
                                Text(
                                    text = if (showHourlyStrip) {
                                        if (isHourlyExpanded) "Tap to hide • 2x full" else "Tap hourly • 2x full"
                                    } else if (isWide) {
                                        "${obs.source} • 2x tap for full"
                                    } else {
                                        "2x tap for forecast"
                                    },
                                    color = subTextColor.copy(alpha = 0.62f),
                                    fontSize = 8.sp,
                                    maxLines = 1
                                )
                            }
                            if (showHourlyStrip) {
                                val chevronRotation by animateFloatAsState(
                                    targetValue = if (isHourlyExpanded) 180f else 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    label = "weather_hourly_chevron_rotation"
                                )
                                Icon(
                                    imageVector = Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = if (isHourlyExpanded) "Collapse hourly forecast" else "Expand hourly forecast",
                                    tint = if (isHourlyExpanded) glowColor else subTextColor.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .graphicsLayer { rotationZ = chevronRotation }
                                        .testTag("weather_hourly_expand_indicator")
                                )
                            }
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("weather_refresh_button")
                            ) {
                                if (isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = glowColor
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = "Refresh live weather",
                                        tint = subTextColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Middle Telemetry Row: Feels Like | Rain Prob (if available) | Humidity | Wind Speed & Direction
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WeatherMetricChip(
                            icon = Icons.Rounded.Thermostat,
                            label = "Feels ${obs.formatFeelsLike(temperatureUnit)}",
                            tint = glowColor,
                            textColor = mainTextColor.copy(alpha = 0.86f),
                            isDark = isDark,
                            isCompact = isCompact,
                            tag = "weather_feels_like_text"
                        )
                        if (showRainProbability && currentHourRainProb != null) {
                            WeatherMetricChip(
                                icon = Icons.Rounded.Umbrella,
                                label = "Rain $currentHourRainProb%",
                                tint = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                textColor = mainTextColor.copy(alpha = 0.86f),
                                isDark = isDark,
                                isCompact = isCompact,
                                tag = "weather_rain_prob_text"
                            )
                        }
                        WeatherMetricChip(
                            icon = Icons.Rounded.WaterDrop,
                            label = "Humidity ${obs.formatHumidity()}",
                            tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            textColor = mainTextColor.copy(alpha = 0.86f),
                            isDark = isDark,
                            isCompact = isCompact,
                            tag = "weather_humidity_text"
                        )
                        WeatherMetricChip(
                            icon = Icons.Rounded.Air,
                            label = "${obs.formatWindSpeed()} ${obs.windDirectionCompass}",
                            tint = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                            textColor = mainTextColor.copy(alpha = 0.86f),
                            isDark = isDark,
                            isCompact = isCompact,
                            tag = "weather_wind_text"
                        )
                    }

                    // Collapsible Full-Day Horizontal Hourly Forecast Strip (12:00 AM to 11:00 PM)
                    AnimatedVisibility(
                        visible = showHourlyStrip && isHourlyExpanded,
                        enter = expandVertically(
                            animationSpec = spring(
                                dampingRatio = 0.88f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            expandFrom = Alignment.Top
                        ) + fadeIn(animationSpec = tween(220)) + slideInVertically(
                            animationSpec = tween(220),
                            initialOffsetY = { -it / 5 }
                        ),
                        exit = shrinkVertically(
                            animationSpec = spring(
                                dampingRatio = 0.92f,
                                stiffness = Spring.StiffnessMedium
                            ),
                            shrinkTowards = Alignment.Top
                        ) + fadeOut(animationSpec = tween(160))
                    ) {
                        when {
                            hourlyForecast.isNotEmpty() -> {
                                HomeFullDayHourlyStrip(
                                    hourlyPoints = hourlyForecast,
                                    currentLocalHour = resolvedCurrentHour,
                                    temperatureUnit = temperatureUnit,
                                    isDark = isDark,
                                    mainTextColor = mainTextColor,
                                    subTextColor = subTextColor,
                                    accentColor = glowColor,
                                    isCompact = isCompact,
                                    isAnimating = shouldAnimate
                                )
                            }
                            isRefreshing -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .testTag("home_hourly_loading_row"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.6.dp,
                                        color = glowColor
                                    )
                                    Text(
                                        text = "Loading hourly forecast…",
                                        color = subTextColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            else -> {
                                Text(
                                    text = "Hourly forecast unavailable • Tap refresh to update",
                                    color = subTextColor.copy(alpha = 0.82f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .testTag("home_hourly_unavailable_text")
                                )
                            }
                        }
                    }
                }
            } else {
                val statusTitle = when (weatherUiState) {
                    WeatherUiState.WaitingForGps -> "Live Weather"
                    is WeatherUiState.Loading -> "Updating Live Weather…"
                    is WeatherUiState.Unavailable -> weatherUiState.reason
                    is WeatherUiState.Available -> "Live Weather"
                }
                val statusSubtitle = when (weatherUiState) {
                    WeatherUiState.WaitingForGps -> "Waiting for GPS location • Double-tap for Weather Dashboard"
                    is WeatherUiState.Loading -> resolvedLocationName.ifBlank { "Fetching current conditions…" }
                    is WeatherUiState.Unavailable -> "Tap refresh or double-tap for cached Weather Dashboard"
                    is WeatherUiState.Available -> ""
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = padH, vertical = if (isCompact) 6.dp else 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        AnimatedWeatherIcon(
                            condition = WeatherConditionType.PARTLY_CLOUDY,
                            isDark = isDark,
                            isAnimating = shouldAnimate,
                            size = if (isCompact) 34.dp else 40.dp
                        )
                        Column {
                            Text(
                                text = statusTitle,
                                color = mainTextColor,
                                fontSize = if (isCompact) 12.sp else 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag("weather_status_title")
                            )
                            Text(
                                text = statusSubtitle,
                                color = subTextColor,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("weather_refresh_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = glowColor
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh live weather",
                                tint = subTextColor,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFullDayHourlyStrip(
    hourlyPoints: List<HourlyWeatherPoint>,
    currentLocalHour: Int,
    temperatureUnit: TemperatureUnit,
    isDark: Boolean,
    mainTextColor: Color,
    subTextColor: Color,
    accentColor: Color,
    isCompact: Boolean,
    isAnimating: Boolean
) {
    val listState = rememberLazyListState()
    val currentIndex = remember(hourlyPoints, currentLocalHour) {
        hourlyPoints.indexOfFirst { it.localHourOfDay == currentLocalHour }.coerceAtLeast(0)
    }
    val showLeftFade by remember { derivedStateOf { listState.canScrollBackward } }
    val showRightFade by remember { derivedStateOf { listState.canScrollForward } }

    LaunchedEffect(currentIndex, hourlyPoints.size) {
        if (hourlyPoints.isNotEmpty()) {
            val targetIdx = (currentIndex - 1).coerceAtLeast(0)
            listState.scrollToItem(targetIdx)
        }
    }

    val rainColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val fadeWidthPx = 18.dp.toPx().coerceAtMost(size.width * 0.12f)
                if (fadeWidthPx > 0f) {
                    if (showLeftFade) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color.Black),
                                startX = 0f,
                                endX = fadeWidthPx
                            ),
                            topLeft = Offset.Zero,
                            size = Size(fadeWidthPx, size.height),
                            blendMode = BlendMode.DstIn
                        )
                    }
                    if (showRightFade) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startX = size.width - fadeWidthPx,
                                endX = size.width
                            ),
                            topLeft = Offset(size.width - fadeWidthPx, 0f),
                            size = Size(fadeWidthPx, size.height),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
            }
            .testTag("home_hourly_forecast_row")
    ) {
        items(
            items = hourlyPoints,
            key = { "${it.localHourOfDay}_${it.epochMs}" }
        ) { pt ->
            val isCurrentHour = pt.localHourOfDay == currentLocalHour
            val compactTemp = temperatureUnit.format(pt.temperatureC)
                .removeSuffix("C")
                .removeSuffix("F")

            Column(
                modifier = Modifier
                    .widthIn(min = if (isCompact) 36.dp else 40.dp)
                    .then(
                        if (isCurrentHour) {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor.copy(alpha = if (isDark) 0.14f else 0.10f))
                        } else {
                            Modifier
                        }
                    )
                    .padding(
                        horizontal = if (isCompact) 4.dp else 5.dp,
                        vertical = 2.dp
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${pt.formatHourAmPm()}, ${temperatureUnit.format(pt.temperatureC)}, rain ${pt.rainProbabilityPercent}%"
                    }
                    .testTag("home_hourly_item_${pt.localHourOfDay}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = if (isCurrentHour) "Now" else pt.formatCompactHourAmPm(),
                    color = if (isCurrentHour) accentColor else subTextColor.copy(alpha = 0.85f),
                    fontSize = if (isCompact) 8.sp else 8.5.sp,
                    lineHeight = if (isCompact) 9.sp else 10.sp,
                    fontWeight = if (isCurrentHour) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false
                )
                AnimatedWeatherIcon(
                    condition = pt.visualCondition,
                    isDark = isDark,
                    isAnimating = isAnimating && isCurrentHour,
                    isHeavyRain = pt.isHeavyRain,
                    size = if (isCompact) 14.dp else 16.dp
                )
                Text(
                    text = compactTemp,
                    color = mainTextColor,
                    fontSize = if (isCompact) 9.5.sp else 10.sp,
                    lineHeight = if (isCompact) 10.5.sp else 11.sp,
                    fontWeight = if (isCurrentHour) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "${pt.rainProbabilityPercent}%",
                    color = if (pt.rainProbabilityPercent >= 25) rainColor else subTextColor.copy(alpha = 0.62f),
                    fontSize = if (isCompact) 7.5.sp else 8.sp,
                    lineHeight = if (isCompact) 8.5.sp else 9.sp,
                    fontWeight = if (pt.rainProbabilityPercent >= 30) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun WeatherMetricChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    textColor: Color,
    isDark: Boolean,
    isCompact: Boolean,
    tag: String
) {
    val pillBg = if (isDark) Color.White.copy(alpha = 0.055f) else Color.Black.copy(alpha = 0.038f)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(pillBg)
            .padding(
                horizontal = if (isCompact) 6.dp else 8.dp,
                vertical = if (isCompact) 2.5.dp else 3.5.dp
            )
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(if (isCompact) 10.5.dp else 12.dp)
        )
        Text(
            text = label,
            color = textColor,
            fontSize = if (isCompact) 9.sp else 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

internal fun weatherAtmosphereColor(condition: WeatherConditionType, isDark: Boolean): Color {
    return when (condition) {
        WeatherConditionType.SUNNY -> if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B)
        WeatherConditionType.PARTLY_CLOUDY -> if (isDark) Color(0xFF60A5FA) else Color(0xFF3B82F6)
        WeatherConditionType.CLOUDY -> if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
        WeatherConditionType.RAINY -> if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
        WeatherConditionType.THUNDERSTORM -> if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
        WeatherConditionType.FOGGY -> if (isDark) Color(0xFFCBD5E1) else Color(0xFF64748B)
        WeatherConditionType.NIGHT -> if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
        WeatherConditionType.CLOUDY_NIGHT -> if (isDark) Color(0xFF818CF8) else Color(0xFF4338CA)
        WeatherConditionType.SNOWY -> if (isDark) Color(0xFF7DD3FC) else Color(0xFF0EA5E9)
        WeatherConditionType.WINDY -> if (isDark) Color(0xFF34D399) else Color(0xFF059669)
    }
}
