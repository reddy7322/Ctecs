package com.example.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NightlightRound
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.ScreenLockRotation
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.geo.OsmTileManager
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.HudColorPreset
import com.example.model.HudDirectionColorMode
import com.example.model.HudDirectionPosition
import com.example.model.HudDirectionStyle
import com.example.model.HudFontStyle
import com.example.model.HudLocationPosition
import com.example.model.HudMapMarkerStyle
import com.example.model.HudMapPosition
import com.example.model.HudPanelSize
import com.example.model.HudSpeedometerStyle
import com.example.model.HudVerticalPosition
import com.example.model.MiniMapStyle
import com.example.model.RouteData
import com.example.model.ScreensaverDisplayMode
import com.example.model.ScreensaverThemeMode
import com.example.model.resolveEffectiveScreensaverPresentation
import com.example.overlay.MiniMapRenderer
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

// Semantic verification keys for automated HUD & mirror physics verification
val DashboardMirrorScaleXKey = SemanticsPropertyKey<Float>("DashboardMirrorScaleX")
var SemanticsPropertyReceiver.dashboardMirrorScaleX by DashboardMirrorScaleXKey

val DashboardHudBrightnessKey = SemanticsPropertyKey<Float>("DashboardHudBrightness")
var SemanticsPropertyReceiver.dashboardHudBrightness by DashboardHudBrightnessKey

val DashboardLandscapeRotationKey = SemanticsPropertyKey<Float>("DashboardLandscapeRotation")
var SemanticsPropertyReceiver.dashboardLandscapeRotation by DashboardLandscapeRotationKey

val ScreensaverDisplayModeKey = SemanticsPropertyKey<String>("ScreensaverDisplayMode")
var SemanticsPropertyReceiver.screensaverDisplayMode by ScreensaverDisplayModeKey

val MiniMapRotationDegKey = SemanticsPropertyKey<Float>("MiniMapRotationDeg")
var SemanticsPropertyReceiver.miniMapRotationDeg by MiniMapRotationDegKey

val ScreensaverThemeDarkKey = SemanticsPropertyKey<Boolean>("ScreensaverThemeDark")
var SemanticsPropertyReceiver.screensaverThemeDark by ScreensaverThemeDarkKey

val ScreensaverScreenOffKey = SemanticsPropertyKey<Boolean>("ScreensaverScreenOff")
var SemanticsPropertyReceiver.screensaverScreenOff by ScreensaverScreenOffKey

private data class HudClusterPalette(
    val rootBackground: Color,
    val ambientGlowTop: Color,
    val panelBackground: Color,
    val panelBorder: Color,
    val primaryDigitColor: Color,
    val chainageGlowColor: Color,
    val secondaryTextColor: Color,
    val speedArcTrack: Color,
    val speedArcStart: Color,
    val speedArcEnd: Color,
    val needleColor: Color,
    val isDark: Boolean,
    val isHudMirror: Boolean
)

private fun resolveHudClusterPalette(
    useDarkInstrument: Boolean,
    isMirrorMode: Boolean,
    settings: HomeScreensaverSettings
): HudClusterPalette {
    val chainageTint = Color(settings.chainageColorPreset.colorLong)
    val chainageGlow = Color(settings.chainageColorPreset.glowLong)
    val speedAccent = Color(settings.speedometerColorPreset.colorLong)

    return when {
        isMirrorMode -> HudClusterPalette(
            rootBackground = Color(0xFF000000),
            ambientGlowTop = Color.Transparent,
            panelBackground = Color.Transparent,
            panelBorder = Color.Transparent,
            primaryDigitColor = if (settings.chainageColorPreset == HudColorPreset.WHITE) Color.White else chainageTint,
            chainageGlowColor = chainageGlow.copy(alpha = 0.28f),
            secondaryTextColor = Color(0xFFE0F2FE),
            speedArcTrack = Color(0xFF1E293B).copy(alpha = 0.50f),
            speedArcStart = speedAccent,
            speedArcEnd = Color(0xFF34D399),
            needleColor = speedAccent,
            isDark = true,
            isHudMirror = true
        )
        useDarkInstrument -> HudClusterPalette(
            rootBackground = Color(0xFF030712),
            ambientGlowTop = Color(0xFF0EA5E9).copy(alpha = 0.11f),
            panelBackground = Color(0xFF0B1324).copy(alpha = 0.24f),
            panelBorder = Color.Transparent,
            primaryDigitColor = chainageTint,
            chainageGlowColor = chainageGlow.copy(alpha = 0.22f),
            secondaryTextColor = Color(0xFFE2E8F0),
            speedArcTrack = Color(0xFF1E293B).copy(alpha = 0.48f),
            speedArcStart = speedAccent,
            speedArcEnd = Color(0xFF34D399),
            needleColor = speedAccent,
            isDark = true,
            isHudMirror = false
        )
        else -> {
            val lightChainageColor = when (settings.chainageColorPreset) {
                HudColorPreset.WHITE -> Color(0xFF0F172A)
                HudColorPreset.CYAN -> Color(0xFF0284C7)
                HudColorPreset.EMERALD -> Color(0xFF059669)
                HudColorPreset.AMBER -> Color(0xFFD97706)
            }
            val lightSpeedAccent = when (settings.speedometerColorPreset) {
                HudColorPreset.WHITE -> Color(0xFF0F172A)
                HudColorPreset.CYAN -> Color(0xFF0284C7)
                HudColorPreset.EMERALD -> Color(0xFF059669)
                HudColorPreset.AMBER -> Color(0xFFD97706)
            }
            HudClusterPalette(
                rootBackground = Color(0xFFF4F7FB),
                ambientGlowTop = Color(0xFF0EA5E9).copy(alpha = 0.08f),
                panelBackground = Color(0xFFFFFFFF).copy(alpha = 0.58f),
                panelBorder = Color.Transparent,
                primaryDigitColor = lightChainageColor,
                chainageGlowColor = lightSpeedAccent.copy(alpha = 0.12f),
                secondaryTextColor = Color(0xFF334155),
                speedArcTrack = Color(0xFFE2E8F0),
                speedArcStart = lightSpeedAccent,
                speedArcEnd = Color(0xFF059669),
                needleColor = lightSpeedAccent,
                isDark = false,
                isHudMirror = false
            )
        }
    }
}

/**
 * Renders the redesigned Automotive Instrument Cluster & Windshield Mirror HUD Screensaver.
 * Keeps the HUD clean, borderless, and minimal with compact Mirror/Normal and Orientation Lock
 * controls in screen-safe corners; touching anywhere else dismisses cleanly when enabled.
 *
 * Notice that in [ScreensaverDisplayMode.MIRROR], horizontal flip (`scaleX = -1f`) is applied
 * ONCE at the root container wrapping the complete rendered Landscape dashboard so every digit,
 * letter, gauge needle, directional arrow, location label, and Mini Map reflects upright on a
 * car windshield without altering any underlying GPS/chainage/bearing calculation.
 */
@Composable
fun AutomotiveChainageScreensaverContent(
    displayState: ScreensaverDisplayState,
    location: GeoPoint?,
    bearing: Float?,
    compassHeading: Float,
    routeData: RouteData?,
    mapThemePreference: String,
    mapTypePreference: String,
    isDarkAppTheme: Boolean,
    screensaverSettings: HomeScreensaverSettings,
    isSamplePreview: Boolean = false,
    sensorPosture: ScreensaverSensorPosture? = null,
    temporaryLandscapeOverride: Boolean = false,
    isScreenOff: Boolean = false,
    isAodActive: Boolean = false,
    is24HourFormat: Boolean = true,
    nextKmLabel: String? = null,
    distanceToNextMetres: Long? = null,
    lastKmStoneEvent: com.example.navigation.KmStoneAlertEvent? = null,
    isKmStoneMonitoringActive: Boolean = false,
    onToggleMirrorQuickMode: ((enableMirror: Boolean, currentlyLandscape: Boolean) -> Unit)? = null,
    onToggleOrientationLock: ((currentlyLandscape: Boolean) -> Unit)? = null,
    onToggleScreenOff: ((Boolean) -> Unit)? = null,
    onToggleAod: ((Boolean) -> Unit)? = null,
    onForceBlackScreenOff: (() -> Unit)? = null
) {
    val effectiveDarkInstrument = when (screensaverSettings.screensaverThemeMode) {
        ScreensaverThemeMode.LIGHT -> false
        ScreensaverThemeMode.DARK -> true
        ScreensaverThemeMode.SYSTEM -> isDarkAppTheme
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val isWindowPortrait = maxWidth < maxHeight
        val presentation = remember(
            screensaverSettings,
            sensorPosture,
            isWindowPortrait,
            temporaryLandscapeOverride
        ) {
            resolveEffectiveScreensaverPresentation(
                settings = screensaverSettings,
                sensorIsLandscape = sensorPosture?.isLandscape,
                windowIsLandscape = !isWindowPortrait,
                temporaryLandscapeOverride = temporaryLandscapeOverride
            )
        }
        val isMirrorMode = presentation.isMirror
        val isLandscapeMode = presentation.isLandscape
        val effectiveMode = presentation.activeMode

        val palette = remember(
            effectiveDarkInstrument,
            isMirrorMode,
            screensaverSettings.chainageColorPreset,
            screensaverSettings.speedometerColorPreset
        ) {
            resolveHudClusterPalette(
                useDarkInstrument = effectiveDarkInstrument,
                isMirrorMode = isMirrorMode,
                settings = screensaverSettings
            )
        }
        val targetHudAlpha = if (isMirrorMode) {
            screensaverSettings.hudBrightness.coerceIn(
                HomeScreensaverSettings.MIN_HUD_BRIGHTNESS,
                HomeScreensaverSettings.MAX_HUD_BRIGHTNESS
            )
        } else {
            1f
        }
        val hudAlpha by animateFloatAsState(
            targetValue = targetHudAlpha,
            animationSpec = tween(durationMillis = 220),
            label = "hud_brightness_transition"
        )
        val rotationDegrees = if (isLandscapeMode && isWindowPortrait) {
            if (sensorPosture?.isLandscape == true && !temporaryLandscapeOverride) {
                sensorPosture.softwareRotationDeg
            } else {
                90f
            }
        } else {
            0f
        }

        // Smooth fade-to-black & fade-back blackout alpha; pauses heavy dashboard animations when opaque
        val blackoutOverlayAlpha by animateFloatAsState(
            targetValue = if (isScreenOff) 1f else 0f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "screensaver_blackout_fade"
        )
        val pauseDashboardRendering = (isScreenOff && blackoutOverlayAlpha >= 0.99f) || (isAodActive && !isScreenOff)
        val showScreenOffBtn = onToggleScreenOff != null && screensaverSettings.showScreenOffControl
        val showAodQuickBtn = onToggleAod != null && screensaverSettings.aodEnabled && !isScreenOff && !isAodActive

        // Subtle animated transition pulse when toggling Mirror <-> Normal
        val transitionAlphaPulse = remember { Animatable(1f) }
        LaunchedEffect(effectiveMode) {
            if (!isScreenOff && transitionAlphaPulse.value == 1f) {
                transitionAlphaPulse.snapTo(0.84f)
                transitionAlphaPulse.animateTo(1f, animationSpec = tween(210))
            }
        }

        val renderCompleteDashboard: @Composable () -> Unit = {
            val scaleXFactor = if (isMirrorMode) -1f else 1f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (pauseDashboardRendering) Color.Black else palette.rootBackground)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("screensaver_dashboard_root")
                        .semantics {
                            dashboardMirrorScaleX = scaleXFactor
                            dashboardHudBrightness = targetHudAlpha
                            dashboardLandscapeRotation = rotationDegrees
                            screensaverDisplayMode = effectiveMode.name
                            screensaverThemeDark = palette.isDark
                            screensaverScreenOff = isScreenOff
                        }
                        .graphicsLayer {
                            scaleX = scaleXFactor
                            alpha = transitionAlphaPulse.value
                        }
                ) {
                    if (!pauseDashboardRendering) {
                        // Foreground instrument surface scaled by adjustable HUD brightness over opaque black
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(hudAlpha)
                        ) {
                            // Subtle automotive radial cluster ambient glow
                            if (!isMirrorMode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(
                                                    palette.ambientGlowTop,
                                                    palette.rootBackground.copy(alpha = 0.85f),
                                                    palette.rootBackground
                                                )
                                            )
                                        )
                                )
                            }

                            // True immersive edge-to-edge: only pad around physical display cutouts (punch-holes/notches)
                            val safeCutoutModifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.displayCutout)

                            if (isLandscapeMode) {
                                LandscapeAutomotiveCluster(
                                    displayState = displayState,
                                    location = location,
                                    bearing = bearing,
                                    compassHeading = compassHeading,
                                    routeData = routeData,
                                    screensaverSettings = screensaverSettings,
                                    palette = palette,
                                    modifier = safeCutoutModifier
                                )
                            } else {
                                PortraitAutomotiveCluster(
                                    displayState = displayState,
                                    location = location,
                                    bearing = bearing,
                                    compassHeading = compassHeading,
                                    routeData = routeData,
                                    screensaverSettings = screensaverSettings,
                                    palette = palette,
                                    modifier = safeCutoutModifier
                                )
                            }
                        }

                        // Sample badge ONLY inside explicit Settings preview mode (never during real Home screensaver)
                        if (isSamplePreview) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFF59E0B).copy(alpha = 0.20f),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 8.dp)
                                    .testTag("screensaver_preview_sample_badge")
                            ) {
                                Text(
                                    text = "SAMPLE DATA",
                                    color = Color(0xFFFCD34D),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Top-corner quick controls kept outside scaleX mirror wrapper so icons and touch targets
                // remain upright and never overlap chainage, speedometer, or Mini Map.
                if (!pauseDashboardRendering) {
                    if (onToggleOrientationLock != null) {
                        ScreensaverOrientationLockToggle(
                            isLocked = screensaverSettings.orientationLocked,
                            isDarkPalette = palette.isDark,
                            hudAlpha = hudAlpha,
                            onToggle = { onToggleOrientationLock(isLandscapeMode) },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .windowInsetsPadding(WindowInsets.displayCutout)
                                .padding(top = 6.dp, start = 8.dp)
                        )
                    }

                    if (onToggleMirrorQuickMode != null || (showScreenOffBtn && !isScreenOff) || showAodQuickBtn) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .windowInsetsPadding(WindowInsets.displayCutout)
                                .padding(top = 6.dp, end = 8.dp)
                        ) {
                            if (showAodQuickBtn) {
                                ScreensaverAodQuickToggle(
                                    isDarkPalette = palette.isDark,
                                    hudAlpha = hudAlpha,
                                    onToggle = { onToggleAod?.invoke(true) }
                                )
                            }
                            if (showScreenOffBtn && !isScreenOff) {
                                ScreensaverScreenOffToggle(
                                    isScreenOff = false,
                                    isDarkPalette = palette.isDark,
                                    hudAlpha = hudAlpha,
                                    onToggle = { onToggleScreenOff?.invoke(true) }
                                )
                            }
                            if (onToggleMirrorQuickMode != null) {
                                ScreensaverMirrorQuickToggle(
                                    isMirrorActive = isMirrorMode,
                                    isDarkPalette = palette.isDark,
                                    hudAlpha = hudAlpha,
                                    onToggle = {
                                        onToggleMirrorQuickMode(!isMirrorMode, isLandscapeMode)
                                    }
                                )
                            }
                        }
                    }
                }

                // Pure-black AMOLED Always-On Display (pauses map, speedometer & decorative animations;
                // preserves orientation, Mirror flip, live chainage & KM Stone monitoring)
                if (isAodActive && !isScreenOff) {
                    AmoledAlwaysOnDisplay(
                        displayState = displayState,
                        settings = screensaverSettings,
                        isMirrorMode = isMirrorMode,
                        is24HourFormat = is24HourFormat,
                        nextKmLabel = nextKmLabel,
                        distanceToNextMetres = distanceToNextMetres,
                        lastKmStoneEvent = lastKmStoneEvent,
                        isKmStoneMonitoringActive = isKmStoneMonitoringActive,
                        onExitAod = { onToggleAod?.invoke(false) },
                        onSwitchToBlackScreenOff = if (showScreenOffBtn) {
                            {
                                if (onForceBlackScreenOff != null) {
                                    onForceBlackScreenOff()
                                } else {
                                    onToggleAod?.invoke(false)
                                    onToggleScreenOff?.invoke(true)
                                }
                            }
                        } else {
                            null
                        }
                    )
                }

                // Pure-black distraction-free Screen OFF overlay (keeps session, GPS, chainage, and KM Stone alerts active)
                if (blackoutOverlayAlpha > 0.01f) {
                    val wakeInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = blackoutOverlayAlpha))
                            .testTag("screensaver_blackout_overlay")
                            .then(
                                if (isScreenOff && onToggleScreenOff != null) {
                                    Modifier.clickable(
                                        interactionSource = wakeInteraction,
                                        indication = null,
                                        onClickLabel = "Wake screensaver display",
                                        onClick = { onToggleScreenOff(false) }
                                    )
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        if (isScreenOff && showScreenOffBtn) {
                            ScreensaverScreenOffToggle(
                                isScreenOff = true,
                                isDarkPalette = true,
                                hudAlpha = 1f,
                                onToggle = { onToggleScreenOff?.invoke(false) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .windowInsetsPadding(WindowInsets.displayCutout)
                                    .padding(top = 6.dp, end = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (rotationDegrees != 0f) {
            Layout(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("screensaver_landscape_rotated_wrapper"),
                content = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = rotationDegrees }
                    ) {
                        renderCompleteDashboard()
                    }
                }
            ) { measurables, constraints ->
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val placeable = measurables.first().measure(
                    Constraints.fixed(width = h, height = w)
                )
                layout(w, h) {
                    placeable.place(
                        x = (w - h) / 2,
                        y = (h - w) / 2
                    )
                }
            }
        } else {
            renderCompleteDashboard()
        }
    }
}

@Composable
private fun ScreensaverOrientationLockToggle(
    isLocked: Boolean,
    isDarkPalette: Boolean,
    hudAlpha: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isLocked && isDarkPalette -> Color(0xFFF59E0B).copy(alpha = 0.24f)
            isLocked -> Color(0xFFFEF3C7).copy(alpha = 0.92f)
            isDarkPalette -> Color(0xFF0F172A).copy(alpha = 0.56f)
            else -> Color(0xFFFFFFFF).copy(alpha = 0.85f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "orient_lock_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isLocked && isDarkPalette -> Color(0xFFFCD34D)
            isLocked -> Color(0xFFB45309)
            isDarkPalette -> Color(0xFFE2E8F0)
            else -> Color(0xFF1E293B)
        },
        animationSpec = tween(durationMillis = 220),
        label = "orient_lock_content"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isLocked) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "orient_lock_scale"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.TopStart,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(hudAlpha.coerceIn(0.65f, 1f))
            .testTag("screensaver_orientation_lock_btn")
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 24.dp, color = Color(0xFF38BDF8)),
                role = androidx.compose.ui.semantics.Role.Switch,
                onClickLabel = if (isLocked) "Unlock orientation" else "Lock orientation",
                onClick = onToggle
            )
            .semantics {
                contentDescription = if (isLocked) "Orientation locked" else "Orientation unlocked"
            }
            .padding(2.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = bgColor,
            shadowElevation = if (isDarkPalette) 1.dp else 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (isLocked) Icons.Default.ScreenLockRotation else Icons.Default.ScreenRotation,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(13.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
                AnimatedContent(
                    targetState = if (isLocked) "Locked" else "Auto",
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.9f, animationSpec = tween(180))) togetherWith
                            fadeOut(tween(130))
                    },
                    label = "orient_lock_label"
                ) { stateLabel ->
                    Text(
                        text = stateLabel,
                        color = contentColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreensaverScreenOffToggle(
    isScreenOff: Boolean,
    isDarkPalette: Boolean,
    hudAlpha: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isScreenOff -> Color(0xFF0A1412).copy(alpha = 0.78f)
            isDarkPalette -> Color(0xFF0F172A).copy(alpha = 0.56f)
            else -> Color(0xFFFFFFFF).copy(alpha = 0.85f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "screen_off_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isScreenOff -> Color(0xFF34D399).copy(alpha = 0.36f)
            isDarkPalette -> Color(0xFF94A3B8).copy(alpha = 0.24f)
            else -> Color(0xFFCBD5E1).copy(alpha = 0.65f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "screen_off_border"
    )
    val iconColor by animateColorAsState(
        targetValue = when {
            isScreenOff -> Color(0xFF34D399)
            isDarkPalette -> Color(0xFFE2E8F0)
            else -> Color(0xFF1E293B)
        },
        animationSpec = tween(durationMillis = 220),
        label = "screen_off_icon_tint"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isScreenOff) 1.08f else 1.0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "screen_off_scale"
    )
    val controlAlpha by animateFloatAsState(
        targetValue = if (isScreenOff) 0.48f else hudAlpha.coerceIn(0.65f, 1f),
        animationSpec = tween(durationMillis = 240),
        label = "screen_off_alpha"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.TopEnd,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(controlAlpha)
            .testTag("screensaver_screen_off_toggle_btn")
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 24.dp, color = Color(0xFF34D399)),
                role = androidx.compose.ui.semantics.Role.Switch,
                onClickLabel = if (isScreenOff) "Screen ON" else "Screen OFF",
                onClick = onToggle
            )
            .semantics {
                contentDescription = if (isScreenOff) "Screen ON" else "Screen OFF"
            }
            .padding(2.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = bgColor,
            border = BorderStroke(0.75.dp, borderColor),
            shadowElevation = if (isScreenOff || isDarkPalette) 1.dp else 2.dp
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier
                        .size(14.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                )
            }
        }
    }
}

@Composable
private fun ScreensaverAodQuickToggle(
    isDarkPalette: Boolean,
    hudAlpha: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isDarkPalette) {
        Color(0xFF0F172A).copy(alpha = 0.56f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.85f)
    }
    val borderColor = if (isDarkPalette) {
        Color(0xFF38BDF8).copy(alpha = 0.30f)
    } else {
        Color(0xFFCBD5E1).copy(alpha = 0.65f)
    }
    val contentColor = if (isDarkPalette) Color(0xFF7DD3FC) else Color(0xFF0284C7)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.TopEnd,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(hudAlpha.coerceIn(0.65f, 1f))
            .testTag("screensaver_aod_toggle_btn")
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 24.dp, color = Color(0xFF38BDF8)),
                role = androidx.compose.ui.semantics.Role.Switch,
                onClickLabel = "Enable AMOLED Always-On Display",
                onClick = onToggle
            )
            .semantics {
                contentDescription = "AMOLED Always-On Display"
            }
            .padding(2.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = bgColor,
            border = BorderStroke(0.75.dp, borderColor),
            shadowElevation = if (isDarkPalette) 1.dp else 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NightlightRound,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "AOD",
                    color = contentColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
private fun ScreensaverMirrorQuickToggle(
    isMirrorActive: Boolean,
    isDarkPalette: Boolean,
    hudAlpha: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        targetValue = when {
            isMirrorActive -> Color(0xFF0284C7).copy(alpha = 0.28f)
            isDarkPalette -> Color(0xFF0F172A).copy(alpha = 0.56f)
            else -> Color(0xFFFFFFFF).copy(alpha = 0.85f)
        },
        animationSpec = tween(durationMillis = 220),
        label = "quick_toggle_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            isMirrorActive -> Color(0xFF7DD3FC)
            isDarkPalette -> Color(0xFFE2E8F0)
            else -> Color(0xFF1E293B)
        },
        animationSpec = tween(durationMillis = 220),
        label = "quick_toggle_content"
    )
    val iconRotation by animateFloatAsState(
        targetValue = if (isMirrorActive) 180f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "quick_toggle_icon_rot"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.TopEnd,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .alpha(hudAlpha.coerceIn(0.65f, 1f))
            .testTag("screensaver_mirror_quick_toggle")
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, radius = 24.dp, color = Color(0xFF38BDF8)),
                role = androidx.compose.ui.semantics.Role.Switch,
                onClickLabel = if (isMirrorActive) "Switch to Normal mode" else "Switch to Mirror mode",
                onClick = onToggle
            )
            .semantics {
                contentDescription = if (isMirrorActive) "Mirror Mode active, toggle for Normal Mode" else "Normal Mode active, toggle for Mirror Mode"
            }
            .padding(2.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = bgColor,
            shadowElevation = if (isDarkPalette) 1.dp else 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (isMirrorActive) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Color(0xFF38BDF8), CircleShape)
                    )
                }
                Icon(
                    imageVector = Icons.Default.Flip,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier
                        .size(13.dp)
                        .graphicsLayer { rotationY = iconRotation }
                )
                AnimatedContent(
                    targetState = if (isMirrorActive) "Mirror" else "Normal",
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(initialScale = 0.9f, animationSpec = tween(180))) togetherWith
                            fadeOut(tween(130))
                    },
                    label = "quick_toggle_label"
                ) { modeLabel ->
                    Text(
                        text = modeLabel,
                        color = contentColor,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberFittedChainageFontSizeSp(
    chainageText: String,
    fontStyle: HudFontStyle,
    preferredMaxSp: Float,
    minSp: Float,
    maxWidthDp: Dp,
    maxHeightDp: Dp
): Float {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val resolvedFamily = when (fontStyle) {
        HudFontStyle.MODERN, HudFontStyle.CONDENSED -> FontFamily.SansSerif
        HudFontStyle.DIGITAL -> FontFamily.Monospace
    }
    val measurementTemplate = remember(chainageText) {
        chainageText.map { ch -> if (ch.isDigit()) '8' else ch }.joinToString("")
    }

    return remember(
        measurementTemplate,
        resolvedFamily,
        preferredMaxSp,
        minSp,
        maxWidthDp,
        maxHeightDp,
        density.density,
        density.fontScale
    ) {
        val availWidthPx = with(density) { maxWidthDp.toPx() } * 0.88f
        val availHeightPx = with(density) { maxHeightDp.toPx() } * 0.90f
        if (availWidthPx <= 8f || availHeightPx <= 8f) return@remember minSp

        var low = minSp.coerceAtLeast(14f)
        var high = preferredMaxSp.coerceAtLeast(low)

        fun fitsAt(spValue: Float): Boolean {
            val baseStyle = TextStyle(
                fontSize = spValue.sp,
                lineHeight = (spValue * 1.04f).sp,
                fontFamily = resolvedFamily,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.sp,
                fontFeatureSettings = "tnum"
            )
            // Measure worst-case uniform slot width ("8" * digitCount + non-digits) AND whole string
            val fullRes = textMeasurer.measure(
                text = measurementTemplate,
                style = baseStyle,
                maxLines = 1,
                softWrap = false
            )
            val eightWidthPx = textMeasurer.measure(
                text = "8",
                style = baseStyle,
                maxLines = 1,
                softWrap = false
            ).size.width
            val nonDigitTemplate = measurementTemplate.filter { !it.isDigit() }
            val nonDigitWidthPx = if (nonDigitTemplate.isNotEmpty()) {
                textMeasurer.measure(
                    text = nonDigitTemplate,
                    style = baseStyle,
                    maxLines = 1,
                    softWrap = false
                ).size.width
            } else {
                0
            }
            val digitCount = measurementTemplate.count { it.isDigit() }
            val slotTotalWidthPx = (eightWidthPx * digitCount + nonDigitWidthPx).toFloat()
            val effectiveWidthPx = maxOf(fullRes.size.width.toFloat(), slotTotalWidthPx)
            return !fullRes.hasVisualOverflow &&
                effectiveWidthPx <= availWidthPx &&
                fullRes.size.height <= availHeightPx
        }

        if (fitsAt(high)) return@remember high
        if (!fitsAt(low)) return@remember low

        repeat(9) {
            val mid = (low + high) / 2f
            if (fitsAt(mid)) {
                low = mid
            } else {
                high = mid
            }
        }
        low
    }
}

@Composable
private fun HeroChainageCard(
    displayState: ScreensaverDisplayState,
    screensaverSettings: HomeScreensaverSettings,
    palette: HudClusterPalette,
    preferredCeilingSp: Float,
    includeLocationInside: Boolean,
    modifier: Modifier = Modifier
) {
    val hasStatusDirectionIndicator = displayState.reliableSide == null && (
        displayState.directionState == com.example.model.DirectionDetectionState.DIRECTION_CHANGING ||
            displayState.directionState == com.example.model.DirectionDetectionState.DIRECTION_UNCERTAIN ||
            displayState.directionState == com.example.model.DirectionDetectionState.GPS_SIGNAL_WEAK
        )
    val showHeroCard = screensaverSettings.showChainage ||
        (screensaverSettings.showDirectionSide && (displayState.reliableSide != null || hasStatusDirectionIndicator)) ||
        (includeLocationInside && screensaverSettings.showLocation && displayState.locationName.isNotBlank())

    if (!showHeroCard) return

    val glassCorner = when (screensaverSettings.chainagePanelSize) {
        HudPanelSize.COMPACT -> 22.dp
        HudPanelSize.STANDARD -> 28.dp
        HudPanelSize.EXPANDED -> 32.dp
    }
    val showSideBadge = screensaverSettings.showDirectionSide && (displayState.reliableSide != null || hasStatusDirectionIndicator)
    val showLocationRow = includeLocationInside && screensaverSettings.showLocation && displayState.locationName.isNotBlank()

    val glassBrush = if (palette.isHudMirror) {
        Brush.radialGradient(
            colors = listOf(
                Color.Black,
                Color.Black
            )
        )
    } else {
        Brush.radialGradient(
            colors = listOf(
                palette.chainageGlowColor.copy(alpha = 0.22f),
                palette.panelBackground.copy(alpha = 0.28f),
                Color.Transparent
            )
        )
    }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(glassCorner))
            .background(brush = glassBrush)
    ) {
        val compactVertical = maxHeight < 210.dp
        val panelPadH = when (screensaverSettings.chainagePanelSize) {
            HudPanelSize.COMPACT -> if (maxWidth < 260.dp) 6.dp else 10.dp
            HudPanelSize.STANDARD -> if (maxWidth < 260.dp) 8.dp else 14.dp
            HudPanelSize.EXPANDED -> if (maxWidth < 260.dp) 10.dp else 18.dp
        }
        val panelPadV = if (compactVertical) 6.dp else screensaverSettings.chainagePanelSize.paddingDp.dp

        val innerAvailW = (maxWidth - panelPadH * 2).coerceAtLeast(72.dp)
        val isInline = screensaverSettings.directionPosition == HudDirectionPosition.INLINE && showSideBadge
        val reservedInlineBadgeW = if (isInline) {
            (92f * screensaverSettings.directionSize.scaleFactor + 14f).dp
                .coerceAtMost(innerAvailW * 0.36f)
        } else {
            0.dp
        }
        val reservedBadgeH = if (!isInline && showSideBadge) {
            (34f * screensaverSettings.directionSize.scaleFactor + if (compactVertical) 6.dp.value else 10.dp.value).dp
        } else {
            0.dp
        }
        val reservedLocationH = if (showLocationRow) {
            (24f * screensaverSettings.locationFontSize.scaleFactor + if (compactVertical) 6.dp.value else 10.dp.value).dp
        } else {
            0.dp
        }

        val chainageAvailW = (innerAvailW - reservedInlineBadgeW).coerceAtLeast(64.dp)
        val rawMaxH = if (maxHeight.value.isFinite() && maxHeight < 1200.dp) {
            (maxHeight - panelPadV * 2 - reservedBadgeH - reservedLocationH).coerceAtLeast(38.dp)
        } else {
            130.dp
        }

        val scaledCeilingSp = (preferredCeilingSp * screensaverSettings.chainageFontSize.scaleFactor)
            .coerceIn(28f, 96f)

        val fittedChainageSp = rememberFittedChainageFontSizeSp(
            chainageText = displayState.chainageText,
            fontStyle = screensaverSettings.chainageFontStyle,
            preferredMaxSp = scaledCeilingSp,
            minSp = 18f,
            maxWidthDp = chainageAvailW,
            maxHeightDp = rawMaxH
        ).sp

        val renderDirectionBadge: @Composable () -> Unit = {
            AnimatedVisibility(
                visible = showSideBadge,
                enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.92f, animationSpec = tween(240)),
                exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f, animationSpec = tween(180))
            ) {
                val side = displayState.reliableSide
                if (side != null) {
                    val baseDirSp = (fittedChainageSp.value * 0.30f).coerceIn(12f, 19f)
                    val dirSp = (baseDirSp * screensaverSettings.directionSize.scaleFactor).coerceIn(10f, 22f).sp
                    val arrowDp = (16f * screensaverSettings.directionSize.scaleFactor).coerceIn(12f, 22f).dp
                    AutomotiveDirectionBadge(
                        side = side,
                        directionArrow = displayState.directionArrow,
                        isManualOverride = displayState.isDirectionManual,
                        style = screensaverSettings.directionIndicatorStyle,
                        colorMode = screensaverSettings.directionColorPreset,
                        arrowsAnimated = screensaverSettings.directionArrowsAnimated,
                        isHudMirror = palette.isHudMirror,
                        isDarkPalette = palette.isDark,
                        fontSize = dirSp,
                        arrowSize = arrowDp,
                        modifier = Modifier.testTag("screensaver_direction_badge")
                    )
                } else if (hasStatusDirectionIndicator) {
                    val statusTint = when (displayState.directionState) {
                        com.example.model.DirectionDetectionState.DIRECTION_CHANGING ->
                            if (palette.isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
                        com.example.model.DirectionDetectionState.GPS_SIGNAL_WEAK ->
                            if (palette.isDark) Color(0xFFF87171) else Color(0xFFDC2626)
                        else -> palette.secondaryTextColor
                    }
                    Surface(
                        shape = CircleShape,
                        color = statusTint.copy(alpha = if (palette.isDark || palette.isHudMirror) 0.14f else 0.11f),
                        shadowElevation = 0.dp,
                        modifier = Modifier.testTag("screensaver_direction_status_badge")
                    ) {
                        Text(
                            text = displayState.directionState.shortLabel,
                            color = statusTint,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        val renderChainageDigits: @Composable (Modifier) -> Unit = { digitMod ->
            if (screensaverSettings.showChainage) {
                AutomotiveOdometerChainage(
                    chainageText = displayState.chainageText,
                    isIncreasing = displayState.isIncreasingTrend,
                    textColor = palette.primaryDigitColor,
                    glowColor = palette.chainageGlowColor,
                    fontSize = fittedChainageSp,
                    lineHeight = (fittedChainageSp.value * 1.04f).sp,
                    fontStyle = screensaverSettings.chainageFontStyle,
                    odometerAnimated = screensaverSettings.chainageOdometerAnimation,
                    animationDurationMs = screensaverSettings.chainageAnimationSpeed.durationMs,
                    modifier = digitMod
                        .testTag("screensaver_chainage_text")
                        .clearAndSetSemantics {
                            text = AnnotatedString(displayState.chainageText)
                            contentDescription = displayState.chainageText
                        }
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = panelPadH, vertical = panelPadV)
        ) {
            when (screensaverSettings.directionPosition) {
                HudDirectionPosition.ABOVE_CHAINAGE -> {
                    if (showSideBadge) {
                        renderDirectionBadge()
                        if (screensaverSettings.showChainage) {
                            Spacer(modifier = Modifier.height(if (compactVertical) 4.dp else 8.dp))
                        }
                    }
                    renderChainageDigits(Modifier)
                }
                HudDirectionPosition.BELOW_CHAINAGE -> {
                    renderChainageDigits(Modifier)
                    if (showSideBadge) {
                        if (screensaverSettings.showChainage) {
                            Spacer(modifier = Modifier.height(if (compactVertical) 5.dp else 10.dp))
                        }
                        renderDirectionBadge()
                    }
                }
                HudDirectionPosition.INLINE -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        renderChainageDigits(Modifier.weight(1f, fill = false))
                        renderDirectionBadge()
                    }
                }
            }

            if (includeLocationInside && screensaverSettings.showLocation) {
                AnimatedVisibility(
                    visible = displayState.locationName.isNotBlank(),
                    enter = fadeIn(tween(260)),
                    exit = fadeOut(tween(180))
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(modifier = Modifier.height(if (compactVertical) 4.dp else 8.dp))
                        AutomotiveLocationLabel(
                            locationName = displayState.locationName,
                            textColor = palette.secondaryTextColor,
                            shadowColor = palette.chainageGlowColor,
                            fontSize = (13.5f * screensaverSettings.locationFontSize.scaleFactor)
                                .coerceIn(10.5f, if (compactVertical) 15f else 19f).sp,
                            modifier = Modifier.testTag("screensaver_location_name")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassMiniMapContainer(
    location: GeoPoint?,
    bearing: Float?,
    compassHeading: Float,
    routeData: RouteData?,
    screensaverSettings: HomeScreensaverSettings,
    palette: HudClusterPalette,
    modifier: Modifier = Modifier
) {
    val edgeShape = RoundedCornerShape(24.dp)

    Box(
        modifier = modifier
            .alpha(screensaverSettings.miniMapOpacity.coerceIn(0.35f, 1f))
            .clip(edgeShape)
            .background(
                if (palette.isHudMirror) Color.Black
                else if (palette.isDark) Color(0xFF060E1D).copy(alpha = 0.38f)
                else Color(0xFFE2E8F0).copy(alpha = 0.42f)
            )
    ) {
        ScreensaverLiveMiniMapPanel(
            location = location,
            bearing = bearing,
            compassHeading = compassHeading,
            routeData = routeData,
            screensaverSettings = screensaverSettings,
            isHudMirror = palette.isHudMirror,
            isDarkPalette = palette.isDark,
            modifier = Modifier
                .fillMaxSize()
                .testTag("screensaver_glass_map")
        )

        // Soft peripheral fade so map blends seamlessly into the screensaver surface with zero borders
        val edgeGlowColor = if (screensaverSettings.miniMapShowBorder) {
            Color(screensaverSettings.miniMapBorderColorPreset.colorLong).copy(
                alpha = if (palette.isDark) 0.10f else 0.06f
            )
        } else {
            Color.Transparent
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            edgeGlowColor,
                            palette.rootBackground.copy(alpha = if (palette.isHudMirror) 0.48f else 0.42f)
                        )
                    )
                )
        )
    }
}

@Composable
private fun PortraitAutomotiveCluster(
    displayState: ScreensaverDisplayState,
    location: GeoPoint?,
    bearing: Float?,
    compassHeading: Float,
    routeData: RouteData?,
    screensaverSettings: HomeScreensaverSettings,
    palette: HudClusterPalette,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("screensaver_portrait_layout")
    ) {
        val preferredCeilingSp = (maxWidth.value * 0.175f).coerceIn(46f, 76f)
        val baseDialSize = minOf(maxWidth * 0.44f, maxHeight * 0.24f)
        val dialSize = (baseDialSize * screensaverSettings.speedometerSize.scaleFactor).coerceIn(92.dp, 220.dp)
        val mapVisible = screensaverSettings.miniMapVisible
        val gap = (maxHeight * 0.018f).coerceIn(8.dp, 16.dp)

        val locationOutsideTop = screensaverSettings.showLocation &&
            screensaverSettings.locationPosition == HudLocationPosition.TOP_BAR &&
            displayState.locationName.isNotBlank()
        val locationOutsideAboveMap = screensaverSettings.showLocation &&
            screensaverSettings.locationPosition == HudLocationPosition.ABOVE_MAP &&
            displayState.locationName.isNotBlank()
        val locationInsideHero = screensaverSettings.showLocation &&
            screensaverSettings.locationPosition == HudLocationPosition.BELOW_SIDE

        val locationSp = (14f * screensaverSettings.locationFontSize.scaleFactor).coerceIn(11f, 20f).sp
        val mapWeight = screensaverSettings.miniMapSize.weightBias.coerceIn(0.65f, 1.45f)

        val heroBlock: @Composable () -> Unit = {
            HeroChainageCard(
                displayState = displayState,
                screensaverSettings = screensaverSettings,
                palette = palette,
                preferredCeilingSp = preferredCeilingSp,
                includeLocationInside = locationInsideHero,
                modifier = Modifier.fillMaxWidth()
            )
        }

        val speedBlock: @Composable () -> Unit = {
            if (screensaverSettings.showSpeedometer) {
                AutomotiveSpeedometerDisplay(
                    speedKmh = displayState.speedKmh,
                    speedText = displayState.speedText,
                    style = screensaverSettings.speedometerStyle,
                    animateNeedle = screensaverSettings.speedometerAnimateNeedle,
                    animateNumbers = screensaverSettings.speedometerAnimateNumbers,
                    palette = palette,
                    dialSize = dialSize
                )
            }
        }

        val mapBlock: @Composable (Modifier) -> Unit = { mapMod ->
            GlassMiniMapContainer(
                location = location,
                bearing = bearing,
                compassHeading = compassHeading,
                routeData = routeData,
                screensaverSettings = screensaverSettings,
                palette = palette,
                modifier = mapMod
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (mapVisible) Arrangement.SpaceBetween else Arrangement.Center
        ) {
            if (locationOutsideTop) {
                AutomotiveLocationLabel(
                    locationName = displayState.locationName,
                    textColor = palette.secondaryTextColor,
                    shadowColor = palette.chainageGlowColor,
                    fontSize = locationSp,
                    modifier = Modifier
                        .padding(bottom = gap)
                        .testTag("screensaver_location_name")
                )
            }

            if (mapVisible && screensaverSettings.miniMapPosition == HudMapPosition.TOP) {
                mapBlock(Modifier.fillMaxWidth().weight(mapWeight))
                Spacer(modifier = Modifier.height(gap))
                if (locationOutsideAboveMap) {
                    AutomotiveLocationLabel(
                        locationName = displayState.locationName,
                        textColor = palette.secondaryTextColor,
                        shadowColor = palette.chainageGlowColor,
                        fontSize = locationSp,
                        modifier = Modifier
                            .padding(bottom = gap / 2)
                            .testTag("screensaver_location_name")
                    )
                }
                if (screensaverSettings.chainagePanelPosition == HudVerticalPosition.BOTTOM) {
                    speedBlock()
                    if (screensaverSettings.showSpeedometer) Spacer(modifier = Modifier.height(gap))
                    heroBlock()
                } else {
                    heroBlock()
                    if (screensaverSettings.showSpeedometer) {
                        Spacer(modifier = Modifier.height(gap))
                        speedBlock()
                    }
                }
            } else {
                when (screensaverSettings.chainagePanelPosition) {
                    HudVerticalPosition.TOP -> {
                        heroBlock()
                        if (screensaverSettings.showSpeedometer) {
                            Spacer(modifier = Modifier.height(gap))
                            speedBlock()
                        }
                    }
                    HudVerticalPosition.CENTER, HudVerticalPosition.BOTTOM -> {
                        if (screensaverSettings.showSpeedometer) {
                            speedBlock()
                            Spacer(modifier = Modifier.height(gap))
                        }
                        heroBlock()
                    }
                }

                if (locationOutsideAboveMap) {
                    Spacer(modifier = Modifier.height(gap))
                    AutomotiveLocationLabel(
                        locationName = displayState.locationName,
                        textColor = palette.secondaryTextColor,
                        shadowColor = palette.chainageGlowColor,
                        fontSize = locationSp,
                        modifier = Modifier.testTag("screensaver_location_name")
                    )
                }

                if (mapVisible) {
                    Spacer(modifier = Modifier.height(gap))
                    mapBlock(Modifier.fillMaxWidth().weight(mapWeight))
                }
            }
        }
    }
}

@Composable
private fun LandscapeAutomotiveCluster(
    displayState: ScreensaverDisplayState,
    location: GeoPoint?,
    bearing: Float?,
    compassHeading: Float,
    routeData: RouteData?,
    screensaverSettings: HomeScreensaverSettings,
    palette: HudClusterPalette,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("screensaver_landscape_layout")
    ) {
        val mapVisible = screensaverSettings.miniMapVisible
        val speedVisible = screensaverSettings.showSpeedometer

        // Prioritize center chainage clarity while balancing speed gauge & Mini Map
        val rawMapW = if (mapVisible) (0.30f * screensaverSettings.miniMapSize.weightBias).coerceIn(0.22f, 0.36f) else 0f
        val rawSpeedW = if (speedVisible) 0.24f else 0f
        val rawCenterW = (1f - rawMapW - rawSpeedW).coerceAtLeast(0.40f)
        val totalW = rawMapW + rawSpeedW + rawCenterW
        val mapWeight = rawMapW / totalW
        val leftWeight = rawSpeedW / totalW
        val centerWeight = rawCenterW / totalW

        val preferredCeilingSp = (maxHeight.value * 0.25f).coerceIn(38f, 68f)
        val baseDialSize = minOf(maxWidth * leftWeight.coerceAtLeast(0.20f) - 8.dp, maxHeight * 0.78f)
        val dialSize = (baseDialSize * screensaverSettings.speedometerSize.scaleFactor).coerceIn(84.dp, 210.dp)
        val rowGap = if (maxWidth < 560.dp) 8.dp else 12.dp

        val speedPanel: @Composable androidx.compose.foundation.layout.RowScope.(Float) -> Unit = { w ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(w)
                    .fillMaxHeight()
                    .padding(4.dp)
            ) {
                AutomotiveSpeedometerDisplay(
                    speedKmh = displayState.speedKmh,
                    speedText = displayState.speedText,
                    style = screensaverSettings.speedometerStyle,
                    animateNeedle = screensaverSettings.speedometerAnimateNeedle,
                    animateNumbers = screensaverSettings.speedometerAnimateNumbers,
                    palette = palette,
                    dialSize = dialSize
                )
            }
        }

        val centerPanel: @Composable androidx.compose.foundation.layout.RowScope.(Float) -> Unit = { w ->
            HeroChainageCard(
                displayState = displayState,
                screensaverSettings = screensaverSettings,
                palette = palette,
                preferredCeilingSp = preferredCeilingSp,
                includeLocationInside = screensaverSettings.showLocation,
                modifier = Modifier
                    .weight(w)
                    .fillMaxHeight()
            )
        }

        val mapPanel: @Composable androidx.compose.foundation.layout.RowScope.(Float) -> Unit = { w ->
            GlassMiniMapContainer(
                location = location,
                bearing = bearing,
                compassHeading = compassHeading,
                routeData = routeData,
                screensaverSettings = screensaverSettings,
                palette = palette,
                modifier = Modifier
                    .weight(w)
                    .fillMaxHeight()
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(rowGap)
        ) {
            if (mapVisible && screensaverSettings.miniMapPosition == HudMapPosition.TOP) {
                mapPanel(mapWeight)
                centerPanel(centerWeight)
                if (speedVisible) speedPanel(leftWeight)
            } else {
                if (speedVisible) speedPanel(leftWeight)
                centerPanel(centerWeight)
                if (mapVisible) mapPanel(mapWeight)
            }
        }
    }
}

@Composable
private fun AutomotiveSpeedometerDisplay(
    speedKmh: Int?,
    speedText: String,
    style: HudSpeedometerStyle,
    animateNeedle: Boolean,
    animateNumbers: Boolean,
    palette: HudClusterPalette,
    dialSize: Dp
) {
    if (style == HudSpeedometerStyle.DIGITAL) {
        AutomotiveDigitalSpeedometer(
            speedKmh = speedKmh,
            speedText = speedText,
            animateNumbers = animateNumbers,
            palette = palette,
            cardSize = dialSize
        )
    } else {
        AutomotiveCircularSpeedometer(
            speedKmh = speedKmh,
            speedText = speedText,
            animateNeedle = animateNeedle,
            animateNumbers = animateNumbers,
            palette = palette,
            dialSize = dialSize
        )
    }
}

@Composable
private fun AutomotiveDigitalSpeedometer(
    speedKmh: Int?,
    speedText: String,
    animateNumbers: Boolean,
    palette: HudClusterPalette,
    cardSize: Dp
) {
    val isLive = speedKmh != null
    val digitalNumber = speedKmh?.toString() ?: "--"
    val numberFontSize = (cardSize.value * 0.34f).coerceIn(28f, 56f).sp
    val unitFontSize = (cardSize.value * 0.10f).coerceIn(11f, 15f).sp

    val glassHaloBrush = if (palette.isHudMirror) {
        Brush.radialGradient(listOf(Color.Black, Color.Black))
    } else {
        Brush.radialGradient(
            listOf(
                palette.speedArcStart.copy(alpha = 0.16f),
                palette.panelBackground.copy(alpha = 0.24f),
                Color.Transparent
            )
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = cardSize * 1.12f, height = cardSize * 0.78f)
            .clip(RoundedCornerShape(24.dp))
            .background(glassHaloBrush)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("screensaver_speed_text")
                .clearAndSetSemantics {
                    text = AnnotatedString(speedText)
                    contentDescription = speedText
                }
        ) {
            if (animateNumbers) {
                AnimatedContent(
                    targetState = digitalNumber,
                    transitionSpec = { fadeIn(tween(170)) togetherWith fadeOut(tween(140)) },
                    label = "digital_speed_digits"
                ) { txt ->
                    Text(
                        text = txt,
                        style = TextStyle(
                            color = if (isLive) palette.primaryDigitColor else palette.secondaryTextColor.copy(alpha = 0.55f),
                            fontSize = numberFontSize,
                            fontWeight = FontWeight.Black,
                            fontFeatureSettings = "tnum",
                            shadow = Shadow(color = palette.chainageGlowColor, offset = Offset.Zero, blurRadius = 18f)
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                Text(
                    text = digitalNumber,
                    style = TextStyle(
                        color = if (isLive) palette.primaryDigitColor else palette.secondaryTextColor.copy(alpha = 0.55f),
                        fontSize = numberFontSize,
                        fontWeight = FontWeight.Black,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
            Text(
                text = "km/h",
                color = palette.speedArcStart,
                fontSize = unitFontSize,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun AutomotiveCircularSpeedometer(
    speedKmh: Int?,
    speedText: String,
    animateNeedle: Boolean,
    animateNumbers: Boolean,
    palette: HudClusterPalette,
    dialSize: Dp
) {
    val isLive = speedKmh != null
    val targetSpeed = (speedKmh ?: 0).coerceIn(0, 260).toFloat()
    val animatedSpeed by animateFloatAsState(
        targetValue = targetSpeed,
        animationSpec = tween(durationMillis = if (animateNeedle) 260 else 0, easing = FastOutSlowInEasing),
        label = "hud_speedometer_needle"
    )
    val effectiveSpeed = if (animateNeedle) animatedSpeed else targetSpeed
    val sweepFraction = (effectiveSpeed / 160f).coerceIn(0f, 1f)
    val startAngle = 150f
    val totalSweep = 240f
    val activeAngle = startAngle + totalSweep * sweepFraction

    val digitalNumber = speedKmh?.toString() ?: "--"
    val digitalFontSize = (dialSize.value * 0.25f).coerceIn(20f, 46f).sp
    val unitFontSize = (dialSize.value * 0.085f).coerceIn(10f, 14f).sp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(dialSize)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeMain = size.minDimension * 0.072f
            val inset = strokeMain * 1.15f
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerR = (size.minDimension / 2f) - inset * 0.55f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (palette.isHudMirror) {
                        listOf(Color.Black, Color.Black)
                    } else if (palette.isDark) {
                        listOf(
                            Color(0xFF0A162B).copy(alpha = 0.36f),
                            Color(0xFF040A17).copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    } else {
                        listOf(
                            Color(0xFFFFFFFF).copy(alpha = 0.72f),
                            Color(0xFFE2E8F0).copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    },
                    center = Offset(cx, cy),
                    radius = outerR
                ),
                radius = outerR,
                center = Offset(cx, cy)
            )
            drawArc(
                color = palette.speedArcTrack,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeMain, cap = StrokeCap.Round)
            )

            for (step in 0..16) {
                val deg = startAngle + (totalSweep * step / 16f)
                val rad = Math.toRadians(deg.toDouble())
                val isMajor = step % 2 == 0
                val innerTickR = outerR - strokeMain * (if (isMajor) 1.52f else 1.25f)
                val outerTickR = outerR - strokeMain * 0.92f
                val passed = deg <= activeAngle && isLive
                drawLine(
                    color = when {
                        passed -> palette.speedArcEnd
                        isMajor -> palette.secondaryTextColor.copy(alpha = 0.45f)
                        else -> palette.secondaryTextColor.copy(alpha = 0.22f)
                    },
                    start = Offset(
                        cx + cos(rad).toFloat() * innerTickR,
                        cy + sin(rad).toFloat() * innerTickR
                    ),
                    end = Offset(
                        cx + cos(rad).toFloat() * outerTickR,
                        cy + sin(rad).toFloat() * outerTickR
                    ),
                    strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            if (isLive && sweepFraction > 0.004f) {
                val arcBrush = Brush.sweepGradient(
                    0.0f to palette.speedArcStart,
                    0.5f to palette.speedArcEnd,
                    1.0f to Color(0xFFFBBF24),
                    center = Offset(cx, cy)
                )
                drawArc(
                    brush = arcBrush,
                    startAngle = startAngle,
                    sweepAngle = totalSweep * sweepFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeMain, cap = StrokeCap.Round)
                )
            }

            val needleAngleRad = Math.toRadians(activeAngle.toDouble())
            val needleInnerR = outerR * 0.56f
            val needleOuterR = outerR - strokeMain * 1.1f
            drawLine(
                color = if (isLive) palette.needleColor else palette.secondaryTextColor.copy(alpha = 0.28f),
                start = Offset(
                    cx + cos(needleAngleRad).toFloat() * needleInnerR,
                    cy + sin(needleAngleRad).toFloat() * needleInnerR
                ),
                end = Offset(
                    cx + cos(needleAngleRad).toFloat() * needleOuterR,
                    cy + sin(needleAngleRad).toFloat() * needleOuterR
                ),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(top = 4.dp)
                .testTag("screensaver_speed_text")
                .clearAndSetSemantics {
                    text = AnnotatedString(speedText)
                    contentDescription = speedText
                }
        ) {
            if (animateNumbers) {
                AnimatedContent(
                    targetState = digitalNumber,
                    transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(120)) },
                    label = "circular_speed_digits"
                ) { txt ->
                    Text(
                        text = txt,
                        style = TextStyle(
                            color = if (isLive) palette.primaryDigitColor else palette.secondaryTextColor.copy(alpha = 0.55f),
                            fontSize = digitalFontSize,
                            fontWeight = FontWeight.Black,
                            fontFeatureSettings = "tnum",
                            shadow = Shadow(
                                color = palette.chainageGlowColor,
                                offset = Offset.Zero,
                                blurRadius = 14f
                            )
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                Text(
                    text = digitalNumber,
                    style = TextStyle(
                        color = if (isLive) palette.primaryDigitColor else palette.secondaryTextColor.copy(alpha = 0.55f),
                        fontSize = digitalFontSize,
                        fontWeight = FontWeight.Black,
                        fontFeatureSettings = "tnum"
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
            Text(
                text = "km/h",
                color = palette.speedArcStart,
                fontSize = unitFontSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun AutomotiveOdometerChainage(
    chainageText: String,
    isIncreasing: Boolean,
    textColor: Color,
    glowColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontStyle: HudFontStyle,
    odometerAnimated: Boolean,
    animationDurationMs: Int,
    modifier: Modifier = Modifier
) {
    val resolvedFamily = when (fontStyle) {
        HudFontStyle.MODERN -> FontFamily.SansSerif
        HudFontStyle.CONDENSED -> FontFamily.SansSerif
        HudFontStyle.DIGITAL -> FontFamily.Monospace
    }
    val digitMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val style = TextStyle(
        color = textColor,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = resolvedFamily,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum",
        shadow = Shadow(
            color = glowColor,
            offset = Offset.Zero,
            blurRadius = 24f
        )
    )

    if (!odometerAnimated) {
        Text(
            text = chainageText,
            style = style,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = modifier
        )
        return
    }

    val tabularDigitWidthDp = remember(fontSize, resolvedFamily, density.density, density.fontScale) {
        val measured = digitMeasurer.measure(
            text = "8",
            style = style,
            maxLines = 1,
            softWrap = false
        )
        with(density) { measured.size.width.toDp() }
    }

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        val len = chainageText.length
        for (i in 0 until len) {
            val ch = chainageText[i]
            val slotKey = len - i
            val slotModifier = if (ch.isDigit()) {
                Modifier.width(tabularDigitWidthDp)
            } else {
                Modifier
            }
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val enterOffset = { fullHeight: Int ->
                        if (isIncreasing) fullHeight / 3 else -fullHeight / 3
                    }
                    val exitOffset = { fullHeight: Int ->
                        if (isIncreasing) -fullHeight / 3 else fullHeight / 3
                    }
                    (slideInVertically(tween(animationDurationMs, easing = FastOutSlowInEasing), enterOffset) +
                        fadeIn(tween(animationDurationMs))) togetherWith
                        (slideOutVertically(tween((animationDurationMs * 0.8f).toInt()), exitOffset) +
                            fadeOut(tween((animationDurationMs * 0.7f).toInt())))
                },
                label = "hud_odometer_digit_$slotKey",
                modifier = slotModifier
            ) { targetChar ->
                Text(
                    text = targetChar.toString(),
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AutomotiveDirectionBadge(
    side: String,
    directionArrow: String = if (side == "LHS") "↑" else "↓",
    isManualOverride: Boolean = false,
    style: HudDirectionStyle,
    colorMode: HudDirectionColorMode,
    arrowsAnimated: Boolean,
    isHudMirror: Boolean,
    isDarkPalette: Boolean,
    fontSize: TextUnit,
    arrowSize: Dp,
    modifier: Modifier = Modifier
) {
    val isLhs = side == "LHS"
    val targetColor = when (colorMode) {
        HudDirectionColorMode.AUTO_SIDE -> if (isDarkPalette) {
            if (isLhs) Color(0xFF34D399) else Color(0xFF38BDF8)
        } else {
            if (isLhs) Color(0xFF059669) else Color(0xFF0284C7)
        }
        HudDirectionColorMode.CYAN -> if (isDarkPalette) Color(0xFF38BDF8) else Color(0xFF0284C7)
        HudDirectionColorMode.EMERALD -> if (isDarkPalette) Color(0xFF34D399) else Color(0xFF059669)
        HudDirectionColorMode.AMBER -> if (isDarkPalette) Color(0xFFFBBF24) else Color(0xFFD97706)
        HudDirectionColorMode.WHITE -> if (isDarkPalette) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    }
    val accentColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(250),
        label = "hud_direction_accent"
    )
    val glassBg = when (style) {
        HudDirectionStyle.PILL -> accentColor.copy(alpha = if (isDarkPalette || isHudMirror) 0.14f else 0.12f)
        HudDirectionStyle.OUTLINE, HudDirectionStyle.MINIMAL -> Color.Transparent
    }
    val borderStroke = when (style) {
        HudDirectionStyle.PILL -> null
        HudDirectionStyle.OUTLINE -> BorderStroke(0.6.dp, accentColor.copy(alpha = 0.36f))
        HudDirectionStyle.MINIMAL -> null
    }

    val arrowOffsetX = if (arrowsAnimated) {
        val arrowTransition = rememberInfiniteTransition(label = "hud_direction_motion")
        val arrowPulse by arrowTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1050, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "hud_direction_pulse"
        )
        (arrowPulse * (if (isLhs) -2.8f else 2.8f)).dp
    } else {
        0.dp
    }

    Surface(
        shape = CircleShape,
        color = glassBg,
        border = borderStroke,
        shadowElevation = 0.dp,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = Triple(side, directionArrow, isManualOverride),
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220))) togetherWith
                    (fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)))
            },
            label = "hud_direction_side"
        ) { (targetSide, targetArrow, isManual) ->
            val targetLhs = targetSide == "LHS"
            val arrowDesc = if (targetArrow == "↑") "Increasing" else "Decreasing"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(
                    horizontal = if (style == HudDirectionStyle.MINIMAL) 8.dp else 18.dp,
                    vertical = if (style == HudDirectionStyle.MINIMAL) 2.dp else 7.dp
                )
            ) {
                if (targetLhs) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = arrowDesc,
                        tint = accentColor,
                        modifier = Modifier
                            .size(arrowSize)
                            .offset(x = arrowOffsetX)
                    )
                    Text(
                        text = if (isManual) "$targetSide • M" else targetSide,
                        color = accentColor,
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.4.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    Text(
                        text = if (isManual) "$targetSide • M" else targetSide,
                        color = accentColor,
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.4.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = arrowDesc,
                        tint = accentColor,
                        modifier = Modifier
                            .size(arrowSize)
                            .offset(x = arrowOffsetX)
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomotiveLocationLabel(
    locationName: String,
    textColor: Color,
    shadowColor: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = locationName,
        transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
        label = "hud_location_name",
        modifier = modifier
    ) { targetLocation ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size((fontSize.value * 1.1f).dp)
            )
            Text(
                text = targetLocation,
                style = TextStyle(
                    color = textColor,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                    shadow = Shadow(
                        color = shadowColor,
                        offset = Offset.Zero,
                        blurRadius = 10f
                    )
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ScreensaverLiveMiniMapPanel(
    location: GeoPoint?,
    bearing: Float?,
    compassHeading: Float,
    routeData: RouteData?,
    screensaverSettings: HomeScreensaverSettings,
    isHudMirror: Boolean,
    isDarkPalette: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tileUpdate by OsmTileManager.tileUpdateEvent.collectAsStateWithLifecycle()
    val reusableRoutePath = remember { Path() }

    val mapStyle = screensaverSettings.miniMapStyle
    val darkTiles = isHudMirror ||
        mapStyle == MiniMapStyle.DARK ||
        mapStyle == MiniMapStyle.SATELLITE ||
        (mapStyle == MiniMapStyle.STANDARD && isDarkPalette)
    val mapTheme = com.example.ui.map.rememberMapThemeColors(darkTiles)
    val osmMapType = if (mapStyle == MiniMapStyle.SATELLITE) "satellite" else "standard"

    var exploredCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var localZoomOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(screensaverSettings.recenterEpoch) {
        exploredCenter = null
        localZoomOffset = 0f
    }

    val continuousZoom = (screensaverSettings.miniMapZoomLevel + localZoomOffset).coerceIn(
        HomeScreensaverSettings.MIN_ZOOM_LEVEL,
        HomeScreensaverSettings.MAX_ZOOM_LEVEL
    )
    val baseZoom = floor(continuousZoom).toInt().coerceIn(2, 19)
    val fractionalScale = 2.0.pow((continuousZoom - baseZoom).toDouble()).toFloat().coerceIn(0.6f, 2.2f)

    var lastValidLocation by remember { mutableStateOf(location) }
    LaunchedEffect(location) {
        if (location != null) {
            lastValidLocation = location
        }
    }

    val targetAnchor = exploredCenter ?: location ?: lastValidLocation ?: routeData?.bounds?.center ?: GeoPoint(28.6139, 77.2090)

    var startAnchor by remember { mutableStateOf(targetAnchor) }
    var endAnchor by remember { mutableStateOf(targetAnchor) }
    val glideAnim = remember { Animatable(1f) }
    LaunchedEffect(
        targetAnchor.latitude,
        targetAnchor.longitude,
        screensaverSettings.recenterEpoch,
        exploredCenter
    ) {
        if (exploredCenter != null) {
            startAnchor = targetAnchor
            endAnchor = targetAnchor
            glideAnim.snapTo(1f)
        } else if (targetAnchor.latitude != endAnchor.latitude ||
            targetAnchor.longitude != endAnchor.longitude ||
            screensaverSettings.recenterEpoch > 0L
        ) {
            val frac = glideAnim.value.toDouble()
            val curLat = startAnchor.latitude + (endAnchor.latitude - startAnchor.latitude) * frac
            val curLng = startAnchor.longitude + (endAnchor.longitude - startAnchor.longitude) * frac
            startAnchor = GeoPoint(curLat, curLng)
            endAnchor = targetAnchor
            glideAnim.snapTo(0f)
            glideAnim.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing))
        }
    }
    val interpolatedAnchor = GeoPoint(
        latitude = startAnchor.latitude + (endAnchor.latitude - startAnchor.latitude) * glideAnim.value.toDouble(),
        longitude = startAnchor.longitude + (endAnchor.longitude - startAnchor.longitude) * glideAnim.value.toDouble()
    )

    val effectiveBearing = remember(interpolatedAnchor, bearing, routeData?.id) {
        MiniMapRenderer.resolveEffectiveBearing(interpolatedAnchor, bearing, routeData)
    }
    val rawTargetRotationDeg = remember(screensaverSettings.miniMapOrientation, effectiveBearing) {
        MiniMapRenderer.resolveMapRotationDegrees(
            mode = screensaverSettings.miniMapOrientation,
            effectiveBearing = effectiveBearing
        )
    }

    var unwrappedRotation by remember { mutableFloatStateOf(rawTargetRotationDeg) }
    LaunchedEffect(rawTargetRotationDeg) {
        var delta = (rawTargetRotationDeg - unwrappedRotation) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        unwrappedRotation += delta
    }
    val animatedMapRotation by animateFloatAsState(
        targetValue = unwrappedRotation,
        animationSpec = tween(280, easing = FastOutSlowInEasing),
        label = "hud_minimap_rotation"
    )

    val navTransition = rememberInfiniteTransition(label = "hud_map_nav_pulse")
    val navPulse by navTransition.animateFloat(
        initialValue = 0.24f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hud_map_nav_pulse_alpha"
    )

    val outerGlowColor = Color(0xFF38BDF8)
    val routeCoreColor = if (isHudMirror) Color(0xFF7DD3FC) else if (isDarkPalette) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val markerColor = if (isHudMirror) Color(0xFF34D399) else Color(0xFF38BDF8)

    Box(
        modifier = modifier
            .clipToBounds()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val currentAnchor = exploredCenter ?: location ?: lastValidLocation ?: routeData?.bounds?.center ?: GeoPoint(28.6139, 77.2090)
                    val curZ = floor((screensaverSettings.miniMapZoomLevel + localZoomOffset).coerceIn(2f, 19f)).toInt()
                    if (pan.x != 0f || pan.y != 0f) {
                        exploredCenter = OsmTileManager.offsetGeoPointByPixels(
                            center = currentAnchor,
                            deltaPxX = pan.x,
                            deltaPxY = pan.y,
                            zoom = curZ
                        )
                    }
                    if (zoom != 1f) {
                        localZoomOffset = (localZoomOffset + (ln(zoom.toDouble()) / ln(2.0)).toFloat())
                            .coerceIn(
                                HomeScreensaverSettings.MIN_ZOOM_LEVEL - screensaverSettings.miniMapZoomLevel,
                                HomeScreensaverSettings.MAX_ZOOM_LEVEL - screensaverSettings.miniMapZoomLevel
                            )
                    }
                }
            }
            .semantics {
                miniMapRotationDeg = rawTargetRotationDeg
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val _tick = tileUpdate
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val diagPx = ceil(hypot(w.toDouble(), h.toDouble()) / fractionalScale)
                .toInt()
                .coerceIn(64, 1024)

            val osmBmp = OsmTileManager.renderMapBitmap(
                context = context,
                center = interpolatedAnchor,
                widthPx = diagPx,
                heightPx = diagPx,
                zoom = baseZoom,
                panOffsetX = 0f,
                panOffsetY = 0f,
                darkTheme = darkTiles,
                mapType = osmMapType
            )

            drawRect(color = if (isHudMirror) Color(0xFF030712) else mapTheme.mapBackground)

            rotate(degrees = animatedMapRotation, pivot = Offset(cx, cy)) {
                if (osmBmp != null) {
                    scale(scale = fractionalScale, pivot = Offset(cx, cy)) {
                        drawImage(
                            image = osmBmp.asImageBitmap(),
                            dstOffset = IntOffset(
                                x = (cx - diagPx / 2f).toInt(),
                                y = (cy - diagPx / 2f).toInt()
                            ),
                            dstSize = IntSize(diagPx, diagPx),
                            alpha = if (isHudMirror) 0.72f else 0.90f
                        )
                    }
                } else {
                    val gridStep = 44.dp.toPx()
                    val halfSpan = maxOf(w, h)
                    var g = -halfSpan
                    while (g <= halfSpan * 1.5f) {
                        drawLine(
                            color = Color(0xFF38BDF8).copy(alpha = 0.14f),
                            start = Offset(cx + g, cy - halfSpan),
                            end = Offset(cx + g, cy + halfSpan),
                            strokeWidth = 1f
                        )
                        drawLine(
                            color = Color(0xFF38BDF8).copy(alpha = 0.14f),
                            start = Offset(cx - halfSpan, cy + g),
                            end = Offset(cx + halfSpan, cy + g),
                            strokeWidth = 1f
                        )
                        g += gridStep
                    }
                }

                fun toScreen(pt: GeoPoint): Offset {
                    val (sx, sy) = OsmTileManager.geoToScreen(
                        pt = pt,
                        center = interpolatedAnchor,
                        widthPx = w,
                        heightPx = h,
                        zoom = baseZoom,
                        panOffsetX = 0f,
                        panOffsetY = 0f
                    )
                    return Offset(
                        x = cx + (sx - cx) * fractionalScale,
                        y = cy + (sy - cy) * fractionalScale
                    )
                }

                // Actual imported route alignment vector lines (if toggled visible)
                if (screensaverSettings.miniMapShowRouteAlignment &&
                    routeData != null &&
                    routeData.alignments.isNotEmpty()
                ) {
                    for (align in routeData.alignments) {
                        if (align.points.size < 2) continue
                        reusableRoutePath.reset()
                        var started = false
                        for (pt in align.points) {
                            val off = toScreen(pt)
                            if (!started) {
                                reusableRoutePath.moveTo(off.x, off.y)
                                started = true
                            } else {
                                reusableRoutePath.lineTo(off.x, off.y)
                            }
                        }
                        drawPath(
                            path = reusableRoutePath,
                            color = outerGlowColor.copy(alpha = 0.30f),
                            style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                        drawPath(
                            path = reusableRoutePath,
                            color = routeCoreColor,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }

                // Live / retained vehicle marker
                val markerGeo = location ?: lastValidLocation
                if (markerGeo != null) {
                    val userOff = toScreen(markerGeo)
                    drawCircle(
                        color = markerColor.copy(alpha = navPulse * 0.65f),
                        radius = 15.dp.toPx(),
                        center = userOff
                    )
                    val activeHeading = effectiveBearing ?: compassHeading
                    when (screensaverSettings.miniMapMarkerStyle) {
                        HudMapMarkerStyle.NAVIGATION_ARROW -> {
                            rotate(degrees = activeHeading, pivot = userOff) {
                                reusableRoutePath.reset()
                                reusableRoutePath.moveTo(userOff.x, userOff.y - 11.dp.toPx())
                                reusableRoutePath.lineTo(userOff.x - 6.dp.toPx(), userOff.y + 6.dp.toPx())
                                reusableRoutePath.lineTo(userOff.x, userOff.y + 2.5.dp.toPx())
                                reusableRoutePath.lineTo(userOff.x + 6.dp.toPx(), userOff.y + 6.dp.toPx())
                                reusableRoutePath.close()
                                drawPath(path = reusableRoutePath, color = markerColor)
                            }
                            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = userOff)
                            drawCircle(color = markerColor, radius = 2.4.dp.toPx(), center = userOff)
                        }
                        HudMapMarkerStyle.PULSE_DOT -> {
                            drawCircle(color = Color.White, radius = 6.5.dp.toPx(), center = userOff)
                            drawCircle(color = markerColor, radius = 4.2.dp.toPx(), center = userOff)
                        }
                        HudMapMarkerStyle.CLASSIC_PIN -> {
                            drawCircle(color = Color.White, radius = 7.dp.toPx(), center = userOff)
                            drawCircle(color = Color(0xFFEF4444), radius = 4.5.dp.toPx(), center = userOff)
                        }
                    }
                }
            }
        }

        // Subtle North orientation pip top-right (non-interactive pure instrument indicator)
        Surface(
            shape = CircleShape,
            color = if (isDarkPalette || isHudMirror) Color(0xFF020617).copy(alpha = 0.52f) else Color.White.copy(alpha = 0.85f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(26.dp)
                .graphicsLayer { rotationZ = animatedMapRotation }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "N",
                    color = if (isDarkPalette || isHudMirror) Color(0xFF38BDF8) else Color(0xFF0284C7),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
    }
}
