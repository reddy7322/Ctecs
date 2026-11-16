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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
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
import com.example.model.MiniMapStyle
import com.example.model.RouteData
import com.example.model.ScreensaverDisplayMode
import com.example.overlay.MiniMapRenderer
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
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
    isMirrorMode: Boolean
): HudClusterPalette {
    return when {
        isMirrorMode -> HudClusterPalette(
            rootBackground = Color(0xFF000000),
            ambientGlowTop = Color(0xFF0284C7).copy(alpha = 0.08f),
            panelBackground = Color(0xFF030712).copy(alpha = 0.88f),
            panelBorder = Color(0xFF38BDF8).copy(alpha = 0.48f),
            primaryDigitColor = Color(0xFFFFFFFF),
            chainageGlowColor = Color(0xFF38BDF8).copy(alpha = 0.45f),
            secondaryTextColor = Color(0xFFE0F2FE),
            speedArcTrack = Color(0xFF1E293B),
            speedArcStart = Color(0xFF06B6D4),
            speedArcEnd = Color(0xFF34D399),
            needleColor = Color(0xFF38BDF8),
            isDark = true,
            isHudMirror = true
        )
        useDarkInstrument -> HudClusterPalette(
            rootBackground = Color(0xFF040814),
            ambientGlowTop = Color(0xFF0EA5E9).copy(alpha = 0.16f),
            panelBackground = Color(0xFF0F172A).copy(alpha = 0.68f),
            panelBorder = Color(0xFF38BDF8).copy(alpha = 0.28f),
            primaryDigitColor = Color(0xFFF8FAFC),
            chainageGlowColor = Color(0xFF38BDF8).copy(alpha = 0.30f),
            secondaryTextColor = Color(0xFFE2E8F0),
            speedArcTrack = Color(0xFF1E293B),
            speedArcStart = Color(0xFF0EA5E9),
            speedArcEnd = Color(0xFF34D399),
            needleColor = Color(0xFF38BDF8),
            isDark = true,
            isHudMirror = false
        )
        else -> HudClusterPalette(
            rootBackground = Color(0xFF0A1120),
            ambientGlowTop = Color(0xFF38BDF8).copy(alpha = 0.14f),
            panelBackground = Color(0xFF111C33).copy(alpha = 0.72f),
            panelBorder = Color(0xFF7DD3FC).copy(alpha = 0.28f),
            primaryDigitColor = Color(0xFFF8FAFC),
            chainageGlowColor = Color(0xFF38BDF8).copy(alpha = 0.28f),
            secondaryTextColor = Color(0xFFE2E8F0),
            speedArcTrack = Color(0xFF1E293B),
            speedArcStart = Color(0xFF0284C7),
            speedArcEnd = Color(0xFF10B981),
            needleColor = Color(0xFF38BDF8),
            isDark = true,
            isHudMirror = false
        )
    }
}

/**
 * Renders the redesigned Automotive Instrument Cluster & Windshield Mirror HUD Screensaver.
 * Strictly button-free inside the screensaver; touching anywhere dismisses cleanly.
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
    screensaverSettings: HomeScreensaverSettings
) {
    val isMirrorMode = screensaverSettings.isMirrorMode
    val isLandscapeMode = screensaverSettings.isLandscapeLayout
    val palette = remember(isDarkAppTheme, isMirrorMode, mapThemePreference) {
        resolveHudClusterPalette(
            useDarkInstrument = isDarkAppTheme ||
                com.example.ui.map.MapThemeConfig.resolveIsDark(mapThemePreference, isDarkAppTheme),
            isMirrorMode = isMirrorMode
        )
    }
    val hudAlpha = if (isMirrorMode) {
        screensaverSettings.hudBrightness.coerceIn(
            HomeScreensaverSettings.MIN_HUD_BRIGHTNESS,
            HomeScreensaverSettings.MAX_HUD_BRIGHTNESS
        )
    } else {
        1f
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val isWindowPortrait = maxWidth < maxHeight
        val rotationDegrees = if (isLandscapeMode && isWindowPortrait) 90f else 0f

        val renderCompleteDashboard: @Composable () -> Unit = {
            val scaleXFactor = if (isMirrorMode) -1f else 1f
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.rootBackground)
                    .testTag("screensaver_dashboard_root")
                    .semantics {
                        dashboardMirrorScaleX = scaleXFactor
                        dashboardHudBrightness = hudAlpha
                        dashboardLandscapeRotation = rotationDegrees
                        screensaverDisplayMode = screensaverSettings.displayMode.name
                    }
                    .graphicsLayer {
                        scaleX = scaleXFactor
                    }
            ) {
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

                    if (isLandscapeMode) {
                        LandscapeAutomotiveCluster(
                            displayState = displayState,
                            location = location,
                            bearing = bearing,
                            compassHeading = compassHeading,
                            routeData = routeData,
                            screensaverSettings = screensaverSettings,
                            palette = palette,
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
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
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                        )
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
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("screensaver_portrait_layout")
    ) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val charCount = displayState.chainageText.length.coerceAtLeast(6)
        val availWidth = (maxWidth - 20.dp).coerceAtLeast(180.dp)
        val maxByW = (availWidth.value / (charCount * 0.63f * fontScale))
        val maxByH = maxHeight.value * 0.14f
        val chainageFontSize = maxByW.coerceIn(34f, maxByH.coerceAtLeast(40f)).sp
        val dialSize = minOf(maxWidth * 0.46f, maxHeight * 0.25f).coerceIn(112.dp, 196.dp)
        val mapVisible = screensaverSettings.miniMapVisible
        val gap = (maxHeight * 0.02f).coerceIn(8.dp, 18.dp)

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (mapVisible) Arrangement.SpaceBetween else Arrangement.Center
        ) {
            // Top-Center Hero Zone: Live Chainage + Animated LHS/RHS + Location Name
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = palette.panelBackground,
                    border = BorderStroke(1.dp, palette.panelBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)
                    ) {
                        AutomotiveOdometerChainage(
                            chainageText = displayState.chainageText,
                            isIncreasing = displayState.isIncreasingTrend,
                            textColor = palette.primaryDigitColor,
                            glowColor = palette.chainageGlowColor,
                            fontSize = chainageFontSize,
                            lineHeight = (chainageFontSize.value * 1.06f).sp,
                            modifier = Modifier
                                .testTag("screensaver_chainage_text")
                                .clearAndSetSemantics {
                                    text = AnnotatedString(displayState.chainageText)
                                    contentDescription = displayState.chainageText
                                }
                        )

                        AnimatedVisibility(
                            visible = displayState.reliableSide != null,
                            enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.92f, animationSpec = tween(240)),
                            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f, animationSpec = tween(180))
                        ) {
                            val side = displayState.reliableSide
                            if (side != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    AutomotiveDirectionBadge(
                                        side = side,
                                        isHudMirror = palette.isHudMirror,
                                        fontSize = (chainageFontSize.value * 0.28f).coerceIn(14f, 20f).sp,
                                        arrowSize = 18.dp,
                                        modifier = Modifier.testTag("screensaver_direction_badge")
                                    )
                                }
                            }
                        }

                        AnimatedVisibility(
                            visible = displayState.locationName.isNotBlank(),
                            enter = fadeIn(tween(260)),
                            exit = fadeOut(tween(180))
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(8.dp))
                                AutomotiveLocationLabel(
                                    locationName = displayState.locationName,
                                    textColor = palette.secondaryTextColor,
                                    shadowColor = palette.chainageGlowColor,
                                    fontSize = 14.sp,
                                    modifier = Modifier.testTag("screensaver_location_name")
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(gap))

            // Center-Lower Gauge Zone: Automotive Circular Digital Speedometer
            AutomotiveCircularSpeedometer(
                speedKmh = displayState.speedKmh,
                speedText = displayState.speedText,
                palette = palette,
                dialSize = dialSize
            )

            // Bottom Zone: Shared Live Mini Map Panel
            if (mapVisible) {
                Spacer(modifier = Modifier.height(gap))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = palette.panelBackground,
                    border = BorderStroke(1.dp, palette.panelBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    ScreensaverLiveMiniMapPanel(
                        location = location,
                        bearing = bearing,
                        compassHeading = compassHeading,
                        routeData = routeData,
                        screensaverSettings = screensaverSettings,
                        isHudMirror = palette.isHudMirror,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("screensaver_glass_map")
                    )
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
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .testTag("screensaver_landscape_layout")
    ) {
        val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        val mapVisible = screensaverSettings.miniMapVisible
        val centerWeight = if (mapVisible) 0.42f else 0.62f
        val leftWeight = if (mapVisible) 0.27f else 0.38f
        val centerAvailW = (maxWidth * centerWeight) - 24.dp
        val charCount = displayState.chainageText.length.coerceAtLeast(6)
        val maxByW = (centerAvailW.value / (charCount * 0.63f * fontScale))
        val maxByH = maxHeight.value * 0.28f
        val chainageFontSize = maxByW.coerceIn(32f, maxByH.coerceAtLeast(38f)).sp
        val dialSize = minOf(maxWidth * leftWeight - 12.dp, maxHeight * 0.78f).coerceIn(110.dp, 210.dp)

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // LEFT CLUSTER: Circular Automotive Speedometer
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = palette.panelBackground,
                border = BorderStroke(1.dp, palette.panelBorder),
                modifier = Modifier
                    .weight(leftWeight)
                    .fillMaxHeight()
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(8.dp)
                ) {
                    AutomotiveCircularSpeedometer(
                        speedKmh = displayState.speedKmh,
                        speedText = displayState.speedText,
                        palette = palette,
                        dialSize = dialSize
                    )
                }
            }

            // CENTER HERO CLUSTER: Large Odometer Chainage + Animated LHS/RHS + Location Name
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = palette.panelBackground,
                border = BorderStroke(1.dp, palette.panelBorder),
                modifier = Modifier
                    .weight(centerWeight)
                    .fillMaxHeight()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    AutomotiveOdometerChainage(
                        chainageText = displayState.chainageText,
                        isIncreasing = displayState.isIncreasingTrend,
                        textColor = palette.primaryDigitColor,
                        glowColor = palette.chainageGlowColor,
                        fontSize = chainageFontSize,
                        lineHeight = (chainageFontSize.value * 1.06f).sp,
                        modifier = Modifier
                            .testTag("screensaver_chainage_text")
                            .clearAndSetSemantics {
                                text = AnnotatedString(displayState.chainageText)
                                contentDescription = displayState.chainageText
                            }
                    )

                    AnimatedVisibility(
                        visible = displayState.reliableSide != null,
                        enter = fadeIn(tween(240)) + scaleIn(initialScale = 0.92f, animationSpec = tween(240)),
                        exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.92f, animationSpec = tween(180))
                    ) {
                        val side = displayState.reliableSide
                        if (side != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(12.dp))
                                AutomotiveDirectionBadge(
                                    side = side,
                                    isHudMirror = palette.isHudMirror,
                                    fontSize = (chainageFontSize.value * 0.28f).coerceIn(14f, 20f).sp,
                                    arrowSize = 18.dp,
                                    modifier = Modifier.testTag("screensaver_direction_badge")
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = displayState.locationName.isNotBlank(),
                        enter = fadeIn(tween(260)),
                        exit = fadeOut(tween(180))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(modifier = Modifier.height(10.dp))
                            AutomotiveLocationLabel(
                                locationName = displayState.locationName,
                                textColor = palette.secondaryTextColor,
                                shadowColor = palette.chainageGlowColor,
                                fontSize = 14.sp,
                                modifier = Modifier.testTag("screensaver_location_name")
                            )
                        }
                    }
                }
            }

            // RIGHT CLUSTER: Live Mini Map Panel (strictly synced to shared settings)
            if (mapVisible) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = palette.panelBackground,
                    border = BorderStroke(1.dp, palette.panelBorder),
                    modifier = Modifier
                        .weight(0.31f)
                        .fillMaxHeight()
                ) {
                    ScreensaverLiveMiniMapPanel(
                        location = location,
                        bearing = bearing,
                        compassHeading = compassHeading,
                        routeData = routeData,
                        screensaverSettings = screensaverSettings,
                        isHudMirror = palette.isHudMirror,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("screensaver_glass_map")
                    )
                }
            }
        }
    }
}

@Composable
private fun AutomotiveCircularSpeedometer(
    speedKmh: Int?,
    speedText: String,
    palette: HudClusterPalette,
    dialSize: Dp
) {
    val isLive = speedKmh != null
    val targetSpeed = (speedKmh ?: 0).coerceIn(0, 260).toFloat()
    val animatedSpeed by animateFloatAsState(
        targetValue = targetSpeed,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "hud_speedometer_needle"
    )
    val sweepFraction = (animatedSpeed / 160f).coerceIn(0f, 1f)
    val startAngle = 150f
    val totalSweep = 240f
    val activeAngle = startAngle + totalSweep * sweepFraction

    val digitalNumber = speedKmh?.toString() ?: "--"
    val digitalFontSize = (dialSize.value * 0.25f).coerceIn(22f, 44f).sp
    val unitFontSize = (dialSize.value * 0.085f).coerceIn(10f, 14f).sp

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(dialSize)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeMain = size.minDimension * 0.078f
            val inset = strokeMain * 1.15f
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val cx = size.width / 2f
            val cy = size.height / 2f
            val outerR = (size.minDimension / 2f) - inset * 0.55f

            // Subtle dark instrument inner backdrop
            drawCircle(
                color = Color(0xFF020617).copy(alpha = if (palette.isHudMirror) 0.92f else 0.65f),
                radius = outerR,
                center = Offset(cx, cy)
            )

            // Outer hairline rim
            drawCircle(
                color = palette.panelBorder.copy(alpha = 0.40f),
                radius = outerR,
                center = Offset(cx, cy),
                style = Stroke(width = 1.4.dp.toPx())
            )

            // Background sweep track
            drawArc(
                color = palette.speedArcTrack,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeMain, cap = StrokeCap.Round)
            )

            // Graduated automotive tachometer ticks (every 10 km/h across 0..160)
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

            // Luminous active speed arc
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

            // Precision Needle when live speed exists
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

        // Center digital readout inside dial bezel
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
            Text(
                text = digitalNumber,
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
    modifier: Modifier = Modifier
) {
    val style = TextStyle(
        color = textColor,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1.1).sp,
        fontFeatureSettings = "tnum",
        shadow = Shadow(
            color = glowColor,
            offset = Offset.Zero,
            blurRadius = 22f
        )
    )

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        val len = chainageText.length
        for (i in 0 until len) {
            val ch = chainageText[i]
            val slotKey = len - i
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val enterOffset = { fullHeight: Int ->
                        if (isIncreasing) fullHeight / 3 else -fullHeight / 3
                    }
                    val exitOffset = { fullHeight: Int ->
                        if (isIncreasing) -fullHeight / 3 else fullHeight / 3
                    }
                    (slideInVertically(tween(210, easing = FastOutSlowInEasing), enterOffset) +
                        fadeIn(tween(190))) togetherWith
                        (slideOutVertically(tween(170), exitOffset) +
                            fadeOut(tween(150)))
                },
                label = "hud_odometer_digit_$slotKey"
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
    isHudMirror: Boolean,
    fontSize: TextUnit,
    arrowSize: Dp,
    modifier: Modifier = Modifier
) {
    val isLhs = side == "LHS"
    val accentColor by animateColorAsState(
        targetValue = if (isLhs) Color(0xFF34D399) else Color(0xFF38BDF8),
        animationSpec = tween(250),
        label = "hud_direction_accent"
    )
    val glassBg = if (isHudMirror) {
        Color(0xFF000000).copy(alpha = 0.85f)
    } else {
        Color(0xFF0F172A).copy(alpha = 0.70f)
    }

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
    val arrowOffsetX = (arrowPulse * (if (isLhs) -2.8f else 2.8f)).dp

    Surface(
        shape = CircleShape,
        color = glassBg,
        border = BorderStroke(1.2.dp, accentColor.copy(alpha = 0.58f)),
        shadowElevation = 0.dp,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = side,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220))) togetherWith
                    (fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)))
            },
            label = "hud_direction_side"
        ) { targetSide ->
            val targetLhs = targetSide == "LHS"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 7.dp)
            ) {
                if (targetLhs) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Increasing",
                        tint = accentColor,
                        modifier = Modifier
                            .size(arrowSize)
                            .offset(x = arrowOffsetX)
                    )
                    Text(
                        text = targetSide,
                        color = accentColor,
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.8.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    Text(
                        text = targetSide,
                        color = accentColor,
                        fontSize = fontSize,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.8.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Decreasing",
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val tileUpdate by OsmTileManager.tileUpdateEvent.collectAsStateWithLifecycle()
    val reusableRoutePath = remember { Path() }

    val mapStyle = screensaverSettings.miniMapStyle
    val darkTiles = isHudMirror || mapStyle == MiniMapStyle.DARK || mapStyle == MiniMapStyle.SATELLITE
    val mapTheme = com.example.ui.map.rememberMapThemeColors(darkTiles)
    val osmMapType = if (mapStyle == MiniMapStyle.SATELLITE) "satellite" else "standard"

    val continuousZoom = screensaverSettings.miniMapZoomLevel.coerceIn(
        HomeScreensaverSettings.MIN_ZOOM_LEVEL,
        HomeScreensaverSettings.MAX_ZOOM_LEVEL
    )
    val baseZoom = floor(continuousZoom).toInt().coerceIn(12, 18)
    val fractionalScale = 2.0.pow((continuousZoom - baseZoom).toDouble()).toFloat().coerceIn(0.6f, 2.2f)

    // Retain last known coordinate when GPS drops briefly
    var lastValidLocation by remember { mutableStateOf(location) }
    LaunchedEffect(location) {
        if (location != null) {
            lastValidLocation = location
        }
    }

    val targetAnchor = location ?: lastValidLocation ?: routeData?.bounds?.center ?: GeoPoint(28.6139, 77.2090)

    var startAnchor by remember { mutableStateOf(targetAnchor) }
    var endAnchor by remember { mutableStateOf(targetAnchor) }
    val glideAnim = remember { Animatable(1f) }
    LaunchedEffect(
        targetAnchor.latitude,
        targetAnchor.longitude,
        screensaverSettings.recenterEpoch
    ) {
        if (targetAnchor.latitude != endAnchor.latitude ||
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

    // Shortest-path angular unwrap so North Up / Direction Up / Auto rotate cleanly
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
    val routeCoreColor = if (isHudMirror) Color(0xFF7DD3FC) else Color(0xFF38BDF8)
    val markerColor = if (isHudMirror) Color(0xFF34D399) else Color(0xFF38BDF8)

    Box(
        modifier = modifier
            .clipToBounds()
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
                .coerceIn(64, 768)

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
                    // Apply fractional scale around viewport center to align vector route cleanly with tiles
                    return Offset(
                        x = cx + (sx - cx) * fractionalScale,
                        y = cy + (sy - cy) * fractionalScale
                    )
                }

                // Actual imported route alignment vector lines
                if (routeData != null && routeData.alignments.isNotEmpty()) {
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

                // Live / retained vehicle marker + reliable heading cone
                val markerGeo = location ?: lastValidLocation
                if (markerGeo != null) {
                    val userOff = toScreen(markerGeo)
                    drawCircle(
                        color = markerColor.copy(alpha = navPulse * 0.65f),
                        radius = 15.dp.toPx(),
                        center = userOff
                    )
                    val activeHeading = effectiveBearing ?: compassHeading
                    rotate(degrees = activeHeading, pivot = userOff) {
                        reusableRoutePath.reset()
                        reusableRoutePath.moveTo(userOff.x, userOff.y - 11.dp.toPx())
                        reusableRoutePath.lineTo(userOff.x - 6.dp.toPx(), userOff.y + 6.dp.toPx())
                        reusableRoutePath.lineTo(userOff.x, userOff.y + 2.5.dp.toPx())
                        reusableRoutePath.lineTo(userOff.x + 6.dp.toPx(), userOff.y + 6.dp.toPx())
                        reusableRoutePath.close()
                        drawPath(path = reusableRoutePath, color = markerColor)
                    }
                    drawCircle(
                        color = Color.White,
                        radius = 4.dp.toPx(),
                        center = userOff
                    )
                    drawCircle(
                        color = markerColor,
                        radius = 2.4.dp.toPx(),
                        center = userOff
                    )
                }
            }
        }

        // Subtle North orientation pip top-right (non-interactive pure instrument indicator)
        Surface(
            shape = CircleShape,
            color = Color(0xFF020617).copy(alpha = 0.72f),
            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(26.dp)
                .graphicsLayer { rotationZ = animatedMapRotation }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "N",
                    color = Color(0xFF38BDF8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }
        }
    }
}
