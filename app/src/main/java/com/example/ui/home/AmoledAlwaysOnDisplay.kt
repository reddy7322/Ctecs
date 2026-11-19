package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HomeScreensaverSettings
import com.example.navigation.DevicePowerThermalMonitor
import com.example.navigation.KmStoneAlertEvent
import com.example.navigation.KmStoneAlertKind
import com.example.navigation.LockScreenNavStateResolver
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val ScreensaverAodActiveKey = SemanticsPropertyKey<Boolean>("ScreensaverAodActive")
var SemanticsPropertyReceiver.screensaverAodActive by ScreensaverAodActiveKey

val AodBurnInOffsetXKey = SemanticsPropertyKey<Float>("AodBurnInOffsetX")
var SemanticsPropertyReceiver.aodBurnInOffsetX by AodBurnInOffsetXKey

val AodBurnInOffsetYKey = SemanticsPropertyKey<Float>("AodBurnInOffsetY")
var SemanticsPropertyReceiver.aodBurnInOffsetY by AodBurnInOffsetYKey

data class AmoledBurnInOffset(
    val xDp: Float,
    val yDp: Float
)

/**
 * Pure, deterministic AMOLED burn-in protection helper.
 * Periodically shifts the minimal AOD content across a safe bounded elliptical path so no static
 * sub-pixel group stays illuminated continuously, without running frame-by-frame animations.
 */
object AmoledBurnInProtection {
    const val NORMAL_SHIFT_INTERVAL_MS = 50_000L
    const val POWER_SAVE_SHIFT_INTERVAL_MS = 75_000L
    const val DEFAULT_MAX_SHIFT_DP = 14f
    const val CYCLE_STEPS = 12

    fun computeShiftOffset(
        stepIndex: Int,
        enabled: Boolean,
        maxShiftDp: Float = DEFAULT_MAX_SHIFT_DP
    ): AmoledBurnInOffset {
        if (!enabled || maxShiftDp <= 0f) return AmoledBurnInOffset(0f, 0f)
        val normalizedStep = ((stepIndex % CYCLE_STEPS) + CYCLE_STEPS) % CYCLE_STEPS
        val angleRad = (normalizedStep.toDouble() / CYCLE_STEPS.toDouble()) * 2.0 * PI
        val radiusScale = if (normalizedStep % 2 == 0) 1.0 else 0.62
        val x = (cos(angleRad) * maxShiftDp * radiusScale).toFloat()
        val y = (sin(angleRad) * maxShiftDp * 0.85 * radiusScale).toFloat()
        return AmoledBurnInOffset(
            xDp = x.coerceIn(-maxShiftDp, maxShiftDp),
            yDp = y.coerceIn(-maxShiftDp, maxShiftDp)
        )
    }

    /**
     * Maps the user's [aodDimLevel] (0.18..0.78) into a safe, restrained Android window brightness
     * fraction while in-app AMOLED AOD is active, softened further under low-battery / thermal load.
     */
    fun resolveWindowBrightness(dimLevel: Float, conservePower: Boolean): Float {
        val clamped = dimLevel.coerceIn(
            HomeScreensaverSettings.MIN_AOD_DIM_LEVEL,
            HomeScreensaverSettings.MAX_AOD_DIM_LEVEL
        )
        val normalized = (clamped - HomeScreensaverSettings.MIN_AOD_DIM_LEVEL) /
            (HomeScreensaverSettings.MAX_AOD_DIM_LEVEL - HomeScreensaverSettings.MIN_AOD_DIM_LEVEL)
        val baseBrightness = 0.03f + normalized * 0.17f // 3% .. 20% hardware backlight/OLED level
        return if (conservePower) {
            (baseBrightness * 0.72f).coerceAtLeast(0.02f)
        } else {
            baseBrightness
        }
    }
}

/**
 * Formats a concise, truthful KM Stone arrival status line for AMOLED AOD display.
 */
fun resolveAodKmStoneStatusText(
    lastEvent: KmStoneAlertEvent?,
    isMonitoringActive: Boolean,
    nowMs: Long = System.currentTimeMillis()
): String {
    if (lastEvent != null) {
        val ageMs = (nowMs - lastEvent.timestampMs).coerceAtLeast(0L)
        if (ageMs <= 180_000L) {
            val sideSuffix = lastEvent.directionSide.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()
            return when (lastEvent.kind) {
                KmStoneAlertKind.ARRIVAL -> "Arrived KM ${lastEvent.stoneFormattedChainage}$sideSuffix"
                KmStoneAlertKind.ADVANCE_WARNING,
                KmStoneAlertKind.VOICE_DISTANCE -> "Approaching KM ${lastEvent.stoneFormattedChainage}$sideSuffix"
            }
        }
    }
    return if (isMonitoringActive) {
        "KM Stone Monitoring Active"
    } else {
        "KM Stone Monitoring Ready"
    }
}

/**
 * Minimalist, pure-black (`#000000`) AMOLED Always-On Display mode rendered inside the existing
 * in-app CTECS Screensaver.
 *
 * Design & battery guarantees:
 * - True `#000000` AMOLED background with zero cards, rectangular borders, heavy shadows, or bright gradients.
 * - Soft black radial/vertical edge fade margins.
 * - Displays ONLY user-selected essential telemetry in dim high-contrast typography.
 * - Periodic pixel-shift burn-in protection (no continuous 60fps animation loops).
 * - Minute-boundary clock updates instead of per-second redraws.
 * - Adapts dimness & shift interval smoothly under low-battery or elevated thermal status while
 *   keeping live GPS & KM Stone monitoring fully operational.
 */
@Composable
fun AmoledAlwaysOnDisplay(
    displayState: ScreensaverDisplayState,
    settings: HomeScreensaverSettings,
    isMirrorMode: Boolean = false,
    is24HourFormat: Boolean = true,
    nextKmLabel: String? = null,
    distanceToNextMetres: Long? = null,
    lastKmStoneEvent: KmStoneAlertEvent? = null,
    isKmStoneMonitoringActive: Boolean = false,
    onExitAod: () -> Unit,
    onSwitchToBlackScreenOff: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val powerThermal = remember(context) { DevicePowerThermalMonitor.snapshot(context) }

    var burnInStep by remember { mutableIntStateOf(0) }
    var clockMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Periodic burn-in pixel shift (50s normal / 75s battery-saver) — zero per-frame redraws
    LaunchedEffect(settings.aodBurnInProtectionEnabled, powerThermal.shouldConserveUiResources) {
        if (!settings.aodBurnInProtectionEnabled) {
            burnInStep = 0
            return@LaunchedEffect
        }
        val intervalMs = if (powerThermal.shouldConserveUiResources) {
            AmoledBurnInProtection.POWER_SAVE_SHIFT_INTERVAL_MS
        } else {
            AmoledBurnInProtection.NORMAL_SHIFT_INTERVAL_MS
        }
        while (true) {
            delay(intervalMs)
            burnInStep = (burnInStep + 1) % AmoledBurnInProtection.CYCLE_STEPS
        }
    }

    // Minute-aligned clock update when Time is enabled (zero per-second wakeups)
    LaunchedEffect(settings.aodShowTime) {
        if (!settings.aodShowTime) return@LaunchedEffect
        while (true) {
            val now = System.currentTimeMillis()
            clockMillis = now
            val msUntilNextMinute = (60_000L - (now % 60_000L)).coerceIn(5_000L, 60_000L)
            delay(msUntilNextMinute)
        }
    }

    val burnInOffset = remember(burnInStep, settings.aodBurnInProtectionEnabled) {
        AmoledBurnInProtection.computeShiftOffset(
            stepIndex = burnInStep,
            enabled = settings.aodBurnInProtectionEnabled
        )
    }

    val dimFactor = remember(settings.aodDimLevel, powerThermal.shouldConserveUiResources) {
        val base = settings.aodDimLevel.coerceIn(
            HomeScreensaverSettings.MIN_AOD_DIM_LEVEL,
            HomeScreensaverSettings.MAX_AOD_DIM_LEVEL
        )
        if (powerThermal.shouldConserveUiResources) (base * 0.84f).coerceAtLeast(0.16f) else base
    }

    val primaryTextColor = Color.White.copy(alpha = (dimFactor + 0.14f).coerceIn(0.24f, 0.88f))
    val secondaryTextColor = Color(0xFFCBD5E1).copy(alpha = (dimFactor * 0.92f).coerceIn(0.20f, 0.72f))
    val accentCyanDim = Color(0xFF7DD3FC).copy(alpha = (dimFactor * 0.95f).coerceIn(0.22f, 0.76f))
    val accentEmeraldDim = Color(0xFF6EE7B7).copy(alpha = (dimFactor * 0.92f).coerceIn(0.22f, 0.74f))
    val accentAmberDim = Color(0xFFFCD34D).copy(alpha = (dimFactor * 0.92f).coerceIn(0.22f, 0.74f))

    val timePattern = if (is24HourFormat) "HH:mm" else "h:mm a"
    val formattedTime = remember(clockMillis, timePattern) {
        SimpleDateFormat(timePattern, Locale.getDefault()).format(Date(clockMillis))
    }

    val kmStoneStatusText = remember(lastKmStoneEvent, isKmStoneMonitoringActive, clockMillis) {
        resolveAodKmStoneStatusText(
            lastEvent = lastKmStoneEvent,
            isMonitoringActive = isKmStoneMonitoringActive,
            nowMs = clockMillis
        )
    }

    val nextKmSummary = remember(nextKmLabel, distanceToNextMetres) {
        when {
            !nextKmLabel.isNullOrBlank() && distanceToNextMetres != null ->
                "$nextKmLabel • ${LockScreenNavStateResolver.formatNavigationDistance(distanceToNextMetres)}"
            !nextKmLabel.isNullOrBlank() -> nextKmLabel
            else -> null
        }
    }

    val wakeInteraction = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("screensaver_amoled_aod_root")
            .semantics {
                screensaverAodActive = true
                aodBurnInOffsetX = burnInOffset.xDp
                aodBurnInOffsetY = burnInOffset.yDp
            }
            .clickable(
                interactionSource = wakeInteraction,
                indication = null,
                onClickLabel = "Exit AMOLED Always-On Display",
                onClick = onExitAod
            )
    ) {
        val isCompactHeight = maxHeight < 420.dp

        // Mirror-aware minimal telemetry cluster with subtle burn-in pixel shift
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .graphicsLayer {
                    scaleX = if (isMirrorMode) -1f else 1f
                }
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 14.dp),
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = burnInOffset.xDp.dp.roundToPx(),
                            y = burnInOffset.yDp.dp.roundToPx()
                        )
                    }
                    .testTag("aod_telemetry_cluster")
            ) {
                if (settings.aodShowTime) {
                    Text(
                        text = formattedTime,
                        color = secondaryTextColor,
                        fontSize = if (isCompactHeight) 16.sp else 20.sp,
                        fontWeight = FontWeight.Light,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                        modifier = Modifier.testTag("aod_time_text")
                    )
                }

                if (settings.aodShowChainage) {
                    val hasValidChainage = displayState.chainageText.isNotBlank() &&
                        displayState.chainageText != "--+---"
                    Text(
                        text = if (hasValidChainage) displayState.chainageText else "--+---",
                        color = primaryTextColor,
                        fontSize = if (isCompactHeight) 48.sp else 62.sp,
                        fontWeight = FontWeight.Thin,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("aod_chainage_text")
                    )
                }

                if (settings.aodShowDirectionSide) {
                    if (!displayState.reliableSide.isNullOrBlank()) {
                        val sideColor = if (displayState.reliableSide == "LHS") accentCyanDim else accentAmberDim
                        val manualTag = if (displayState.isDirectionManual) " • MAN" else ""
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.testTag("aod_direction_row")
                        ) {
                            Text(
                                text = "${displayState.reliableSide} ${displayState.directionArrow}$manualTag".trim(),
                                color = sideColor,
                                fontSize = if (isCompactHeight) 15.sp else 18.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 2.sp,
                                maxLines = 1
                            )
                        }
                    } else if (
                        displayState.directionState == com.example.model.DirectionDetectionState.DIRECTION_CHANGING ||
                        displayState.directionState == com.example.model.DirectionDetectionState.DIRECTION_UNCERTAIN ||
                        displayState.directionState == com.example.model.DirectionDetectionState.GPS_SIGNAL_WEAK
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.testTag("aod_direction_status_row")
                        ) {
                            Text(
                                text = displayState.directionState.shortLabel,
                                color = secondaryTextColor,
                                fontSize = if (isCompactHeight) 12.sp else 13.5.sp,
                                fontWeight = FontWeight.Light,
                                letterSpacing = 1.2.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (settings.aodShowNextKmStone && !nextKmSummary.isNullOrBlank()) {
                    Text(
                        text = nextKmSummary,
                        color = secondaryTextColor,
                        fontSize = if (isCompactHeight) 13.sp else 15.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.6.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("aod_next_km_text")
                    )
                }

                if (settings.aodShowGpsStatus || settings.aodShowKmStoneStatus) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (settings.aodShowGpsStatus) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.testTag("aod_gps_status_row")
                            ) {
                                Icon(
                                    imageVector = if (displayState.isLiveGpsValid) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                                    contentDescription = "GPS status",
                                    tint = if (displayState.isLiveGpsValid) accentEmeraldDim else accentAmberDim,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (displayState.isLiveGpsValid) "GPS Live" else "GPS Acquiring",
                                    color = secondaryTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        }

                        if (settings.aodShowKmStoneStatus) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.testTag("aod_km_stone_status_row")
                            ) {
                                val isArrivedHighlight = lastKmStoneEvent != null &&
                                    (clockMillis - lastKmStoneEvent.timestampMs) <= 180_000L
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = "KM Stone status",
                                    tint = if (isArrivedHighlight) accentEmeraldDim else accentCyanDim,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = kmStoneStatusText,
                                    color = if (isArrivedHighlight) accentEmeraldDim else secondaryTextColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Smooth fading top/bottom black margins (no hard lines or boxes)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black, Color.Transparent)
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black)
                    )
                )
        )

        // Subtle, non-mirror-flipped low-luminance corner controls to restore normal Screensaver
        // or switch to pure-black Screen OFF.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(top = 6.dp, end = 8.dp)
        ) {
            if (onSwitchToBlackScreenOff != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("aod_switch_black_screen_btn")
                        .clickable(
                            onClickLabel = "Switch to pure black Screen OFF",
                            onClick = onSwitchToBlackScreenOff
                        )
                        .semantics { contentDescription = "Switch to pure black Screen OFF" }
                ) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = secondaryTextColor.copy(alpha = 0.65f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("aod_exit_to_screensaver_btn")
                    .clickable(
                        onClickLabel = "Restore normal Screensaver",
                        onClick = onExitAod
                    )
                    .semantics { contentDescription = "Exit Always-On Display and restore Screensaver" }
            ) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = accentCyanDim.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Compact inline live preview for `Settings -> Screensaver -> AMOLED Always-On Display`.
 * Reflects selected display elements, dim level, and burn-in shift state on true `#000000`.
 */
@Composable
fun AmoledAodPreviewBox(
    settings: HomeScreensaverSettings,
    chainageText: String = "204+320",
    sideText: String = "LHS ↑",
    nextKmText: String = "Next KM 205+000 • 680 m",
    modifier: Modifier = Modifier
) {
    val dim = settings.aodDimLevel.coerceIn(
        HomeScreensaverSettings.MIN_AOD_DIM_LEVEL,
        HomeScreensaverSettings.MAX_AOD_DIM_LEVEL
    )
    val primaryColor = Color.White.copy(alpha = (dim + 0.14f).coerceIn(0.24f, 0.88f))
    val secondaryColor = Color(0xFFCBD5E1).copy(alpha = (dim * 0.92f).coerceIn(0.20f, 0.72f))
    val cyanDim = Color(0xFF7DD3FC).copy(alpha = (dim * 0.95f).coerceIn(0.22f, 0.76f))
    val emeraldDim = Color(0xFF6EE7B7).copy(alpha = (dim * 0.92f).coerceIn(0.22f, 0.74f))

    val nowTime = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(148.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("screensaver_aod_inline_preview")
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (settings.aodShowTime) {
                Text(
                    text = nowTime,
                    color = secondaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp
                )
            }
            if (settings.aodShowChainage) {
                Text(
                    text = chainageText,
                    color = primaryColor,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Thin,
                    letterSpacing = 0.8.sp,
                    maxLines = 1
                )
            }
            if (settings.aodShowDirectionSide) {
                Text(
                    text = sideText,
                    color = cyanDim,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 1.5.sp
                )
            }
            if (settings.aodShowNextKmStone) {
                Text(
                    text = nextKmText,
                    color = secondaryColor,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (settings.aodShowGpsStatus || settings.aodShowKmStoneStatus) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (settings.aodShowGpsStatus) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GpsFixed,
                                contentDescription = null,
                                tint = emeraldDim,
                                modifier = Modifier.size(10.dp)
                            )
                            Text("GPS Live", color = secondaryColor, fontSize = 9.5.sp)
                        }
                    }
                    if (settings.aodShowKmStoneStatus) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = cyanDim,
                                modifier = Modifier.size(10.dp)
                            )
                            Text("KM Monitoring", color = secondaryColor, fontSize = 9.5.sp)
                        }
                    }
                }
            }
        }

        // Subtle AOD badge in corner of preview
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = Icons.Default.NightlightRound,
                contentDescription = null,
                tint = cyanDim.copy(alpha = 0.7f),
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = if (settings.aodBurnInProtectionEnabled) "AMOLED • Burn-in Shift ON" else "AMOLED AOD",
                color = secondaryColor.copy(alpha = 0.65f),
                fontSize = 8.5.sp
            )
        }
    }
}
