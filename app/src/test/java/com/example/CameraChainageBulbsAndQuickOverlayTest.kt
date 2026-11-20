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
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
import org.junit.Assert.assertTrue
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
    fun `Portrait CameraScreen keeps all Highway Functions always visible and interactive with no toggle`() {
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

        // Collapse/expand toggle must NOT exist
        composeTestRule.onNodeWithTag("portrait_quick_overlay_toggle").assertDoesNotExist()

        // All 5 Highway Functions + animated bulb + shutter + timestamp card must be immediately displayed
        composeTestRule.onNodeWithTag("portrait_highway_functions_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("side_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lane_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("activity_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chainage_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("custom_comment_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_chainage_value", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_portrait_chainage_bulb_manual", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(expectStateDescription("ANIMATED_ACTIVE"))
        composeTestRule.onNodeWithTag("shutter_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("portrait_timestamp_card").assertIsDisplayed()

        // No KMZ / CUSTOM / MANUAL text labels on Camera HUD
        assertEquals(0, composeTestRule.onAllNodesWithText("KMZ").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("CUSTOM").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("MANUAL").fetchSemanticsNodes().size)

        // Verify Side chip cycles directly through RHS -> LHS -> BOTH -> MEDIAN -> NONE -> AUTO
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(150L)
        assertEquals(RoadSide.RHS, viewModel.projectProfile.value.roadSide)
        assertTrue(viewModel.smartDirectionState.value.isManualOverride)
        composeTestRule.onNodeWithTag("side_chip").performClick()
        advanceAndIdle(150L)
        assertEquals(RoadSide.LHS, viewModel.projectProfile.value.roadSide)

        // Verify Activity dropdown opens on click and updates activity when preset selected
        composeTestRule.onNodeWithTag("activity_chip").performClick()
        advanceAndIdle(200L)
        composeTestRule.onNodeWithText("EMB").assertIsDisplayed().performClick()
        advanceAndIdle(200L)
        assertEquals("EMB", viewModel.projectProfile.value.activity)

        // Re-entering CameraScreen preserves permanent visibility of all Highway Functions
        sessionKey++
        advanceAndIdle(200L)
        composeTestRule.onNodeWithTag("portrait_quick_overlay_toggle").assertDoesNotExist()
        composeTestRule.onNodeWithTag("side_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lane_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("activity_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chainage_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("custom_comment_chip").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-port")
    fun `Narrow Portrait CameraScreen wraps all 5 Highway Functions cleanly without clipping or overlapping shutter`() {
        composeTestRule.mainClock.autoAdvance = false
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = MainViewModel(app)
        viewModel.setManualChainage("199+250")

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

        listOf("side_chip", "lane_chip", "activity_chip", "chainage_chip", "custom_comment_chip").forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = composeTestRule.onNodeWithTag(tag).getBoundsInRoot()
            assertTrue("$tag right edge ${bounds.right.value} should stay within 320dp", bounds.right.value <= 320f)
            assertTrue("$tag left edge ${bounds.left.value} should be >= 0dp", bounds.left.value >= 0f)
        }

        val highwayBarBounds = composeTestRule.onNodeWithTag("portrait_highway_functions_bar").getBoundsInRoot()
        val shutterBounds = composeTestRule.onNodeWithTag("shutter_button").getBoundsInRoot()
        assertTrue(
            "Highway functions bar bottom (${highwayBarBounds.bottom.value}) should stay above shutter top (${shutterBounds.top.value})",
            highwayBarBounds.bottom.value < shutterBounds.top.value
        )
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land")
    fun `Landscape CameraScreen keeps all Highway Functions always visible without toggle while preserving top toolbar toggle`() {
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

        // Quick overlay toggle must NOT exist in Landscape
        composeTestRule.onNodeWithTag("landscape_quick_overlay_toggle").assertDoesNotExist()

        // All 5 Highway Functions must be immediately visible
        composeTestRule.onNodeWithTag("landscape_highway_functions_column").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_side_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_lane_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_activity_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_chainage_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_comment_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_landscape_chainage_value", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("hud_landscape_chainage_bulb_manual", useUnmergedTree = true)
            .assertIsDisplayed()
            .assert(expectStateDescription("ANIMATED_ACTIVE"))
        composeTestRule.onNodeWithTag("landscape_shutter_button").assertIsDisplayed()

        // Confirm pills fit vertically within 411dp screen height without clipping
        val commentBounds = composeTestRule.onNodeWithTag("landscape_comment_pill").getBoundsInRoot()
        assertTrue("Comment pill bottom (${commentBounds.bottom.value}) should stay within 411dp", commentBounds.bottom.value <= 411f)

        // Top hardware toolbar toggle starts expanded and toggles independently without hiding Highway Functions
        composeTestRule.onNodeWithTag("landscape_toolbar_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_flash_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_toolbar_toggle").performClick()
        advanceAndIdle(600L)
        composeTestRule.onNodeWithTag("landscape_flash_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("landscape_toolbar_toggle").performClick()
        advanceAndIdle(600L)
        composeTestRule.onNodeWithTag("landscape_flash_button").assertIsDisplayed()

        // All 5 Highway Functions remain visible while top toolbar is expanded
        composeTestRule.onNodeWithTag("landscape_side_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_lane_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_activity_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_chainage_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("landscape_comment_pill").assertIsDisplayed()

        // No KMZ / CUSTOM / MANUAL labels
        assertEquals(0, composeTestRule.onAllNodesWithText("KMZ").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("CUSTOM").fetchSemanticsNodes().size)
        assertEquals(0, composeTestRule.onAllNodesWithText("MANUAL").fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = "w720dp-h220dp-land")
    fun `Ultra-short Landscape CameraScreen dynamically rearranges all 5 Highway Functions into 2-column FlowRow without vertical clipping`() {
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

        listOf(
            "landscape_side_pill",
            "landscape_lane_pill",
            "landscape_activity_pill",
            "landscape_chainage_pill",
            "landscape_comment_pill"
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed()
            val bounds = composeTestRule.onNodeWithTag(tag).getBoundsInRoot()
            assertTrue("$tag bottom (${bounds.bottom.value}) should stay within 220dp", bounds.bottom.value <= 220f)
        }
    }
}
