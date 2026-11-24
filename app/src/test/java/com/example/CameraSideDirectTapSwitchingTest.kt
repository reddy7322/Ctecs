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
import com.example.model.DirectionDetectionState
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.SmartDirectionState
import com.example.overlay.OverlayTextGenerator
import com.example.ui.MainViewModel
import com.example.ui.camera.CameraScreen
import com.example.ui.camera.CameraSideOption
import com.example.ui.camera.resolveCameraSideDisplayState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CameraSideDirectTapSwitchingTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun advanceAndIdle(ms: Long = 200L) {
        composeTestRule.mainClock.advanceTimeBy(ms)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertNoAutoManualOrDropdownUiPresent() {
        // Zero visible MANUAL / AUTO / dropdown labels anywhere on Camera Screen
        assertEquals(0, composeTestRule.onAllNodesWithText("MANUAL", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("MAN▾", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("• M", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("AUTO", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Auto (Smart Direction)", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("Smart Direction Mode", substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `CameraSideOption cycles strictly in LHS RHS BHS MEDIAN NONE LHS order and never exposes AUTO`() {
        assertEquals(
            listOf(CameraSideOption.LHS, CameraSideOption.RHS, CameraSideOption.BHS, CameraSideOption.MEDIAN, CameraSideOption.NONE),
            CameraSideOption.entries
        )

        val sequence = buildList {
            var current = CameraSideOption.LHS
            repeat(6) {
                add(current)
                current = current.next()
            }
        }
        assertEquals(
            listOf(
                CameraSideOption.LHS,
                CameraSideOption.RHS,
                CameraSideOption.BHS,
                CameraSideOption.MEDIAN,
                CameraSideOption.NONE,
                CameraSideOption.LHS
            ),
            sequence
        )

        // Underlying AUTO mode with confirmed LHS displays ONLY "LHS" (never "AUTO"), and tapping advances to RHS
        val autoLhs = resolveCameraSideDisplayState(
            SmartDirectionState(
                detectionState = DirectionDetectionState.LHS_CONFIRMED,
                isManualOverride = false,
                confirmedSide = "LHS"
            ),
            ProjectProfile(roadSide = RoadSide.RHS)
        )
        assertEquals("LHS", autoLhs.displayLabel)
        assertEquals(CameraSideOption.LHS, autoLhs.activeOption)
        assertEquals(CameraSideOption.RHS, autoLhs.nextOptionOnTap())

        // Underlying AUTO mode with confirmed RHS displays ONLY "RHS", and tapping advances to BHS
        val autoRhs = resolveCameraSideDisplayState(
            SmartDirectionState(
                detectionState = DirectionDetectionState.RHS_CONFIRMED,
                isManualOverride = false,
                confirmedSide = "RHS"
            ),
            ProjectProfile(roadSide = RoadSide.LHS)
        )
        assertEquals("RHS", autoRhs.displayLabel)
        assertEquals(CameraSideOption.RHS, autoRhs.activeOption)
        assertEquals(CameraSideOption.BHS, autoRhs.nextOptionOnTap())

        // Underlying AUTO mode when uncertain / weak GPS never shows "AUTO" and never guesses LHS/RHS
        val autoUncertain = resolveCameraSideDisplayState(
            SmartDirectionState(
                detectionState = DirectionDetectionState.DIRECTION_UNCERTAIN,
                isManualOverride = false,
                confirmedSide = null
            ),
            ProjectProfile(roadSide = RoadSide.RHS)
        )
        assertEquals("---", autoUncertain.displayLabel)
        assertNull(autoUncertain.activeOption)
        assertEquals(CameraSideOption.LHS, autoUncertain.nextOptionOnTap())

        // Manual BHS, MEDIAN & NONE map cleanly
        val manualBhs = resolveCameraSideDisplayState(
            SmartDirectionState(isManualOverride = true, confirmedSide = "BHS"),
            ProjectProfile(roadSide = RoadSide.BOTH)
        )
        assertEquals("BHS", manualBhs.displayLabel)
        assertEquals(CameraSideOption.MEDIAN, manualBhs.nextOptionOnTap())

        val manualMedian = resolveCameraSideDisplayState(
            SmartDirectionState(isManualOverride = true, confirmedSide = "MEDIAN"),
            ProjectProfile(roadSide = RoadSide.MEDIAN)
        )
        assertEquals("MEDIAN", manualMedian.displayLabel)
        assertEquals(CameraSideOption.NONE, manualMedian.nextOptionOnTap())

        val manualNone = resolveCameraSideDisplayState(
            SmartDirectionState(isManualOverride = true, confirmedSide = "NONE"),
            ProjectProfile(roadSide = RoadSide.NONE)
        )
        assertEquals("NONE", manualNone.displayLabel)
        assertEquals(CameraSideOption.LHS, manualNone.nextOptionOnTap())
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port")
    fun `Portrait CameraScreen cycles LHS RHS BHS NONE LHS on direct tap with no AUTO or MANUAL text and synchronized capture output`() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)
        viewModel.updateSide(RoadSide.LHS)

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

        // Step 0: Starts on LHS
        composeTestRule.onNodeWithTag("side_chip").assertIsDisplayed().assertTextEquals("LHS")
        assertNoAutoManualOrDropdownUiPresent()
        assertEquals("LHS", OverlayTextGenerator.resolveAuthoritativeCaptureSide(viewModel.createCurrentSnapshot()).first)

        // Step 1: Tap -> RHS
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("side_chip").assertTextEquals("RHS")
        assertEquals(RoadSide.RHS, viewModel.projectProfile.value.roadSide)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        assertEquals("RHS", OverlayTextGenerator.resolveAuthoritativeCaptureSide(viewModel.createCurrentSnapshot()).first)
        assertNoAutoManualOrDropdownUiPresent()

        // Step 2: Tap -> BHS
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("side_chip").assertTextEquals("BHS")
        assertEquals(RoadSide.BOTH, viewModel.projectProfile.value.roadSide)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        assertEquals("BHS", OverlayTextGenerator.resolveAuthoritativeCaptureSide(viewModel.createCurrentSnapshot()).first)
        assertNoAutoManualOrDropdownUiPresent()

        // Step 3: Tap -> MEDIAN
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("side_chip").assertTextEquals("MEDIAN")
        assertEquals(RoadSide.MEDIAN, viewModel.projectProfile.value.roadSide)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        assertEquals("MEDIAN", OverlayTextGenerator.resolveAuthoritativeCaptureSide(viewModel.createCurrentSnapshot()).first)
        assertNoAutoManualOrDropdownUiPresent()

        // Step 4: Tap -> NONE (omits side cleanly from capture output!)
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("side_chip").assertTextEquals("NONE")
        assertEquals(RoadSide.NONE, viewModel.projectProfile.value.roadSide)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        assertNull(
            "NONE must omit side field from capture overlay/metadata",
            OverlayTextGenerator.resolveAuthoritativeCaptureSide(viewModel.createCurrentSnapshot()).first
        )
        assertNoAutoManualOrDropdownUiPresent()

        // Step 5: Tap -> wraps cleanly back to LHS
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("side_chip").assertTextEquals("LHS")
        assertEquals(RoadSide.LHS, viewModel.projectProfile.value.roadSide)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        assertEquals("LHS", OverlayTextGenerator.resolveAuthoritativeCaptureSide(viewModel.createCurrentSnapshot()).first)
        assertNoAutoManualOrDropdownUiPresent()

        // Underlying resetSideToAuto still works internally and NEVER displays "AUTO" on Camera Screen
        viewModel.resetSideToAuto()
        advanceAndIdle(200L)
        assertFalse(viewModel.smartDirectionState.value.isManualOverride)
        assertNoAutoManualOrDropdownUiPresent()
    }

    @Test
    @Config(qualifiers = "w800dp-h400dp-land")
    fun `Landscape CameraScreen cycles LHS RHS BHS NONE LHS on direct tap with aligned pill and no AUTO or MANUAL text`() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)
        viewModel.updateSide(RoadSide.LHS)

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

        // Step 0: LHS
        composeTestRule.onNodeWithTag("landscape_side_pill").assertTextEquals("LHS")
        assertNoAutoManualOrDropdownUiPresent()

        // Step 1: RHS
        composeTestRule.onNodeWithTag("landscape_side_pill").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("landscape_side_pill").assertTextEquals("RHS")
        assertEquals(RoadSide.RHS, viewModel.projectProfile.value.roadSide)
        assertNoAutoManualOrDropdownUiPresent()

        // Step 2: BHS
        composeTestRule.onNodeWithTag("landscape_side_pill").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("landscape_side_pill").assertTextEquals("BHS")
        assertEquals(RoadSide.BOTH, viewModel.projectProfile.value.roadSide)
        assertNoAutoManualOrDropdownUiPresent()

        // Step 3: MEDIAN
        composeTestRule.onNodeWithTag("landscape_side_pill").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("landscape_side_pill").assertTextEquals("MEDIAN")
        assertEquals(RoadSide.MEDIAN, viewModel.projectProfile.value.roadSide)
        assertNoAutoManualOrDropdownUiPresent()

        // Step 4: NONE
        composeTestRule.onNodeWithTag("landscape_side_pill").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("landscape_side_pill").assertTextEquals("NONE")
        assertEquals(RoadSide.NONE, viewModel.projectProfile.value.roadSide)
        assertNoAutoManualOrDropdownUiPresent()

        // Step 5: Wraps back to LHS
        composeTestRule.onNodeWithTag("landscape_side_pill").performClick()
        advanceAndIdle(150L)
        composeTestRule.onNodeWithTag("landscape_side_pill").assertTextEquals("LHS")
        assertEquals(RoadSide.LHS, viewModel.projectProfile.value.roadSide)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        assertNoAutoManualOrDropdownUiPresent()
    }
}
