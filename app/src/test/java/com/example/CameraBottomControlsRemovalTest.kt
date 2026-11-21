package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.model.CaptureMode
import com.example.ui.MainViewModel
import com.example.ui.camera.CameraScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraBottomControlsRemovalTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun advanceAndIdle(ms: Long = 200L) {
        composeTestRule.mainClock.advanceTimeBy(ms)
        shadowOf(Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port")
    fun portraitCameraScreen_removesBottomPhotoVideoOutputControls_andPreservesCaptureAndOverlays() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)
        viewModel.setManualChainage("145+500")

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraScreen(
                        viewModel = viewModel,
                        onNavigateToPhotos = {}
                    )
                }
            }
        }
        advanceAndIdle(200L)

        // Removed bottom Photo/Video output controls & popups must not exist
        composeTestRule.onNodeWithTag("portrait_bottom_output_bar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("portrait_photo_settings_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("portrait_video_settings_card").assertDoesNotExist()
        composeTestRule.onNodeWithTag("photo_output_settings_popup").assertDoesNotExist()
        composeTestRule.onNodeWithTag("video_output_settings_popup").assertDoesNotExist()

        // Preserved core camera controls, zoom, highway chips & timestamp overlay
        composeTestRule.onNodeWithTag("shutter_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("recent_thumbnail_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gps_sim_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("portrait_highway_functions_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("portrait_timestamp_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zoom_preset_1").assertIsDisplayed()

        // Space previously occupied by the bottom output bar is reclaimed (shutter sits near bottom edge)
        val shutterBounds = composeTestRule.onNodeWithTag("shutter_button").getBoundsInRoot()
        assertTrue(
            "Shutter bottom (${shutterBounds.bottom.value}dp) should sit cleanly near bottom edge of 891dp screen without empty output container gap",
            shutterBounds.bottom.value >= 865f && shutterBounds.bottom.value <= 891f
        )

        // Photo <-> Video capture mode switching continues working cleanly
        composeTestRule.onNodeWithText("VIDEO").assertIsDisplayed().performClick()
        advanceAndIdle(150L)
        assertEquals(CaptureMode.VIDEO, viewModel.captureMode.value)

        composeTestRule.onNodeWithText("PHOTO").assertIsDisplayed().performClick()
        advanceAndIdle(150L)
        assertEquals(CaptureMode.PHOTO, viewModel.captureMode.value)
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    fun landscapeCameraScreen_removesPhotoVideoOutputControls_andPreservesCaptureAndOverlays() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)
        viewModel.setManualChainage("145+500")

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraScreen(
                        viewModel = viewModel,
                        onNavigateToPhotos = {}
                    )
                }
            }
        }
        advanceAndIdle(200L)

        // Removed Photo/Video output controls & popups must not exist in Landscape
        composeTestRule.onNodeWithTag("landscape_photo_settings_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("landscape_video_settings_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("portrait_bottom_output_bar").assertDoesNotExist()
        composeTestRule.onNodeWithTag("photo_output_settings_popup").assertDoesNotExist()
        composeTestRule.onNodeWithTag("video_output_settings_popup").assertDoesNotExist()

        // Preserved core landscape camera controls, dock, toolbar, zoom & highway functions
        composeTestRule.onNodeWithTag("landscape_shutter_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_video_record_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_gallery_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_camera_flip_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_highway_functions_column").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_toolbar_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_flash_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_timer_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zoom_preset_1").assertIsDisplayed()
    }
}
