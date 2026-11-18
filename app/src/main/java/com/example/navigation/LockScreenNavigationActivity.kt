package com.example.navigation

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

/**
 * Lock-Screen Navigation Activity using [setShowWhenLocked] (API 27+) / [FLAG_SHOW_WHEN_LOCKED]
 * so live chainage & route guidance appears cleanly on lock without ever bypassing or disabling
 * system PIN/password/biometric security.
 */
class LockScreenNavigationActivity : ComponentActivity() {

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        enableEdgeToEdge()

        val locationTracker = CtecsNavigationTelemetryHub.getOrCreateLocationTracker(applicationContext)

        setContent {
            val locationState by locationTracker.locationState.collectAsStateWithLifecycle()
            val matchResult by CtecsNavigationTelemetryHub.matchResult.collectAsStateWithLifecycle()
            val activeRoute by CtecsNavigationTelemetryHub.activeRoute.collectAsStateWithLifecycle()
            val projectProfile by CtecsNavigationTelemetryHub.projectProfile.collectAsStateWithLifecycle()
            val locationName by CtecsNavigationTelemetryHub.locationName.collectAsStateWithLifecycle()
            val compassState by CtecsNavigationTelemetryHub.compassState.collectAsStateWithLifecycle()
            val screensaverSettings by CtecsNavigationTelemetryHub.screensaverSettings.collectAsStateWithLifecycle()
            val is24Hour by CtecsNavigationTelemetryHub.is24HourFormat.collectAsStateWithLifecycle()

            var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(2000L)
                    nowMs = System.currentTimeMillis()
                }
            }

            val resolver = remember { LockScreenNavStateResolver() }
            val navState = remember(
                locationState,
                matchResult,
                activeRoute?.id,
                projectProfile,
                locationName,
                is24Hour,
                nowMs
            ) {
                resolver.resolve(
                    locationState = locationState,
                    matchResult = matchResult,
                    routeData = activeRoute,
                    projectProfile = projectProfile,
                    locationName = locationName,
                    is24HourFormat = is24Hour,
                    nowMs = nowMs
                )
            }

            MyApplicationTheme(themeMode = "dark") {
                LockScreenNavigationContent(
                    navState = navState,
                    location = locationState.point,
                    bearing = locationState.bearing,
                    compassHeading = compassState.azimuthDegrees,
                    routeData = activeRoute,
                    screensaverSettings = screensaverSettings,
                    onClose = { finish() }
                )
            }
        }
    }
}
