package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Thread-safe, reference-counted Screen Wake Lock Manager for CTECS.
 * Ensures the screen remains awake while Camera is open or Highway Survey is actively recording,
 * without interfering with each other or causing unintended screen timeouts.
 */
object ScreenWakeLockManager {
    private val activeRequesters = mutableSetOf<String>()

    @Synchronized
    fun acquire(activity: Activity?, requesterId: String) {
        if (activity == null) return
        activeRequesters.add(requesterId)
        try {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (ignored: Exception) {}
    }

    @Synchronized
    fun release(activity: Activity?, requesterId: String) {
        if (activity == null) return
        activeRequesters.remove(requesterId)
        if (activeRequesters.isEmpty()) {
            try {
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } catch (ignored: Exception) {}
        }
    }

    @Synchronized
    fun isWakeLockActive(): Boolean = activeRequesters.isNotEmpty()

    @Synchronized
    fun getActiveRequesters(): Set<String> = activeRequesters.toSet()
}

/**
 * Resolves the underlying Activity from a given Context, unwrapping any ContextWrappers safely.
 */
fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) {
            return current
        }
        current = current.baseContext
    }
    return null
}

/**
 * Composable lifecycle helper that keeps the device screen awake as long as [enabled] is true
 * and the composable remains in the composition.
 *
 * Automatically releases the wake lock on disposal or when [enabled] changes to false,
 * safely respecting other active requesters (e.g. Survey while Camera is in use, or vice-versa).
 */
@Composable
fun KeepScreenOn(
    enabled: Boolean = true,
    requesterId: String
) {
    val context = LocalContext.current
    DisposableEffect(enabled, requesterId) {
        val activity = context.findActivity()
        if (enabled) {
            ScreenWakeLockManager.acquire(activity, requesterId)
        }
        onDispose {
            ScreenWakeLockManager.release(activity, requesterId)
        }
    }
}
