package com.example.ui.settings

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.settings.SettingSaveEntry
import com.example.settings.SettingSavePhase
import com.example.settings.SettingsAutoSaveCoordinator
import com.example.ui.theme.CtecsTheme

val LocalSettingsAutoSaveCoordinator = compositionLocalOf<SettingsAutoSaveCoordinator?> { null }
val LocalSettingsSectionKey = compositionLocalOf<String?> { null }

internal fun isSystemReducedMotionEnabled(context: Context): Boolean {
    return try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    } catch (_: Throwable) {
        false
    }
}

/**
 * Observes a single specific setting key's auto-save state and renders the tiny,
 * zero-layout-shift confirmation indicator beside that setting.
 */
@Composable
fun ScopedSettingSaveBadge(
    settingKey: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val coordinator = LocalSettingsAutoSaveCoordinator.current ?: return
    val allEntries by coordinator.entries.collectAsStateWithLifecycle()
    val entry = allEntries[settingKey]
    SettingsSaveConfirmationBadge(
        entry = entry,
        onRetry = { coordinator.retrySave(settingKey) },
        showLabel = showLabel,
        modifier = modifier
    )
}

/**
 * Observes a Settings category/section's latest auto-save confirmation so the section header
 * displays a subtle confirmation indicator when any setting inside that section saves.
 */
@Composable
fun SectionSettingSaveBadge(
    sectionKey: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val coordinator = LocalSettingsAutoSaveCoordinator.current ?: return
    val sectionEntries by coordinator.sectionEntries.collectAsStateWithLifecycle()
    val entry = sectionEntries[sectionKey]
    SettingsSaveConfirmationBadge(
        entry = entry,
        onRetry = { entry?.let { coordinator.retrySave(it.settingKey) } },
        showLabel = showLabel,
        modifier = modifier
    )
}

/**
 * Minimalist, lightweight animated save confirmation badge:
 * - Fixed 20.dp vertical footprint so surrounding layout NEVER shifts.
 * - SAVING: tiny 12.dp progress spinner.
 * - SAVED: tiny animated circular green checkmark with optional subtle "Saved" label (soft green glow in Dark Mode, crisp green in Light Mode).
 * - FAILED: tiny warning indicator with a one-tap Retry action.
 * - Fully respects Android reduced-motion settings.
 */
@Composable
fun SettingsSaveConfirmationBadge(
    entry: SettingSaveEntry?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true
) {
    val c = CtecsTheme.colors
    val context = LocalContext.current
    val reducedMotion = remember(context) { isSystemReducedMotionEnabled(context) }

    val phase = entry?.phase ?: SettingSavePhase.IDLE
    val animKey = if (phase == SettingSavePhase.IDLE) "IDLE" else "${phase.name}_${entry?.revision ?: 0L}"

    // Soft green glow on AMOLED Dark Mode, crisp forest green on Light Mode
    val savedGreen = if (c.isDark) Color(0xFF34D399) else Color(0xFF15803D)
    val savedBg = if (c.isDark) Color(0xFF34D399).copy(alpha = 0.14f) else Color(0xFF15803D).copy(alpha = 0.09f)
    val failedColor = if (c.isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
    val failedBg = if (c.isDark) Color(0xFFFBBF24).copy(alpha = 0.14f) else Color(0xFFB45309).copy(alpha = 0.10f)

    Box(
        modifier = modifier.height(20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        AnimatedContent(
            targetState = Pair(phase, entry?.revision ?: 0L),
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    (fadeIn(tween(150)) + scaleIn(initialScale = 0.85f, animationSpec = tween(170, easing = FastOutSlowInEasing)))
                        .togetherWith(fadeOut(tween(180)))
                }
            },
            label = "save_confirmation_$animKey"
        ) { (currentPhase, currentRev) ->
            when (currentPhase) {
                SettingSavePhase.IDLE -> {
                    Spacer(modifier = Modifier.size(1.dp))
                }

                SettingSavePhase.SAVING -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .semantics {
                                contentDescription = "Saving preference"
                            }
                            .testTag("setting_save_saving_${entry?.settingKey ?: "any"}")
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.6.dp,
                            color = c.textSecondary
                        )
                        if (showLabel) {
                            Text(
                                text = "Saving",
                                color = c.textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }

                SettingSavePhase.SAVED -> {
                    var targetDraw by remember(currentRev) { mutableFloatStateOf(if (reducedMotion) 1f else 0f) }
                    LaunchedEffect(currentRev) {
                        targetDraw = 1f
                    }
                    val checkProgress by animateFloatAsState(
                        targetValue = targetDraw,
                        animationSpec = tween(
                            durationMillis = if (reducedMotion) 0 else 260,
                            easing = FastOutSlowInEasing
                        ),
                        label = "check_draw_progress"
                    )
                    val popScale by animateFloatAsState(
                        targetValue = if (targetDraw > 0f) 1f else 0.82f,
                        animationSpec = tween(
                            durationMillis = if (reducedMotion) 0 else 200,
                            easing = FastOutSlowInEasing
                        ),
                        label = "check_pop_scale"
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .scale(popScale)
                            .clip(RoundedCornerShape(999.dp))
                            .background(savedBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = "Preference saved"
                            }
                            .testTag("setting_save_saved_${entry?.settingKey ?: "any"}")
                    ) {
                        MinimalAnimatedCheckCircle(
                            progress = checkProgress,
                            color = savedGreen,
                            isDark = c.isDark
                        )
                        if (showLabel) {
                            Text(
                                text = "Saved",
                                color = savedGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                }

                SettingSavePhase.FAILED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(failedBg)
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .semantics {
                                liveRegion = LiveRegionMode.Polite
                                contentDescription = "Failed to save preference, tap to retry"
                            }
                            .testTag("setting_save_failed_${entry?.settingKey ?: "any"}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = failedColor,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Retry",
                            color = failedColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = failedColor,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinimalAnimatedCheckCircle(
    progress: Float,
    color: Color,
    isDark: Boolean
) {
    Canvas(modifier = Modifier.size(13.dp)) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Subtle green glow halo in AMOLED Dark Mode
        if (isDark) {
            drawCircle(
                color = color.copy(alpha = 0.22f * progress),
                radius = r,
                center = center
            )
        }

        // Minimalist circular ring
        drawCircle(
            color = color.copy(alpha = 0.85f),
            radius = r - 0.9.dp.toPx(),
            center = center,
            style = Stroke(width = 1.2.dp.toPx())
        )

        // Smooth draw-on checkmark path
        val fullCheck = Path().apply {
            moveTo(size.width * 0.29f, size.height * 0.52f)
            lineTo(size.width * 0.45f, size.height * 0.68f)
            lineTo(size.width * 0.72f, size.height * 0.35f)
        }
        val measure = PathMeasure()
        measure.setPath(fullCheck, false)
        val segment = Path()
        measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), segment, true)

        drawPath(
            path = segment,
            color = color,
            style = Stroke(
                width = 1.55.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
