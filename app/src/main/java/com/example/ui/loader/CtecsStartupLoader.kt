package com.example.ui.loader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.settings.isSystemReducedMotionEnabled
import com.example.ui.theme.CtecsTheme

/**
 * Resolves the appropriate transparent CTECS logo resource for Dark Mode vs Light Mode.
 */
internal fun resolveStartupLogoRes(isDark: Boolean): Int {
    return if (isDark) R.drawable.ctecs_logo_dark else R.drawable.ctecs_logo_light
}

/**
 * Minimalist, genuinely transparent, borderless CTECS application startup loader.
 *
 * Design Guarantees:
 * - Zero popup card, zero opaque modal container, zero thick border, zero heavy shadow.
 * - Uses the existing native launch window / theme surface directly (`c.background`),
 *   so logo, tiny percentage, and thin progress line float cleanly without any box.
 * - Automatically selects transparent Dark Mode or Light Mode CTECS logo variant.
 * - Tracks real startup progress; never shows 100% before essential initialisation completes.
 * - Respects Android reduced-motion settings.
 */
@Composable
fun CtecsStartupLoader(
    state: StartupLoaderUiState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val context = LocalContext.current
    val reducedMotion = remember(context) { isSystemReducedMotionEnabled(context) }

    AnimatedVisibility(
        visible = state.isVisible,
        enter = fadeIn(tween(if (reducedMotion) 0 else 160)),
        exit = fadeOut(tween(if (reducedMotion) 0 else 240, easing = FastOutSlowInEasing)),
        modifier = modifier
    ) {
        var entered by remember { mutableStateOf(reducedMotion) }
        LaunchedEffect(Unit) { entered = true }

        val logoAlpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(if (reducedMotion) 0 else 280, easing = FastOutSlowInEasing),
            label = "startup_logo_alpha"
        )
        val logoScale by animateFloatAsState(
            targetValue = if (entered) 1f else 0.94f,
            animationSpec = tween(if (reducedMotion) 0 else 320, easing = FastOutSlowInEasing),
            label = "startup_logo_scale"
        )
        val animatedProgress by animateFloatAsState(
            targetValue = if (state.isCompleted) 1f else state.progress.coerceIn(0f, 0.99f),
            animationSpec = tween(if (reducedMotion) 0 else 240, easing = FastOutSlowInEasing),
            label = "startup_progress_fill"
        )

        val logoRes = resolveStartupLogoRes(c.isDark)
        val logoVariantDesc = if (c.isDark) "CTECS logo dark theme" else "CTECS logo light theme"
        val displayPercent = if (state.isCompleted) 100 else (animatedProgress.coerceIn(0f, 0.99f) * 100f).toInt()

        // Directly uses theme launch surface — zero popup card, zero modal container, zero border
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.background)
                .testTag("ctecs_startup_loader"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .background(Color.Transparent)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 1. Original transparent CTECS Logo (exact aspect ratio, centered, theme-matched)
                Image(
                    painter = painterResource(id = logoRes),
                    contentDescription = logoVariantDesc,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(92.dp)
                        .alpha(logoAlpha)
                        .scale(logoScale)
                        .semantics { contentDescription = logoVariantDesc }
                        .testTag("ctecs_startup_logo")
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 2. Tiny animated loading percentage
                if (state.errorMessage == null) {
                    val percentLabel = if (state.isIndeterminate && !state.isCompleted) {
                        "Loading..."
                    } else {
                        "Loading... $displayPercent%"
                    }
                    Text(
                        text = percentLabel,
                        color = c.textSecondary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.25.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("ctecs_startup_percentage")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. Minimalist thin horizontal progress line (transparent surroundings)
                    MinimalStartupProgressLine(
                        progress = animatedProgress,
                        isIndeterminate = state.isIndeterminate && !state.isCompleted,
                        reducedMotion = reducedMotion,
                        trackColor = c.subtleSeparator,
                        accentColor = c.accent,
                        modifier = Modifier.testTag("ctecs_startup_progress")
                    )

                    Spacer(modifier = Modifier.height(9.dp))

                    // 4. Subtle dynamic loading status (actual startup task label)
                    AnimatedContent(
                        targetState = state.statusMessage,
                        transitionSpec = {
                            if (reducedMotion) {
                                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                            } else {
                                fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                            }
                        },
                        label = "startup_status_fade"
                    ) { statusText ->
                        Text(
                            text = statusText,
                            color = c.textMuted,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.testTag("ctecs_startup_status")
                        )
                    }
                } else {
                    // Minimal non-crashing error & retry UI (never shows 100%)
                    Text(
                        text = state.errorMessage,
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .testTag("ctecs_startup_error")
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 88.dp, minHeight = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.accent.copy(alpha = if (c.isDark) 0.16f else 0.11f))
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                            .testTag("ctecs_startup_retry_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Retry",
                            color = c.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalStartupProgressLine(
    progress: Float,
    isIndeterminate: Boolean,
    reducedMotion: Boolean,
    trackColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .width(116.dp)
            .height(2.5.dp)
            .clip(CircleShape)
            .background(trackColor)
    ) {
        if (isIndeterminate) {
            val infiniteTransition = rememberInfiniteTransition(label = "startup_indeterminate")
            val sweep by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = if (reducedMotion) 0.35f else 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "startup_indeterminate_sweep"
            )
            val segWidth = maxWidth * 0.34f
            val travel = (maxWidth - segWidth).coerceAtLeast(0.dp)
            Box(
                modifier = Modifier
                    .offset(x = travel * sweep)
                    .width(segWidth)
                    .height(2.5.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        } else {
            Box(
                modifier = Modifier
                    .width(maxWidth * progress.coerceIn(0f, 1f))
                    .height(2.5.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
    }
}
