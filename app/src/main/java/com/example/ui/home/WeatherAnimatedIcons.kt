package com.example.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.WeatherConditionType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Smooth, battery-efficient original animated weather illustrations.
 *
 * Covers Sunny, Partly Cloudy, Cloudy/Overcast, Light & Heavy Rain, Thunderstorm,
 * Fog/Mist, Snow, Clear Night, Cloudy Night, and Windy conditions.
 *
 * When [isAnimating] is false (off-screen, lifecycle paused, Screensaver/AOD active,
 * or user disabled animations in settings), no infinite animation clock runs — cleanly
 * falling back to crisp static weather illustrations.
 */
@Composable
fun AnimatedWeatherIcon(
    condition: WeatherConditionType,
    isDark: Boolean,
    isAnimating: Boolean = true,
    size: Dp = 52.dp,
    modifier: Modifier = Modifier,
    isHeavyRain: Boolean = false
) {
    AnimatedContent(
        targetState = condition to isHeavyRain,
        transitionSpec = {
            (fadeIn(tween(300)) + scaleIn(initialScale = 0.92f, animationSpec = tween(300))) togetherWith
                fadeOut(tween(200))
        },
        label = "weather_condition_icon_transition",
        modifier = modifier
    ) { (targetCondition, heavyRain) ->
        val slowPhase: Float
        val fastPhase: Float
        val pulsePhase: Float

        if (isAnimating) {
            val transition = rememberInfiniteTransition(label = "weather_anim_${targetCondition.name}")
            val s by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 12_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "weather_slow_phase"
            )
            val f by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = if (heavyRain) 1_100 else 1_800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "weather_fast_phase"
            )
            val p by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 2_800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "weather_pulse_phase"
            )
            slowPhase = s
            fastPhase = f
            pulsePhase = p
        } else {
            slowPhase = 0.15f
            fastPhase = 0.35f
            pulsePhase = 0.5f
        }

        val semanticsLabel = if (targetCondition == WeatherConditionType.RAINY && heavyRain) {
            "Heavy Rain"
        } else {
            targetCondition.defaultLabel
        }

        Canvas(
            modifier = Modifier
                .size(size)
                .semantics { contentDescription = semanticsLabel }
        ) {
            when (targetCondition) {
                WeatherConditionType.SUNNY -> drawSunny(slowPhase, pulsePhase)
                WeatherConditionType.PARTLY_CLOUDY -> drawPartlyCloudy(slowPhase, pulsePhase, isDark)
                WeatherConditionType.CLOUDY -> drawCloudy(pulsePhase, isDark)
                WeatherConditionType.RAINY -> drawRainy(fastPhase, pulsePhase, isDark, heavyRain)
                WeatherConditionType.THUNDERSTORM -> drawThunderstorm(fastPhase, pulsePhase, isDark)
                WeatherConditionType.FOGGY -> drawFoggy(pulsePhase, isDark)
                WeatherConditionType.NIGHT -> drawNight(pulsePhase, slowPhase)
                WeatherConditionType.SNOWY -> drawSnowy(fastPhase, pulsePhase, isDark)
                WeatherConditionType.CLOUDY_NIGHT -> drawCloudyNight(pulsePhase, slowPhase, isDark)
                WeatherConditionType.WINDY -> drawWindy(fastPhase, pulsePhase, isDark)
            }
        }
    }
}

private fun DrawScope.drawSunny(slowPhase: Float, pulsePhase: Float) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val r = size.minDimension * 0.24f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFD54F).copy(alpha = 0.42f + 0.14f * pulsePhase),
                Color(0xFFFFB300).copy(alpha = 0.14f),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = r * (2.15f + 0.18f * pulsePhase)
        ),
        radius = r * (2.15f + 0.18f * pulsePhase),
        center = Offset(cx, cy)
    )

    rotate(degrees = slowPhase * 360f, pivot = Offset(cx, cy)) {
        val rayCount = 8
        val innerR = r * 1.30f
        val outerR = r * (1.68f + 0.10f * pulsePhase)
        for (i in 0 until rayCount) {
            val angleRad = (i * (360.0 / rayCount)) * PI / 180.0
            val start = Offset(
                cx + (cos(angleRad) * innerR).toFloat(),
                cy + (sin(angleRad) * innerR).toFloat()
            )
            val end = Offset(
                cx + (cos(angleRad) * outerR).toFloat(),
                cy + (sin(angleRad) * outerR).toFloat()
            )
            drawLine(
                color = Color(0xFFFFC107),
                start = start,
                end = end,
                strokeWidth = size.minDimension * 0.055f,
                cap = StrokeCap.Round
            )
        }
    }

    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFFE082), Color(0xFFFFA000)),
            start = Offset(cx - r, cy - r),
            end = Offset(cx + r, cy + r)
        ),
        radius = r,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawPartlyCloudy(slowPhase: Float, pulsePhase: Float, isDark: Boolean) {
    val sunCx = size.width * 0.62f
    val sunCy = size.height * 0.36f
    val sunR = size.minDimension * 0.20f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFFD54F).copy(alpha = 0.38f + 0.12f * pulsePhase),
                Color.Transparent
            ),
            center = Offset(sunCx, sunCy),
            radius = sunR * 2.0f
        ),
        radius = sunR * 2.0f,
        center = Offset(sunCx, sunCy)
    )
    rotate(degrees = slowPhase * 180f, pivot = Offset(sunCx, sunCy)) {
        for (i in 0 until 6) {
            val a = i * 60.0 * PI / 180.0
            drawLine(
                color = Color(0xFFFFCA28).copy(alpha = 0.85f),
                start = Offset(sunCx + (cos(a) * sunR * 1.25f).toFloat(), sunCy + (sin(a) * sunR * 1.25f).toFloat()),
                end = Offset(sunCx + (cos(a) * sunR * 1.56f).toFloat(), sunCy + (sin(a) * sunR * 1.56f).toFloat()),
                strokeWidth = size.minDimension * 0.042f,
                cap = StrokeCap.Round
            )
        }
    }
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFFE57F), Color(0xFFFF9800)),
            start = Offset(sunCx - sunR, sunCy - sunR),
            end = Offset(sunCx + sunR, sunCy + sunR)
        ),
        radius = sunR,
        center = Offset(sunCx, sunCy)
    )

    val driftX = (pulsePhase - 0.5f) * size.width * 0.08f
    val cloudTop = if (isDark) Color(0xFFF1F5F9) else Color(0xFFFFFFFF)
    val cloudBot = if (isDark) Color(0xFFCBD5E1) else Color(0xFFDCE6F2)
    drawSoftCloud(
        centerX = size.width * 0.46f + driftX,
        baseY = size.height * 0.72f,
        width = size.width * 0.66f,
        topColor = cloudTop,
        bottomColor = cloudBot
    )
}

private fun DrawScope.drawCloudy(pulsePhase: Float, isDark: Boolean) {
    val driftBack = (0.5f - pulsePhase) * size.width * 0.07f
    val driftFront = (pulsePhase - 0.5f) * size.width * 0.09f

    drawSoftCloud(
        centerX = size.width * 0.58f + driftBack,
        baseY = size.height * 0.54f,
        width = size.width * 0.56f,
        topColor = if (isDark) Color(0xFF94A3B8) else Color(0xFFCBD5E1),
        bottomColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
    )
    drawSoftCloud(
        centerX = size.width * 0.45f + driftFront,
        baseY = size.height * 0.72f,
        width = size.width * 0.70f,
        topColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFFF8FAFC),
        bottomColor = if (isDark) Color(0xFF94A3B8) else Color(0xFFCBD5E1)
    )
}

private fun DrawScope.drawRainy(fastPhase: Float, pulsePhase: Float, isDark: Boolean, isHeavy: Boolean) {
    val drift = (pulsePhase - 0.5f) * size.width * 0.04f
    val dropColor = if (isHeavy) {
        if (isDark) Color(0xFF0EA5E9) else Color(0xFF0369A1)
    } else {
        if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    }

    val xs = if (isHeavy) listOf(0.24f, 0.36f, 0.48f, 0.60f, 0.72f) else listOf(0.30f, 0.44f, 0.58f, 0.70f)
    val offsets = if (isHeavy) listOf(0.0f, 0.35f, 0.15f, 0.65f, 0.45f) else listOf(0.0f, 0.45f, 0.20f, 0.72f)
    for (i in xs.indices) {
        val progress = (fastPhase + offsets[i]) % 1f
        val startX = size.width * xs[i] - progress * size.width * (if (isHeavy) 0.08f else 0.06f)
        val startY = size.height * (0.56f + progress * 0.31f)
        val dropLen = size.height * (if (isHeavy) 0.17f else 0.13f)
        val alpha = (1f - kotlin.math.abs(progress - 0.5f) * 1.6f).coerceIn(0.20f, 0.96f)
        drawLine(
            color = dropColor.copy(alpha = alpha),
            start = Offset(startX, startY),
            end = Offset(startX - dropLen * 0.24f, startY + dropLen),
            strokeWidth = size.minDimension * (if (isHeavy) 0.058f else 0.05f),
            cap = StrokeCap.Round
        )
    }

    drawSoftCloud(
        centerX = size.width * 0.50f + drift,
        baseY = size.height * 0.56f,
        width = size.width * 0.70f,
        topColor = if (isHeavy) Color(0xFF94A3B8) else (if (isDark) Color(0xFFCBD5E1) else Color(0xFFE2E8F0)),
        bottomColor = if (isHeavy) Color(0xFF475569) else (if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8))
    )
}

private fun DrawScope.drawThunderstorm(fastPhase: Float, pulsePhase: Float, isDark: Boolean) {
    val drift = (pulsePhase - 0.5f) * size.width * 0.03f
    val dropColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)

    for ((idx, xFrac) in listOf(0.28f, 0.72f).withIndex()) {
        val p = (fastPhase + idx * 0.5f) % 1f
        val x = size.width * xFrac - p * size.width * 0.05f
        val y = size.height * (0.58f + p * 0.28f)
        drawLine(
            color = dropColor.copy(alpha = (1f - p).coerceIn(0.2f, 0.9f)),
            start = Offset(x, y),
            end = Offset(x - size.width * 0.03f, y + size.height * 0.12f),
            strokeWidth = size.minDimension * 0.045f,
            cap = StrokeCap.Round
        )
    }

    val flashAlpha = when {
        pulsePhase > 0.78f -> ((pulsePhase - 0.78f) / 0.22f).coerceIn(0.45f, 1.0f)
        else -> 0.42f
    }
    val boltPath = Path().apply {
        moveTo(size.width * 0.53f, size.height * 0.50f)
        lineTo(size.width * 0.43f, size.height * 0.70f)
        lineTo(size.width * 0.52f, size.height * 0.70f)
        lineTo(size.width * 0.46f, size.height * 0.90f)
        lineTo(size.width * 0.61f, size.height * 0.65f)
        lineTo(size.width * 0.52f, size.height * 0.65f)
        close()
    }
    drawPath(
        path = boltPath,
        color = Color(0xFFFACC15).copy(alpha = flashAlpha)
    )

    drawSoftCloud(
        centerX = size.width * 0.50f + drift,
        baseY = size.height * 0.54f,
        width = size.width * 0.72f,
        topColor = Color(0xFF94A3B8),
        bottomColor = if (isDark) Color(0xFF475569) else Color(0xFF64748B)
    )
}

private fun DrawScope.drawFoggy(pulsePhase: Float, isDark: Boolean) {
    val mistColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF64748B)
    drawSoftCloud(
        centerX = size.width * 0.50f,
        baseY = size.height * 0.48f,
        width = size.width * 0.62f,
        topColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFFE2E8F0),
        bottomColor = if (isDark) Color(0xFF94A3B8) else Color(0xFFCBD5E1)
    )

    val ys = listOf(0.58f, 0.70f, 0.82f)
    val widths = listOf(0.68f, 0.56f, 0.46f)
    for (i in ys.indices) {
        val dir = if (i % 2 == 0) 1f else -1f
        val shift = (pulsePhase - 0.5f) * size.width * 0.12f * dir
        val barW = size.width * widths[i]
        val barH = size.height * 0.065f
        val left = (size.width - barW) * 0.5f + shift
        val top = size.height * ys[i]
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    mistColor.copy(alpha = 0.65f - i * 0.10f),
                    Color.Transparent
                ),
                startX = left,
                endX = left + barW
            ),
            topLeft = Offset(left, top),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barH * 0.5f, barH * 0.5f)
        )
    }
}

private fun DrawScope.drawNight(pulsePhase: Float, slowPhase: Float) {
    val cx = size.width * 0.48f
    val cy = size.height * 0.52f
    val r = size.minDimension * 0.26f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF818CF8).copy(alpha = 0.30f + 0.12f * pulsePhase),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = r * 2.1f
        ),
        radius = r * 2.1f,
        center = Offset(cx, cy)
    )

    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFEF08A), Color(0xFFEAB308)),
            start = Offset(cx - r, cy - r),
            end = Offset(cx + r, cy + r)
        ),
        radius = r,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = Color(0xFF1E293B).copy(alpha = 0.88f),
        radius = r * 0.82f,
        center = Offset(cx + r * 0.46f, cy - r * 0.28f)
    )

    val stars = listOf(
        Triple(0.76f, 0.26f, pulsePhase),
        Triple(0.84f, 0.48f, 1f - pulsePhase),
        Triple(0.24f, 0.28f, ((slowPhase * 2f) % 1f))
    )
    for ((sx, sy, twinkle) in stars) {
        val starR = size.minDimension * (0.032f + 0.022f * twinkle)
        val starCenter = Offset(size.width * sx, size.height * sy)
        val starColor = Color(0xFFFEF9C3).copy(alpha = 0.45f + 0.55f * twinkle)
        drawLine(
            color = starColor,
            start = Offset(starCenter.x - starR, starCenter.y),
            end = Offset(starCenter.x + starR, starCenter.y),
            strokeWidth = size.minDimension * 0.025f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = starColor,
            start = Offset(starCenter.x, starCenter.y - starR),
            end = Offset(starCenter.x, starCenter.y + starR),
            strokeWidth = size.minDimension * 0.025f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawCloudyNight(pulsePhase: Float, slowPhase: Float, isDark: Boolean) {
    val cx = size.width * 0.64f
    val cy = size.height * 0.36f
    val r = size.minDimension * 0.19f

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF818CF8).copy(alpha = 0.28f + 0.10f * pulsePhase),
                Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = r * 1.9f
        ),
        radius = r * 1.9f,
        center = Offset(cx, cy)
    )
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFEF08A), Color(0xFFEAB308)),
            start = Offset(cx - r, cy - r),
            end = Offset(cx + r, cy + r)
        ),
        radius = r,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = if (isDark) Color(0xFF0F172A).copy(alpha = 0.90f) else Color(0xFF334155).copy(alpha = 0.86f),
        radius = r * 0.78f,
        center = Offset(cx + r * 0.44f, cy - r * 0.24f)
    )

    // Subtle twinkling star
    val starR = size.minDimension * (0.028f + 0.018f * ((slowPhase * 2f) % 1f))
    val starCenter = Offset(size.width * 0.26f, size.height * 0.26f)
    val starColor = Color(0xFFFEF9C3).copy(alpha = 0.45f + 0.50f * pulsePhase)
    drawLine(starColor, Offset(starCenter.x - starR, starCenter.y), Offset(starCenter.x + starR, starCenter.y), size.minDimension * 0.022f, StrokeCap.Round)
    drawLine(starColor, Offset(starCenter.x, starCenter.y - starR), Offset(starCenter.x, starCenter.y + starR), size.minDimension * 0.022f, StrokeCap.Round)

    val driftX = (pulsePhase - 0.5f) * size.width * 0.08f
    drawSoftCloud(
        centerX = size.width * 0.45f + driftX,
        baseY = size.height * 0.74f,
        width = size.width * 0.68f,
        topColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFFE2E8F0),
        bottomColor = if (isDark) Color(0xFF475569) else Color(0xFF94A3B8)
    )
}

private fun DrawScope.drawWindy(fastPhase: Float, pulsePhase: Float, isDark: Boolean) {
    val lineColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
    val secondaryColor = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0284C7)

    drawSoftCloud(
        centerX = size.width * 0.44f + (pulsePhase - 0.5f) * size.width * 0.06f,
        baseY = size.height * 0.46f,
        width = size.width * 0.56f,
        topColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFFF1F5F9),
        bottomColor = if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
    )

    val strokeW = size.minDimension * 0.055f
    val shift = (fastPhase - 0.5f) * size.width * 0.10f

    // Flowing upper wind ribbon with curl
    val p1 = Path().apply {
        moveTo(size.width * 0.16f + shift, size.height * 0.60f)
        lineTo(size.width * 0.72f + shift * 0.4f, size.height * 0.60f)
        cubicTo(
            size.width * 0.84f, size.height * 0.60f,
            size.width * 0.84f, size.height * 0.46f,
            size.width * 0.72f, size.height * 0.48f
        )
    }
    drawPath(p1, color = lineColor.copy(alpha = 0.90f), style = Stroke(width = strokeW, cap = StrokeCap.Round))

    // Lower wind gust ribbon
    val p2 = Path().apply {
        moveTo(size.width * 0.24f - shift * 0.6f, size.height * 0.76f)
        lineTo(size.width * 0.66f - shift * 0.3f, size.height * 0.76f)
        cubicTo(
            size.width * 0.78f, size.height * 0.76f,
            size.width * 0.78f, size.height * 0.88f,
            size.width * 0.66f, size.height * 0.86f
        )
    }
    drawPath(p2, color = secondaryColor.copy(alpha = 0.85f), style = Stroke(width = strokeW * 0.88f, cap = StrokeCap.Round))
}

private fun DrawScope.drawSnowy(fastPhase: Float, pulsePhase: Float, isDark: Boolean) {
    val flakeColor = if (isDark) Color(0xFFE0F2FE) else Color(0xFF38BDF8)

    val xs = listOf(0.32f, 0.48f, 0.64f)
    val offsets = listOf(0.0f, 0.38f, 0.72f)
    for (i in xs.indices) {
        val p = (fastPhase + offsets[i]) % 1f
        val sway = sin((p * 2.0 * PI) + i).toFloat() * size.width * 0.04f
        val fx = size.width * xs[i] + sway
        val fy = size.height * (0.58f + p * 0.30f)
        val alpha = (1f - kotlin.math.abs(p - 0.5f) * 1.7f).coerceIn(0.2f, 0.95f)
        drawCircle(
            color = flakeColor.copy(alpha = alpha),
            radius = size.minDimension * 0.042f,
            center = Offset(fx, fy)
        )
    }

    val drift = (pulsePhase - 0.5f) * size.width * 0.04f
    drawSoftCloud(
        centerX = size.width * 0.50f + drift,
        baseY = size.height * 0.56f,
        width = size.width * 0.70f,
        topColor = if (isDark) Color(0xFFF1F5F9) else Color(0xFFFFFFFF),
        bottomColor = if (isDark) Color(0xFF94A3B8) else Color(0xFFCBD5E1)
    )
}

private fun DrawScope.drawSoftCloud(
    centerX: Float,
    baseY: Float,
    width: Float,
    topColor: Color,
    bottomColor: Color
) {
    val h = width * 0.36f
    val brush = Brush.verticalGradient(
        colors = listOf(topColor, bottomColor),
        startY = baseY - h * 1.35f,
        endY = baseY
    )
    drawCircle(
        brush = brush,
        radius = h * 0.52f,
        center = Offset(centerX - width * 0.24f, baseY - h * 0.48f)
    )
    drawCircle(
        brush = brush,
        radius = h * 0.70f,
        center = Offset(centerX - width * 0.02f, baseY - h * 0.68f)
    )
    drawCircle(
        brush = brush,
        radius = h * 0.55f,
        center = Offset(centerX + width * 0.24f, baseY - h * 0.50f)
    )
    drawRoundRect(
        brush = brush,
        topLeft = Offset(centerX - width * 0.46f, baseY - h * 0.60f),
        size = Size(width * 0.92f, h * 0.60f),
        cornerRadius = CornerRadius(h * 0.30f, h * 0.30f)
    )
}
