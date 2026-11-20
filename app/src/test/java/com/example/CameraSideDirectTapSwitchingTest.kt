package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.SmartDirectionState
import com.example.ui.MainViewModel
import com.example.ui.camera.CameraScreen
import com.example.ui.camera.CameraSideOption
import com.example.ui.camera.resolveCameraSideOption
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraSideDirectTapSwitchingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun advanceAndIdle(ms: Long = 200L) {
        composeTestRule.mainClock.advanceTimeBy(ms)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertNoManualOrDropdownUiPresent() {
        // Zero visible MANUAL / Manual / MAN / • M labels anywhere on Camera Screen
        assertEquals(0, composeTestRule.onAllNodesWithText("MANUAL", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("MAN▾", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("• M", substring = true).fetchSemanticsNodes().size)
        // Zero dropdown / popup menu items
        assertEquals(0, composeTestRule.onAllNodesWithText("Auto (Smart Direction)", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Smart Direction Mode", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("AUTO▾", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `CameraSideOption cycles strictly in RHS LHS BOTH MEDIAN NONE AUTO RHS order`() {
        val sequence = buildList {
            var current = CameraSideOption.RHS
            repeat(7) {
                add(current)
                current = current.next()
            }
        }
        assertEquals(
            listOf(
                CameraSideOption.RHS,
                CameraSideOption.LHS,
                CameraSideOption.BOTH,
                CameraSideOption.MEDIAN,
                CameraSideOption.NONE,
                CameraSideOption.AUTO,
                CameraSideOption.RHS
            ),
            sequence
        )

        // Verify resolveCameraSideOption handles AUTO, all manual sides, and BHS -> BOTH cleanly
        assertEquals(
            CameraSideOption.AUTO,
            resolveCameraSideOption(
                SmartDirectionState(isManualOverride = false, confirmedSide = "LHS"),
                ProjectProfile(roadSide = RoadSide.RHS)
            )
        )
        assertEquals(
            CameraSideOption.BOTH,
            resolveCameraSideOption(
                SmartDirectionState(isManualOverride = true, confirmedSide = "BHS"),
                ProjectProfile(roadSide = RoadSide.BOTH)
            )
        )
        assertEquals(
            CameraSideOption.NONE,
            resolveCameraSideOption(
                SmartDirectionState(isManualOverride = true, confirmedSide = "NONE"),
                ProjectProfile(roadSide = RoadSide.NONE)
            )
        )
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port")
    fun `Portrait CameraScreen cycles RHS LHS BOTH MEDIAN NONE AUTO RHS on direct tap with no dropdown and no MANUAL text`() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)
        viewModel.updateSide(RoadSide.RHS)

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

        // Step 0: Starts on RHS
        composeTestRule.onNodeWithTag("side_chip").assertIsDisplayed().assertTextEquals("RHS")
        assertNoManualOrDropdownUiPresent()

        // Step 1: Tap -> LHS
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("side_chip").assertTextEquals("LHS")
        assertEquals(RoadSide.LHS, viewModel.projectProfile.value.roadSide)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        assertNoManualOrDropdownUiPresent()

        // Step 2: Tap -> BOTH
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("side_chip").assertTextEquals("BOTH")
        assertEquals(RoadSide.BOTH, viewModel.projectProfile.value.roadSide)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        assertNoManualOrDropdownUiPresent()

        // Step 3: Tap -> MEDIAN
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("side_chip").assertTextEquals("MEDIAN")
        assertEquals(RoadSide.MEDIAN, viewModel.projectProfile.value.roadSide)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        assertNoManualOrDropdownUiPresent()

        // Step 4: Tap -> NONE
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("side_chip").assertTextEquals("NONE")
        assertEquals(RoadSide.NONE, viewModel.projectProfile.value.roadSide)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        assertNoManualOrDropdownUiPresent()

        // Step 5: Tap -> AUTO
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(200L)
        composeTestRule.onNodeWithTag("side_chip").assertTextEquals("AUTO")
        assertFalse("AUTO mode must clear manual override so GPS/KMZ auto detection runs", viewModel.smartDirectionState.value.isManualOverride)
        assertNoManualOrDropdownUiPresent()

        // Step 6: Tap -> wraps back to RHS
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("side_chip").assertTextEquals("RHS")
        assertEquals(RoadSide.RHS, viewModel.projectProfile.value.roadSide)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        assertNoManualOrDropdownUiPresent()

        // Metadata snapshot stays synchronized cleanly
        val snapshot = viewModel.createCurrentSnapshot()
        assertEquals("RHS", snapshot.matchResult.smartDirectionState?.confirmedSide)
    }

    @Test
    @Config(qualifiers = "w800dp-h400dp-land")
    fun `Landscape CameraScreen cycles RHS LHS BOTH MEDIAN NONE AUTO RHS on direct tap with no dropdown no MANUAL text and aligned pill`() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)
        viewModel.updateSide(RoadSide.RHS)

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

        // Verify alignment & consistent sizing with sibling Landscape Highway Function pill
        val sideBounds = composeTestRule.onNodeWithTag("landscape_side_pill").assertIsDisplayed().getBoundsInRoot()
        val laneBounds = composeTestRule.onNodeWithTag("landscape_lane_pill").assertIsDisplayed().getBoundsInRoot()
        assertEquals("Landscape side pill width must match sibling pill width", laneBounds.right - laneBounds.left, sideBounds.right - sideBounds.left)
        assertEquals("Landscape side pill height must match sibling pill height", laneBounds.bottom - laneBounds.top, sideBounds.bottom - sideBounds.top)

        // Step 0: RHS
        composeTestRule.onNodeWithTag("landscape_side_pill").assertTextEquals("RHS")
        assertNoManualOrDropdownUiPresent()

        // Step 1: LHS
        composeTestRule.onNodeWithTag("landscape_side_pill").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("landscape_side_pill").assertTextEquals("LHS")
        assertEquals(RoadSide.LHS, viewModel.projectProfile.value.roadSide)
        assertNoManualOrDropdownUiPresent()

        // Step 2: BOTH
        composeTestRule.onNodeWithTag("landscape_side_pill").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("landscape_side_pill").assertTextEquals("BOTH")
        assertEquals(RoadSide.BOTH, viewModel.projectProfile.value.roadSide)
        assertNoManualOrDropdownUiPresent()

        // Step 3: MEDIAN
        composeTestRule.onNodeWithTag("landscape_side_pill").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("landscape_side_pill").assertTextEquals("MEDIAN")
        assertEquals(RoadSide.MEDIAN, viewModel.projectProfile.value.roadSide)
        assertNoManualOrDropdownUiPresent()

        // Step 4: NONE
        composeTestRule.onNodeWithTag("landscape_side_pill").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("landscape_side_pill").assertTextEquals("NONE")
        assertEquals(RoadSide.NONE, viewModel.projectProfile.value.roadSide)
        assertNoManualOrDropdownUiPresent()

        // Step 5: AUTO
        composeTestRule.onNodeWithTag("landscape_side_pill").performClick()
        advanceAndIdle(200L)
        composeTestRule.onNodeWithTag("landscape_side_pill").assertTextEquals("AUTO")
        assertFalse(viewModel.smartDirectionState.value.isManualOverride)
        assertNoManualOrDropdownUiPresent()

        // Step 6: Wraps to RHS
        composeTestRule.onNodeWithTag("landscape_side_pill").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("landscape_side_pill").assertTextEquals("RHS")
        assertEquals(RoadSide.RHS, viewModel.projectProfile.value.roadSide)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        assertNoManualOrDropdownUiPresent()
    }
}
