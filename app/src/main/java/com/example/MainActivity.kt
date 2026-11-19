package com.example

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.resolveEffectiveScreensaverPresentation
import com.example.sensor.DeviceOrientation
import com.example.ui.AppTab
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.home.ScreensaverSensorPosture
import com.example.ui.theme.MyApplicationTheme
import com.example.widget.CtecsWidgetUpdater

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var showWidgetTrackingConfirmDialog by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleWidgetLaunchIntent(intent)
        setContent {
            val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
            val deviceOrientation by viewModel.deviceOrientation.collectAsStateWithLifecycle()
            val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
            val screensaverSettings by viewModel.homeScreensaverSettings.collectAsStateWithLifecycle()
            val isScreensaverActive by viewModel.isScreensaverActive.collectAsStateWithLifecycle()
            val screensaverSensorPosture by viewModel.screensaverSensorPosture.collectAsStateWithLifecycle()
            val navNotifSettings by viewModel.navigationNotificationSettings.collectAsStateWithLifecycle()
            val windowIsLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
            var lastLandscapeOrientation by remember {
                mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
            }

            // Automatic Orientation Override:
            // - On Camera tab: follows sensor between portrait/landscape while not recording.
            // - While Home Screensaver is active: rotates natively even if system Auto-Rotate is OFF,
            //   respecting Mirror HUD preference and Orientation Lock.
            // - Otherwise locks Home, Photos, Routes, Settings cleanly to PORTRAIT.
            LaunchedEffect(
                currentTab,
                deviceOrientation,
                isRecording,
                isScreensaverActive,
                screensaverSettings,
                screensaverSensorPosture,
                windowIsLandscape
            ) {
                val targetOrientation = when {
                    currentTab == AppTab.CAMERA -> {
                        if (isRecording) {
                            null
                        } else {
                            when (deviceOrientation) {
                                DeviceOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                DeviceOrientation.LANDSCAPE_LEFT -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                DeviceOrientation.REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                                DeviceOrientation.LANDSCAPE_RIGHT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                            }
                        }
                    }
                    currentTab == AppTab.HOME && isScreensaverActive -> {
                        val presentation = resolveEffectiveScreensaverPresentation(
                            settings = screensaverSettings,
                            sensorIsLandscape = screensaverSensorPosture?.isLandscape,
                            windowIsLandscape = windowIsLandscape
                        )
                        if (!presentation.isLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } else if (presentation.isOrientationLocked) {
                            lastLandscapeOrientation
                        } else {
                            val landscapeTarget = when (screensaverSensorPosture) {
                                ScreensaverSensorPosture.LANDSCAPE_RIGHT ->
                                    ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                                ScreensaverSensorPosture.LANDSCAPE_LEFT ->
                                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                else -> lastLandscapeOrientation
                            }
                            lastLandscapeOrientation = landscapeTarget
                            landscapeTarget
                        }
                    }
                    else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }

                if (targetOrientation != null && requestedOrientation != targetOrientation) {
                    try {
                        requestedOrientation = targetOrientation
                    } catch (ignored: Exception) {}
                }
            }

            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            MyApplicationTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    MainScreen(viewModel = viewModel)

                    if (showWidgetTrackingConfirmDialog) {
                        val isCurrentlyTracking = navNotifSettings.enabled
                        AlertDialog(
                            onDismissRequest = { showWidgetTrackingConfirmDialog = false },
                            title = {
                                Text(
                                    text = if (isCurrentlyTracking) "Stop Live Navigation Tracking?" else "Start Live Navigation Tracking?",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            text = {
                                Text(
                                    text = if (isCurrentlyTracking) {
                                        "This will stop background chainage navigation updates for widgets and the status bar."
                                    } else {
                                        "This will enable live GPS & chainage tracking for home-screen widgets and navigation notifications."
                                    }
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        showWidgetTrackingConfirmDialog = false
                                        viewModel.setNavigationNotificationEnabled(!isCurrentlyTracking)
                                        CtecsWidgetUpdater.updateAllWidgets(applicationContext)
                                    },
                                    modifier = Modifier.testTag("widget_tracking_confirm_btn")
                                ) {
                                    Text(
                                        text = if (isCurrentlyTracking) "Stop Tracking" else "Start Tracking",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showWidgetTrackingConfirmDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWidgetLaunchIntent(intent)
    }

    fun handleWidgetLaunchIntent(intent: Intent?) {
        val target = intent?.getStringExtra(CtecsWidgetUpdater.EXTRA_WIDGET_OPEN_TARGET) ?: return
        when (target) {
            CtecsWidgetUpdater.TARGET_CHAINAGE,
            CtecsWidgetUpdater.TARGET_DASHBOARD -> {
                viewModel.setTab(AppTab.HOME)
            }
            CtecsWidgetUpdater.TARGET_MINI_MAP -> {
                viewModel.setTab(AppTab.ROUTES)
            }
            CtecsWidgetUpdater.TARGET_CONFIRM_TRACKING_TOGGLE -> {
                viewModel.setTab(AppTab.HOME)
                showWidgetTrackingConfirmDialog = true
            }
        }
        intent.removeExtra(CtecsWidgetUpdater.EXTRA_WIDGET_OPEN_TARGET)
    }
}
