package com.example.ui.loader

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Smart Minimalist Glass Page Loader UI with percentage animations
 * and transparent logo branding.
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
        enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.90f, animationSpec = tween(220)),
        exit = fadeOut(tween(300)) + scaleOut(targetScale = 1.05f, animationSpec = tween(300)),
        modifier = modifier
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "animated_page_loader_progress"
        )

        // Micro-breathing ambient animation for the logo
        val infiniteTransition = rememberInfiniteTransition(label = "logo_breathe")
        val logoScale by infiniteTransition.animateFloat(
            initialValue = 0.96f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(1300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "logo_scale"
        )
        val auraAlpha by infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.65f,
            animationSpec = infiniteRepeatable(
                animation = tween(1300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "aura_alpha"
        )

        // Fullscreen translucent glass overlay backdrop
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x99050811))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (progress >= 1f) onDismissRequest?.invoke()
                }
                .testTag("smart_glass_page_loader"),
            contentAlignment = Alignment.Center
        ) {
            // Central Minimalist Glass Card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color(0xDD0C1322), // Translucent frosted glass obsidian
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x44FFFFFF),
                            Color(0x15FFFFFF),
                            Color(0x40FF6D00) // Subtle highway orange reflection
                        )
                    )
                ),
                shadowElevation = 18.dp,
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 330.dp)
                    .padding(horizontal = 24.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = Color(0x55FF6D00)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Logo Container with Smart Transparent Background & Glowing Halo
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    ) {
                        // Ambient radial glow behind the logo
                        Box(
                            modifier = Modifier
                                .size(115.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFF6D00).copy(alpha = auraAlpha * 0.35f),
                                            Color(0xFF0284C7).copy(alpha = auraAlpha * 0.20f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Untouched Attached Logo with its native transparent background
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(105.dp)
                                .scale(logoScale)
                        )
                    }

                    // Smart Percentage Number with Accent Typography
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "${(animatedProgress * 100).toInt()}",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = "%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFF9E00),
                            modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
                        )
                    }

                    // Smart Percentage Progress Bar
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(Color(0x28FFFFFF))
                    ) {
                        val maxW = maxWidth
                        val currentW = maxW * animatedProgress

                        // Gradient Fill Track (Highway Orange -> Cyan/Sky Blue)
                        Box(
                            modifier = Modifier
                                .width(currentW)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFFFF6D00),
                                            Color(0xFFFF9E00),
                                            Color(0xFF38BDF8)
                                        )
                                    )
                                )
                        )
                    }

                    // Smart Subtitle / Status Text
                    Text(
                        text = if (animatedProgress >= 1f) "Ready" else status,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}
