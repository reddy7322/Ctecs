package com.example

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.sensor.DeviceOrientation
import com.example.ui.MainScreen
import com.example.ui.MainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
            val deviceOrientation by viewModel.deviceOrientation.collectAsStateWithLifecycle()
            val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()

            // Automatic Orientation Override:
            // Locks non-camera screens (Home, Photos, Routes, Settings) strictly to PORTRAIT mode.
            // On Camera screen, dynamically adjusts between portrait and landscape for photo capture.
            LaunchedEffect(currentTab, deviceOrientation, isRecording) {
                if (currentTab == com.example.ui.AppTab.CAMERA) {
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
                }
            }
        }
    }
}
