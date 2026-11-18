package com.example.ui.home

import android.view.OrientationEventListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * Physical device orientation posture detected exclusively while the Screensaver is active
 * and Auto Orientation is enabled.
 */
enum class ScreensaverSensorPosture(
    val isLandscape: Boolean,
    val softwareRotationDeg: Float
) {
    PORTRAIT(isLandscape = false, softwareRotationDeg = 0f),
    LANDSCAPE_RIGHT(isLandscape = true, softwareRotationDeg = 90f),
    LANDSCAPE_LEFT(isLandscape = true, softwareRotationDeg = -90f)
}

object ScreensaverOrientationClassifier {

    /**
     * Classifies raw Android [OrientationEventListener] azimuth angle (0..359 deg) into a posture
     * with angular deadzones around 45/135/225/315 degree diagonals to avoid flickering.
     * Returns [currentPosture] when [orientationDegrees] is [OrientationEventListener.ORIENTATION_UNKNOWN]
     * or inside a diagonal deadzone.
     */
    fun classifyAngle(
        orientationDegrees: Int,
        currentPosture: ScreensaverSensorPosture?
    ): ScreensaverSensorPosture? {
        if (orientationDegrees < 0) return currentPosture
        val deg = ((orientationDegrees % 360) + 360) % 360
        return when {
            deg <= 32 || deg >= 328 || deg in 148..212 -> ScreensaverSensorPosture.PORTRAIT
            deg in 58..122 -> ScreensaverSensorPosture.LANDSCAPE_RIGHT
            deg in 238..302 -> ScreensaverSensorPosture.LANDSCAPE_LEFT
            else -> currentPosture
        }
    }
}

/**
 * Listens to Android's hardware orientation sensor ONLY when [enabled] is true and the host
 * lifecycle is resumed. Unregisters immediately when dismissed or paused for zero battery waste.
 * Applies [debounceMs] hold delay so fleeting tilts or hand jitter never flip layouts.
 */
@Composable
fun rememberScreensaverSensorPosture(
    enabled: Boolean,
    debounceMs: Long = 380L
): ScreensaverSensorPosture? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var rawPosture by remember { mutableStateOf<ScreensaverSensorPosture?>(null) }
    var stablePosture by remember { mutableStateOf<ScreensaverSensorPosture?>(null) }
    var isLifecycleResumed by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            isLifecycleResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val shouldListen = enabled && isLifecycleResumed

    DisposableEffect(shouldListen, context) {
        if (!shouldListen) {
            rawPosture = null
            stablePosture = null
            onDispose { }
        } else {
            val listener = object : OrientationEventListener(context) {
                override fun onOrientationChanged(orientation: Int) {
                    val classified = ScreensaverOrientationClassifier.classifyAngle(
                        orientationDegrees = orientation,
                        currentPosture = rawPosture ?: stablePosture
                    )
                    if (classified != null && classified != rawPosture) {
                        rawPosture = classified
                    }
                }
            }
            if (listener.canDetectOrientation()) {
                listener.enable()
            }
            onDispose {
                listener.disable()
            }
        }
    }

    LaunchedEffect(shouldListen, rawPosture) {
        if (!shouldListen) return@LaunchedEffect
        val candidate = rawPosture ?: return@LaunchedEffect
        if (stablePosture == null) {
            // Establish initial posture immediately without artificial startup lag
            stablePosture = candidate
        } else if (candidate != stablePosture) {
            delay(debounceMs.coerceAtLeast(150L))
            stablePosture = candidate
        }
    }

    return if (shouldListen) stablePosture else null
}
