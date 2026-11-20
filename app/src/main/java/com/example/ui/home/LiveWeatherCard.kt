package com.example.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Thermostat
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.WeatherConditionType
import com.example.model.WeatherObservation
import com.example.model.WeatherUiState
import com.example.ui.theme.CtecsTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Premium minimalist borderless Home Screen Live Weather Card.
 *
 * Design highlights:
 * - Completely borderless atmospheric wash with smooth fading margins that blend naturally
 *   into AMOLED Deep Black Dark Mode (#000000) and Professional Light Mode (#FFFFFF).
 * - Smooth condition-based color and animated icon transitions inspired by Google Weather.
 * - Automatically pauses animations when off-screen, lifecycle-paused, or when Screensaver/AOD is active.
 * - Never fabricates weather data when GPS or network is unavailable.
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
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    // Pause animations automatically when activity moves to background
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
    val shouldAnimate = isVisible && isLifecycleResumed

    val activeObservation: WeatherObservation? = when (weatherUiState) {
        is WeatherUiState.Available -> weatherUiState.observation
        is WeatherUiState.Loading -> weatherUiState.previous
        is WeatherUiState.Unavailable -> weatherUiState.cachedObservation
        WeatherUiState.WaitingForGps -> null
    }
    val isRefreshing = weatherUiState is WeatherUiState.Loading ||
        (weatherUiState is WeatherUiState.Available && weatherUiState.isRefreshing)

    val activeCondition = activeObservation?.condition ?: WeatherConditionType.PARTLY_CLOUDY

    // Atmospheric tint adapts smoothly to current weather condition & Light/Dark theme
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .drawBehind {
                // Soft borderless gradient with smooth fading margins — zero hard borders
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
                        center = Offset(size.width * 0.14f, size.height * 0.42f),
                        radius = size.width * 0.58f
                    )
                )
            }
            .testTag("home_live_weather_card")
    ) {
        val isWide = maxWidth >= 420.dp
        val padH = if (isCompact) 10.dp else 13.dp
        val padV = if (isCompact) 7.dp else 10.dp

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
                                condition = obs.condition,
                                isDark = isDark,
                                isAnimating = shouldAnimate,
                                size = if (isCompact) 42.dp else 50.dp,
                                modifier = Modifier.testTag("weather_animated_icon")
                            )

                            Column(verticalArrangement = Arrangement.Center) {
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = obs.formatTemperature(),
                                        color = mainTextColor,
                                        fontSize = if (isCompact) 22.sp else 26.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = (-0.5).sp,
                                        modifier = Modifier.testTag("weather_temp_text")
                                    )
                                    Text(
                                        text = obs.conditionLabel,
                                        color = mainTextColor.copy(alpha = 0.86f),
                                        fontSize = if (isCompact) 11.5.sp else 13.sp,
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
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = displayLocation,
                                        color = subTextColor,
                                        fontSize = if (isCompact) 10.sp else 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.testTag("weather_location_text")
                                    )
                                }
                            }
                        }

                        // Right side: Updated time + accessible refresh button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Updated $updatedTimeStr",
                                    color = subTextColor.copy(alpha = 0.85f),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    modifier = Modifier.testTag("weather_updated_text")
                                )
                                if (isWide) {
                                    Text(
                                        text = obs.source,
                                        color = subTextColor.copy(alpha = 0.6f),
                                        fontSize = 8.5.sp,
                                        maxLines = 1
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

                    // Bottom Telemetry Row: Feels Like | Humidity | Wind Speed & Direction
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WeatherMetricChip(
                            icon = Icons.Rounded.Thermostat,
                            label = "Feels ${obs.formatFeelsLike()}",
                            tint = glowColor,
                            textColor = mainTextColor.copy(alpha = 0.85f),
                            isDark = isDark,
                            isCompact = isCompact,
                            tag = "weather_feels_like_text"
                        )
                        WeatherMetricChip(
                            icon = Icons.Rounded.WaterDrop,
                            label = "Humidity ${obs.formatHumidity()}",
                            tint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                            textColor = mainTextColor.copy(alpha = 0.85f),
                            isDark = isDark,
                            isCompact = isCompact,
                            tag = "weather_humidity_text"
                        )
                        WeatherMetricChip(
                            icon = Icons.Rounded.Air,
                            label = "${obs.formatWindSpeed()} ${obs.windDirectionCompass}",
                            tint = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                            textColor = mainTextColor.copy(alpha = 0.85f),
                            isDark = isDark,
                            isCompact = isCompact,
                            tag = "weather_wind_text"
                        )
                    }
                }
            } else {
                // Waiting for GPS / Loading / Offline state — never displays fabricated values
                val statusTitle = when (weatherUiState) {
                    WeatherUiState.WaitingForGps -> "Live Weather"
                    is WeatherUiState.Loading -> "Updating Live Weather…"
                    is WeatherUiState.Unavailable -> weatherUiState.reason
                    is WeatherUiState.Available -> "Live Weather"
                }
                val statusSubtitle = when (weatherUiState) {
                    WeatherUiState.WaitingForGps -> "Waiting for GPS location to load local conditions"
                    is WeatherUiState.Loading -> resolvedLocationName.ifBlank { "Fetching current conditions…" }
                    is WeatherUiState.Unavailable -> "Tap refresh when network connection is available"
                    is WeatherUiState.Available -> ""
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onRefresh)
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
                horizontal = if (isCompact) 7.dp else 9.dp,
                vertical = if (isCompact) 3.dp else 4.dp
            )
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(if (isCompact) 11.dp else 12.5.dp)
        )
        Text(
            text = label,
            color = textColor,
            fontSize = if (isCompact) 9.5.sp else 10.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun weatherAtmosphereColor(condition: WeatherConditionType, isDark: Boolean): Color {
    return when (condition) {
        WeatherConditionType.SUNNY -> if (isDark) Color(0xFFFBBF24) else Color(0xFFF59E0B)
        WeatherConditionType.PARTLY_CLOUDY -> if (isDark) Color(0xFF60A5FA) else Color(0xFF3B82F6)
        WeatherConditionType.CLOUDY -> if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
        WeatherConditionType.RAINY -> if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
        WeatherConditionType.THUNDERSTORM -> if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED)
        WeatherConditionType.FOGGY -> if (isDark) Color(0xFFCBD5E1) else Color(0xFF64748B)
        WeatherConditionType.NIGHT -> if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5)
        WeatherConditionType.SNOWY -> if (isDark) Color(0xFF7DD3FC) else Color(0xFF0EA5E9)
    }
}
