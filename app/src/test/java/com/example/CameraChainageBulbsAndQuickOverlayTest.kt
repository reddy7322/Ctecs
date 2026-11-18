package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.MatchConfidence
import com.example.model.RoadSide
import com.example.ui.MainViewModel
import com.example.ui.camera.CameraScreen
import com.example.ui.camera.ChainageBubbleStatus
import com.example.ui.camera.ChainageIndicatorsState
import com.example.ui.camera.ChainageStatusBubblesRow
import com.example.ui.camera.resolveChainageIndicatorsState
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CameraChainageBulbsAndQuickOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun advanceAndIdle(ms: Long = 350L) {
        composeTestRule.mainClock.advanceTimeBy(ms)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun expectStateDescription(expected: String): SemanticsMatcher {
        return SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected)
    }

    @Test
    fun `ChainageStatusBubblesRow shows animated bulbs without visible KMZ CUSTOM or MANUAL labels`() {
        composeTestRule.mainClock.autoAdvance = false

        var currentState by mutableStateOf(
            ChainageIndicatorsState(
                displayChainage = "198+880",
                kmzStatus = ChainageBubbleStatus.ACTIVE,
                customStatus = ChainageBubbleStatus.INACTIVE,
                manualStatus = ChainageBubbleStatus.INACTIVE
            )
        )

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                ChainageStatusBubblesRow(
                    state = currentState,
                    isDarkTheme = true,
                    isScreenVisible = true,
                    showInactiveBubbles = false,
                    showLabels = false,
                    tagPrefix = "hud_portrait_"
                )
            }
        }
        advanceAndIdle(100L)

        // 1. KMZ Active -> green animated bulb only, NO "KMZ" / "CUSTOM" / "MANUAL" text
        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_kmz", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(expectStateDescription("ANIMATED_ACTIVE"))
        composeTestRule.onNodeWithContentDescription("KMZ chainage active").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_custom", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_manual", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, composeTestRule.onAllNodesWithText("KMZ", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("CUSTOM", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("MANUAL", substring = true, ignoreCase = true).fetchSemanticsNodes().size)

        // 2. KMZ + Custom Correction Active -> green + purple animated bulbs, NO text
        currentState = resolveChainageIndicatorsState(
            matchResult = ChainageMatchResult(
                chainage = Chainage(198_910L),
                rawUncorrectedChainage = Chainage(198_880L),
                confidence = MatchConfidence.HIGH,
                source = ChainageSource.CUSTOM_CORRECTION
            ),
            manualChainageText = null,
            hasActiveRoute = true,
            isCorrectionsEnabled = true
        )
        advanceAndIdle(150L)

        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_kmz", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(expectStateDescription("ANIMATED_ACTIVE"))
        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_custom", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(expectStateDescription("ANIMATED_ACTIVE"))
        composeTestRule.onNodeWithContentDescription("Custom correction chainage active").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_manual", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, composeTestRule.onAllNodesWithText("KMZ", substring = true, ignoreCase = true).fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("CUSTOM", substring = true, ignoreCase = true).fetchSemanticsNodes().size)

        // 3. Manual Chainage Active -> yellow animated bulb ONLY, NO KMZ/Custom bulbs, NO text
        currentState = resolveChainageIndicatorsState(
            matchResult = ChainageMatchResult.noRoute(),
            manualChainageText = "205+500",
            hasActiveRoute = true,
            isCorrectionsEnabled = true
        )
        advanceAndIdle(150L)

        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_manual", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(expectStateDescription("ANIMATED_ACTIVE"))
        composeTestRule.onNodeWithContentDescription("Manual chainage active").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_kmz", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_custom", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(0, composeTestRule.onAllNodesWithText("MANUAL", substring = true, ignoreCase = true).fetchSemanticsNodes().size)

        // 4. No active source -> zero bulbs displayed
        currentState = resolveChainageIndicatorsState(
            matchResult = ChainageMatchResult.noRoute(),
            manualChainageText = null,
            hasActiveRoute = false,
            isCorrectionsEnabled = true
        )
        advanceAndIdle(150L)

        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_kmz", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_custom", useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_manual", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp-port")
    fun `Portrait CameraScreen collapses Quick Overlay Settings initially toggles cleanly and keeps Chainage visible`() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)
        viewModel.setManualChainage("199+250")
        var sessionKey by mutableIntStateOf(0)

        composeTestRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                Box(modifier = Modifier.fillMaxSize()) {
                    key(sessionKey) {
                        CameraScreen(
                            viewModel = viewModel,
                            onNavigateToPhotos = {}
                        )
                    }
                }
            }
        }
        advanceAndIdle(200L)

        // Initially collapsed: Quick Overlay toggle & always-visible chainage + animated yellow manual bulb are shown
        composeTestRule.onNodeWithTag("portrait_quick_overlay_toggle")
            .assertIsDisplayed()
            .assert(expectStateDescription("Collapsed"))
        composeTestRule.onNodeWithTag("chainage_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_chainage_value", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_manual", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(expectStateDescription("ANIMATED_ACTIVE"))
        composeTestRule.onNodeWithTag("shutter_button").assertIsDisplayed()

        // Quick overlay chips must NOT be displayed initially
        composeTestRule.onNodeWithTag("side_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("lane_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("activity_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("custom_comment_chip").assertDoesNotExist()

        // No permanent KMZ / CUSTOM / MANUAL text labels on Camera HUD
        assertEquals(0, composeTestRule.onAllNodesWithText("KMZ").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("CUSTOM").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("MANUAL").fetchSemanticsNodes().size)

        // Tap once -> Smoothly expands all quick overlay controls
        composeTestRule.onNodeWithTag("portrait_quick_overlay_toggle").performClick()
        advanceAndIdle(400L)

        composeTestRule.onNodeWithTag("portrait_quick_overlay_toggle")
            .assert(expectStateDescription("Expanded"))
        composeTestRule.onNodeWithTag("side_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lane_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("activity_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("custom_comment_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chainage_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shutter_button").assertIsDisplayed()

        // Interactive options inside expanded Quick Overlay still work
        val initialSide = viewModel.projectProfile.value.roadSide
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(150L)
        assertEquals(
            when (initialSide) {
                RoadSide.RHS -> RoadSide.LHS
                RoadSide.LHS -> RoadSide.BOTH
                RoadSide.BOTH -> RoadSide.MEDIAN
                RoadSide.MEDIAN -> RoadSide.NONE
                RoadSide.NONE -> RoadSide.RHS
            },
            viewModel.projectProfile.value.roadSide
        )

        // Tap again -> Smoothly collapses back
        composeTestRule.onNodeWithTag("portrait_quick_overlay_toggle").performClick()
        advanceAndIdle(400L)

        composeTestRule.onNodeWithTag("portrait_quick_overlay_toggle")
            .assert(expectStateDescription("Collapsed"))
        composeTestRule.onNodeWithTag("side_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("lane_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("activity_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("custom_comment_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("chainage_chip").assertIsDisplayed()

        // Expand then re-enter CameraScreen -> must reset to collapsed
        composeTestRule.onNodeWithTag("portrait_quick_overlay_toggle").performClick()
        advanceAndIdle(400L)
        composeTestRule.onNodeWithTag("side_chip").assertIsDisplayed()

        sessionKey++
        advanceAndIdle(200L)
        composeTestRule.onNodeWithTag("portrait_quick_overlay_toggle")
            .assert(expectStateDescription("Collapsed"))
        composeTestRule.onNodeWithTag("side_chip").assertDoesNotExist()
        composeTestRule.onNodeWithTag("chainage_chip").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    fun `Landscape CameraScreen collapses Quick Overlay Settings initially toggles cleanly and keeps Chainage visible`() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)
        viewModel.setManualChainage("202+100")

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

        // Initially collapsed in Landscape
        composeTestRule.onNodeWithTag("landscape_quick_overlay_toggle")
            .assertIsDisplayed()
            .assert(expectStateDescription("Collapsed"))
        composeTestRule.onNodeWithTag("landscape_chainage_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_landscape_chainage_value", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_landscape_chainage_bulb_manual", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(expectStateDescription("ANIMATED_ACTIVE"))
        composeTestRule.onNodeWithTag("landscape_shutter_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("landscape_side_pill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("landscape_lane_pill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("landscape_activity_pill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("landscape_comment_pill").assertDoesNotExist()

        assertEquals(0, composeTestRule.onAllNodesWithText("KMZ").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("CUSTOM").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("MANUAL").fetchSemanticsNodes().size)

        // Expand in Landscape
        composeTestRule.onNodeWithTag("landscape_quick_overlay_toggle").performClick()
        advanceAndIdle(400L)

        composeTestRule.onNodeWithTag("landscape_quick_overlay_toggle")
            .assert(expectStateDescription("Expanded"))
        composeTestRule.onNodeWithTag("landscape_side_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_lane_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_activity_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_comment_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_chainage_pill").assertIsDisplayed()

        // Collapse again in Landscape
        composeTestRule.onNodeWithTag("landscape_quick_overlay_toggle").performClick()
        advanceAndIdle(400L)

        composeTestRule.onNodeWithTag("landscape_quick_overlay_toggle")
            .assert(expectStateDescription("Collapsed"))
        composeTestRule.onNodeWithTag("landscape_side_pill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("landscape_lane_pill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("landscape_activity_pill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("landscape_comment_pill").assertDoesNotExist()
        composeTestRule.onNodeWithTag("landscape_chainage_pill").assertIsDisplayed()
    }
}
