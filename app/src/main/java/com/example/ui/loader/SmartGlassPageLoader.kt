package com.example.ui.loader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.CtecsTheme

/**
 * Minimalist flat borderless page loader adhering to the AMOLED Deep Black / Professional Light
 * design system (subtle blue progress indicator, zero glassmorphism, zero heavy shadows/glows).
 */
@Composable
fun SmartGlassPageLoader(
    isLoading: Boolean,
    progress: Float,
    status: String,
    onDismissRequest: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isLoading,
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(220)),
        modifier = modifier
    ) {
        val c = CtecsTheme.colors
        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "animated_page_loader_progress"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (progress >= 1f) onDismissRequest?.invoke()
                }
                .testTag("smart_glass_page_loader"),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (c.isDark) c.surface else c.background,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                modifier = Modifier
                    .widthIn(min = 260.dp, max = 320.dp)
                    .padding(horizontal = 24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(84.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${(animatedProgress * 100).toInt()}",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.textPrimary,
                            letterSpacing = (-0.3).sp
                        )
                        Text(
                            text = "%",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = c.accent,
                            modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
                        )
                    }

                    // Clean flat electric-blue progress bar
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(c.secondarySurface)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(maxWidth * animatedProgress)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(c.accent)
                        )
                    }

                    Text(
                        text = if (animatedProgress >= 1f) "Ready" else status,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
