package com.example.ui.settings

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CtecsTheme
import kotlin.math.PI
import kotlin.math.sin

internal fun isSystemReducedMotionEnabled(context: Context): Boolean {
    return try {
        val scale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        scale == 0f
    } catch (_: Exception) {
        false
    }
}

/**
 * Lightweight animated coffee cup illustration with:
 * - Gentle floating movement
 * - Natural rising steam wisps with opacity variation
 * - Smooth coffee filling level driven by the selected donation amount
 * - Subtle verified celebratory glow ONLY when [isVerifiedPaid] is true
 * - Full respect for Android reduced-motion accessibility settings
 */
@Composable
fun AnimatedCoffeeCup(
    fillFraction: Float,
    modifier: Modifier = Modifier,
    size: Dp = 76.dp,
    isVerifiedPaid: Boolean = false
) {
    val context = LocalContext.current
    val reducedMotion = remember(context) { isSystemReducedMotionEnabled(context) }
    val c = CtecsTheme.colors

    val animatedFill by animateFloatAsState(
        targetValue = fillFraction.coerceIn(0.12f, 0.96f),
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 420, easing = FastOutSlowInEasing),
        label = "coffee_fill_level"
    )
    val celebrationScale by animateFloatAsState(
        targetValue = if (isVerifiedPaid && !reducedMotion) 1.04f else 1f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 360, easing = FastOutSlowInEasing),
        label = "coffee_celebration_scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "coffee_cup_motion")
    val floatPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (reducedMotion) 0f else (2f * PI.toFloat()),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coffee_float_phase"
    )
    val steamPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (reducedMotion) 0f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "coffee_steam_phase"
    )

    val cupOutline = c.coffeeAccent
    val coffeeLiquidTop = if (c.isDark) Color(0xFFE0A464) else Color(0xFFA86A34)
    val coffeeLiquidBottom = if (c.isDark) Color(0xFF7B4820) else Color(0xFF5D3415)
    val steamColor = c.coffeeAccent
    val glowColor = if (isVerifiedPaid) c.success else c.coffeeAccent

    Canvas(
        modifier = modifier
            .size(size)
            .scale(celebrationScale)
            .semantics {
                contentDescription = if (isVerifiedPaid) "Verified coffee received" else "Coffee support illustration"
            }
    ) {
        val w = this.size.width
        val h = this.size.height
        val floatOffsetY = if (reducedMotion) 0f else sin(floatPhase) * (h * 0.025f)

        // Subtle warm glow behind cup (enhanced ONLY on verified PAID success)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glowColor.copy(alpha = if (isVerifiedPaid) 0.28f else 0.12f),
                    Color.Transparent
                ),
                center = Offset(w * 0.48f, h * 0.58f + floatOffsetY),
                radius = w * 0.48f
            ),
            radius = w * 0.48f,
            center = Offset(w * 0.48f, h * 0.58f + floatOffsetY)
        )

        // Cup geometry
        val cupLeft = w * 0.22f
        val cupRight = w * 0.72f
        val cupTop = h * 0.40f + floatOffsetY
        val cupBottom = h * 0.84f + floatOffsetY
        val cupWidth = cupRight - cupLeft
        val cupHeight = cupBottom - cupTop

        // Rising organic steam wisps (3 staggered streams)
        val steamBaseY = cupTop - h * 0.05f
        val steamHeight = h * 0.25f
        for (i in 0..2) {
            val localP = (steamPhase + i * 0.33f) % 1f
            val xCenter = cupLeft + cupWidth * (0.28f + i * 0.22f)
            val riseY = steamBaseY - localP * steamHeight * 0.45f
            val alpha = if (reducedMotion) 0.38f else (sin(localP * PI).toFloat() * 0.65f).coerceIn(0.05f, 0.65f)
            val sway = if (reducedMotion) 0f else sin((localP * 2f * PI + i).toDouble()).toFloat() * (w * 0.028f)

            val steamPath = Path().apply {
                moveTo(xCenter, riseY)
                cubicTo(
                    xCenter + sway - w * 0.025f, riseY - steamHeight * 0.35f,
                    xCenter - sway + w * 0.025f, riseY - steamHeight * 0.68f,
                    xCenter + sway * 0.5f, riseY - steamHeight
                )
            }
            drawPath(
                path = steamPath,
                color = steamColor.copy(alpha = alpha),
                style = Stroke(width = w * 0.028f, cap = StrokeCap.Round)
            )
        }

        // Cup interior path for clipping liquid fill
        val cupShape = Path().apply {
            moveTo(cupLeft, cupTop)
            lineTo(cupRight, cupTop)
            lineTo(cupRight - cupWidth * 0.08f, cupBottom - cupHeight * 0.12f)
            quadraticTo(
                cupRight - cupWidth * 0.12f, cupBottom,
                cupLeft + cupWidth * 0.5f, cupBottom
            )
            quadraticTo(
                cupLeft + cupWidth * 0.12f, cupBottom,
                cupLeft + cupWidth * 0.08f, cupBottom - cupHeight * 0.12f
            )
            close()
        }

        // Cup subtle background fill
        drawPath(
            path = cupShape,
            color = c.coffeeSurface
        )

        // Animated coffee liquid inside cup
        clipPath(cupShape) {
            val fillTop = cupBottom - cupHeight * animatedFill
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(coffeeLiquidTop, coffeeLiquidBottom),
                    startY = fillTop,
                    endY = cupBottom
                ),
                topLeft = Offset(cupLeft, fillTop),
                size = Size(cupWidth, cupBottom - fillTop + 2f)
            )
            // Subtle crema highlight at top of liquid
            drawRoundRect(
                color = Color(0xFFF6D9B2).copy(alpha = 0.42f),
                topLeft = Offset(cupLeft + cupWidth * 0.08f, fillTop),
                size = Size(cupWidth * 0.84f, h * 0.024f),
                cornerRadius = CornerRadius(4f, 4f)
            )
        }

        // Cup outline
        drawPath(
            path = cupShape,
            color = cupOutline,
            style = Stroke(width = w * 0.036f, cap = StrokeCap.Round)
        )

        // Cup handle
        val handlePath = Path().apply {
            moveTo(cupRight - w * 0.01f, cupTop + cupHeight * 0.20f)
            cubicTo(
                cupRight + w * 0.18f, cupTop + cupHeight * 0.16f,
                cupRight + w * 0.18f, cupTop + cupHeight * 0.68f,
                cupRight - w * 0.04f, cupTop + cupHeight * 0.68f
            )
        }
        drawPath(
            path = handlePath,
            color = cupOutline,
            style = Stroke(width = w * 0.036f, cap = StrokeCap.Round)
        )

        // Minimalist saucer line
        drawLine(
            color = cupOutline.copy(alpha = 0.55f),
            start = Offset(cupLeft - w * 0.04f, cupBottom + h * 0.05f),
            end = Offset(cupRight + w * 0.04f, cupBottom + h * 0.05f),
            strokeWidth = w * 0.028f,
            cap = StrokeCap.Round
        )
    }
}
