package com.example

import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sensor.DeviceOrientation
import com.example.ui.AppTab
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
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
            val navNotifSettings by viewModel.navigationNotificationSettings.collectAsStateWithLifecycle()

            // Automatic Orientation Override:
            // Locks non-camera screens (Home, Photos, Routes, Settings) strictly to PORTRAIT mode.
            // On Camera screen, dynamically adjusts between portrait and landscape for photo capture.
            LaunchedEffect(currentTab, deviceOrientation, isRecording) {
                if (currentTab == AppTab.CAMERA) {
                    if (!isRecording) {
                        val targetOrientation = when (deviceOrientation) {
                            DeviceOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            DeviceOrientation.LANDSCAPE_LEFT -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            DeviceOrientation.REVERSE_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                            DeviceOrientation.LANDSCAPE_RIGHT -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                        }
                        if (requestedOrientation != targetOrientation) {
                            try {
                                requestedOrientation = targetOrientation
                            } catch (ignored: Exception) {}
                        }
                    }
                } else {
                    // Force PORTRAIT on Home Page and all other non-camera screens
                    if (requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                        try {
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        } catch (ignored: Exception) {}
                    }
                }
            }

            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

            MyApplicationTheme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.material3.MaterialTheme.colorScheme.background) {
                    MainScreen(viewModel = viewModel)

                    if (showWidgetTrackingConfirmDialog) {
                        val isCurrentlyTracking = navNotifSettings.enabled && screensaverSettings.lockScreenNavEnabled
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
