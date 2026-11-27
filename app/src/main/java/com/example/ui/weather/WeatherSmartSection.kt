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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GpsFixed
import androidx.compose.material.icons.rounded.GpsOff
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.location.LocationState
import com.example.model.ChainageMatchResult
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import com.example.ui.theme.smartFadeSurface
import java.util.Locale

/**
 * Canonical section keys for independent smart expand/collapse persistence on the Weather Page.
 */
object WeatherSectionKeys {
    const val CURRENT_DETAILS = "current_details"
    const val HOURLY_FORECAST = "hourly_forecast"
    const val DAILY_FORECAST = "daily_forecast"
    const val TEMPERATURE_TRENDS = "temperature_trends"
    const val RAINFALL_FORECAST = "rainfall_forecast"
    const val WIND_INFO = "wind_info"
    const val HUMIDITY_INFO = "humidity_info"
    const val UV_INDEX = "uv_index"
    const val SUNRISE_SUNSET = "sunrise_sunset"
    const val WEATHER_ALERTS = "weather_alerts"
    const val BC_SUITABILITY = "bc_suitability"
    const val DBM_SUITABILITY = "dbm_suitability"
    const val OTHER_HIGHWAY_WORK = "other_highway_work"
    const val WEATHER_DELAY_ESTIMATION = "weather_delay_estimation"
    const val IRC_MORTH_REFERENCES = "irc_morth_references"
}

/**
 * Read-only presentation snapshot derived directly from CTECS's existing GPS and KMZ/KML
 * chainage engine (`liveMatchResult`, `locationState`, `projectProfile`, `activeRouteData`).
 */
@Immutable
data class LiveHighwayLocationSnapshot(
    val roadOrProjectName: String = "",
    val currentChainageText: String = "",
    val sideOfRoad: String = "",
    val gpsLocationName: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isGpsLive: Boolean = false,
    val isChainageStale: Boolean = false,
    val staleReason: String = ""
) {
    val hasHighwayLine: Boolean
        get() = roadOrProjectName.isNotBlank() || currentChainageText.isNotBlank() || sideOfRoad.isNotBlank()

    /**
     * Formatted live highway string, e.g. "NH-58 | 196+450 | LHS".
     */
    val formattedHighwayLine: String
        get() {
            val parts = buildList {
                if (roadOrProjectName.isNotBlank()) add(roadOrProjectName)
                if (currentChainageText.isNotBlank()) add(currentChainageText)
                if (sideOfRoad.isNotBlank()) add(sideOfRoad)
            }
            return if (parts.isEmpty()) "Awaiting Route / GPS" else parts.joinToString(" | ")
        }

    val formattedCoordinates: String?
        get() = if (latitude != null && longitude != null) {
            String.format(Locale.US, "%.4f, %.4f", latitude, longitude)
        } else {
            null
        }
}

/**
 * Builds a [LiveHighwayLocationSnapshot] purely from existing CTECS GPS & KMZ/KML chainage state.
 * Falls back to [lastKnownValid] when GPS temporarily drops, marking the display as stale.
 */
fun buildLiveHighwayLocationSnapshot(
    matchResult: ChainageMatchResult,
    locationState: LocationState,
    projectProfile: ProjectProfile,
    activeRoute: RouteData?,
    resolvedLocationName: String,
    lastKnownValid: LiveHighwayLocationSnapshot? = null,
    nowMs: Long = System.currentTimeMillis()
): LiveHighwayLocationSnapshot {
    fun String?.cleanTag(): String {
        val t = this?.trim().orEmpty()
        return if (
            t.isEmpty() ||
            t.equals("None", ignoreCase = true) ||
            t.equals("Unknown", ignoreCase = true) ||
            t.equals("N/A", ignoreCase = true) ||
            t == "---"
        ) "" else t
    }

    val roadName = projectProfile.highwayId.cleanTag()
        .ifBlank { activeRoute?.name.cleanTag() }
        .ifBlank { matchResult.matchedAlignmentName.cleanTag() }
        .ifBlank { lastKnownValid?.roadOrProjectName.orEmpty() }

    val directChainage = matchResult.customChainageText.cleanTag()
        .ifBlank { matchResult.chainage?.format().cleanTag() }

    val fallbackChainage = matchResult.lastReliableChainage?.format().cleanTag()
        .ifBlank { lastKnownValid?.currentChainageText.orEmpty() }

    val effectiveChainage = directChainage.ifBlank { fallbackChainage }

    val rawSide = matchResult.smartDirectionState?.confirmedSide.cleanTag()
        .ifBlank { matchResult.detectedSide.cleanTag() }
        .ifBlank {
            projectProfile.roadSide.takeIf { it != RoadSide.NONE }?.label.cleanTag()
        }
        .ifBlank { lastKnownValid?.sideOfRoad.orEmpty() }

    val gpsFixStale = locationState.lastFixTime > 0L && (nowMs - locationState.lastFixTime) > 30_000L
    val gpsUsable = locationState.isUsable && !gpsFixStale

    val isStale = when {
        matchResult.isManual -> false
        directChainage.isBlank() && fallbackChainage.isNotBlank() -> true
        !gpsUsable && effectiveChainage.isNotBlank() -> true
        matchResult.isUncertain && effectiveChainage.isNotBlank() -> true
        else -> false
    }

    val staleReason = when {
        !gpsUsable && effectiveChainage.isNotBlank() -> "STALE • LAST VALID"
        matchResult.isUncertain -> "STALE • LOW GPS"
        directChainage.isBlank() && fallbackChainage.isNotBlank() -> "STALE • LAST VALID"
        else -> ""
    }

    val gpsName = resolvedLocationName.trim()
        .ifBlank { lastKnownValid?.gpsLocationName.orEmpty() }

    return LiveHighwayLocationSnapshot(
        roadOrProjectName = roadName,
        currentChainageText = effectiveChainage,
        sideOfRoad = rawSide,
        gpsLocationName = gpsName,
        latitude = locationState.point?.latitude ?: lastKnownValid?.latitude,
        longitude = locationState.point?.longitude ?: lastKnownValid?.longitude,
        isGpsLive = gpsUsable,
        isChainageStale = isStale,
        staleReason = staleReason
    )
}

/**
 * Compact, clearly readable Live Highway Location header displayed inside the Weather Forecast
 * hourly section. Synchronizes live with CTECS GPS and KMZ/KML alignment matching.
 */
@Composable
fun LiveHighwayLocationBanner(
    snapshot: LiveHighwayLocationSnapshot,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val statusColor = when {
        snapshot.isChainageStale -> c.warning
        snapshot.isGpsLive -> c.success
        else -> c.textMuted
    }
    val statusLabel = when {
        snapshot.isChainageStale -> snapshot.staleReason.ifBlank { "STALE" }
        snapshot.isGpsLive -> "LIVE"
        else -> "GPS WAIT"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .smartFadedBorderSurface(
                surfaceColor = c.secondarySurface,
                accentColor = if (snapshot.isChainageStale) c.warning else accentColor,
                cornerRadius = 12.dp
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("weather_live_highway_location_banner"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Route,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "LIVE HIGHWAY LOCATION",
                    color = c.textMuted,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.7.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusColor.copy(alpha = if (c.isDark) 0.18f else 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("weather_live_chainage_status_badge")
            ) {
                Icon(
                    imageVector = if (snapshot.isGpsLive && !snapshot.isChainageStale) {
                        Icons.Rounded.GpsFixed
                    } else {
                        Icons.Rounded.GpsOff
                    },
                    contentDescription = statusLabel,
                    tint = statusColor,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }

        Text(
            text = snapshot.formattedHighwayLine,
            color = c.textPrimary,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("weather_live_chainage_line")
        )

        val gpsDetail = buildList {
            if (snapshot.gpsLocationName.isNotBlank()) add(snapshot.gpsLocationName)
            snapshot.formattedCoordinates?.let { add(it) }
        }.joinToString(" • ")

        if (gpsDetail.isNotBlank()) {
            Text(
                text = gpsDetail,
                color = c.textSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("weather_live_gps_location_text")
            )
        }
    }
}

/**
 * Reusable premium minimalist smart expandable/collapsible section for the Weather Page.
 *
 * - Displays compact title, small icon, essential inline summary, and subtle chevron by default.
 * - Supports an optional [alwaysVisibleContent] slot so critical warnings or live chainage
 *   remain visible even while the section is collapsed.
 * - Uses smart faded tonal borders without bulky boxes or popup windows.
 */
@Composable
fun WeatherSmartSection(
    sectionKey: String,
    title: String,
    summaryText: String,
    icon: ImageVector,
    iconTint: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    badgeColor: Color = iconTint,
    alwaysVisibleContent: (@Composable ColumnScope.() -> Unit)? = null,
    expandedContent: @Composable ColumnScope.() -> Unit
) {
    val c = CtecsTheme.colors
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "weather_section_chevron_$sectionKey"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .smartFadedBorderSurface(
                surfaceColor = c.surface,
                accentColor = if (isExpanded) iconTint else Color.Transparent,
                cornerRadius = 14.dp
            )
            .testTag("weather_section_$sectionKey")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 9.dp)
                .semantics {
                    contentDescription = "$title, $summaryText. ${if (isExpanded) "Expanded" else "Collapsed"}"
                }
                .testTag("weather_section_header_$sectionKey"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = if (c.isDark) 0.16f else 0.11f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        color = c.textPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!badgeText.isNullOrBlank()) {
                        Text(
                            text = badgeText,
                            color = badgeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = if (c.isDark) 0.18f else 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                .testTag("weather_section_badge_$sectionKey")
                        )
                    }
                }
                if (summaryText.isNotBlank()) {
                    Text(
                        text = summaryText,
                        color = c.textSecondary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = if (isExpanded) "Collapse $title" else "Expand $title",
                tint = c.textMuted,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(chevronRotation)
            )
        }

        if (alwaysVisibleContent != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = if (isExpanded) 6.dp else 9.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                content = alwaysVisibleContent
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(190)) + fadeIn(animationSpec = tween(160)),
            exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(animationSpec = tween(120))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weather_section_body_$sectionKey")
            ) {
                SmartFadeDivider(
                    color = if (c.isDark) c.elevatedSurface else c.subtleSeparator,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    content = expandedContent
                )
            }
        }
    }
}

/**
 * Compact label-value telemetry line used inside expanded Weather Page sections.
 */
@Composable
fun CompactMetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = CtecsTheme.colors.textPrimary
) {
    val c = CtecsTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = c.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Subtle smart faded outline border paired with tonal surface wash for minimalist Weather Page sections.
 */
@Composable
fun Modifier.smartFadedBorderSurface(
    surfaceColor: Color,
    accentColor: Color = Color.Transparent,
    cornerRadius: androidx.compose.ui.unit.Dp = 14.dp
): Modifier {
    val isDark = CtecsTheme.colors.isDark
    val outlineBase = if (accentColor != Color.Transparent) {
        accentColor.copy(alpha = if (isDark) 0.28f else 0.22f)
    } else if (isDark) {
        Color(0xFF94A3B8).copy(alpha = 0.16f)
    } else {
        Color(0xFF0F172A).copy(alpha = 0.08f)
    }
    return this
        .smartFadeSurface(
            surfaceColor = surfaceColor,
            accentGlowColor = Color.Transparent,
            cornerRadius = cornerRadius,
            edgeAlpha = if (isDark) 0.72f else 0.86f,
            centerAlpha = 0.98f
        )
        .drawWithContent {
            drawContent()
            val strokePx = 1.dp.toPx()
            val half = strokePx * 0.5f
            val radPx = cornerRadius.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    0.0f to Color.Transparent,
                    0.14f to outlineBase,
                    0.86f to outlineBase,
                    1.0f to Color.Transparent
                ),
                topLeft = Offset(half, half),
                size = Size(
                    (size.width - strokePx).coerceAtLeast(1f),
                    (size.height - strokePx).coerceAtLeast(1f)
                ),
                cornerRadius = CornerRadius(radPx, radPx),
                style = Stroke(width = strokePx)
            )
        }
}
