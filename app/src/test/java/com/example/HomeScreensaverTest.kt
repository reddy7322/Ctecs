package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.ui.home.ChainageScreensaverOverlay
import com.example.ui.home.HOME_SCREENSAVER_INACTIVITY_TIMEOUT_MS
import com.example.ui.home.HomeScreensaverCoordinator
import com.example.ui.home.ScreensaverChainageStateHolder
import com.example.ui.home.shouldAllowScreensaver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HomeScreensaverTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleRoute(): RouteData {
        return RouteData(
            id = "nh58-route",
            name = "Muzzafarnagar-Haridwar-NH-58-50m.kmz",
            fileName = "Muzzafarnagar-Haridwar-NH-58-50m.kmz",
            markers = emptyList(),
            alignments = listOf(
                RouteAlignment(
                    id = "align-1",
                    name = "NH-58 Mainline",
                    points = listOf(
                        GeoPoint(29.5000, 77.7000),
                        GeoPoint(29.5050, 77.7020),
                        GeoPoint(29.5100, 77.7040)
                    ),
                    direction = "INCREASING"
                )
            ),
            bounds = GeoBounds(29.49, 29.52, 77.69, 77.71),
            startChainage = Chainage(199_000L),
            endChainage = Chainage(203_000L)
        )
    }

    @Test
    fun `screensaver state holder retains last valid chainage on GPS loss and suppresses unreliable direction`() {
        val holder = ScreensaverChainageStateHolder()
        val route = sampleRoute()

        // 1. Live reliable KMZ match on LHS
        val liveLhs = holder.resolve(
            matchResult = ChainageMatchResult(
                chainage = Chainage(200_050L),
                confidence = MatchConfidence.HIGH,
                detectedSide = "LHS",
                source = ChainageSource.KMZ
            ),
            route = route,
            hasGpsFix = true,
            projectRoadSide = RoadSide.NONE
        )
        assertEquals("200+050", liveLhs.chainageText)
        assertEquals("LHS", liveLhs.reliableSide)
        assertEquals("↑", liveLhs.directionArrow)
        assertTrue(liveLhs.isLiveGpsValid)

        // 2. Dynamic transition from LHS -> RHS
        val liveRhs = holder.resolve(
            matchResult = ChainageMatchResult(
                chainage = Chainage(200_120L),
                confidence = MatchConfidence.HIGH,
                detectedSide = "RHS",
                source = ChainageSource.KMZ
            ),
            route = route,
            hasGpsFix = true,
            projectRoadSide = RoadSide.NONE
        )
        assertEquals("200+120", liveRhs.chainageText)
        assertEquals("RHS", liveRhs.reliableSide)
        assertEquals("↓", liveRhs.directionArrow)

        // 3. Temporary GPS loss: retains "200+120" (never fabricates new values) and hides unreliable direction
        val gpsLoss = holder.resolve(
            matchResult = ChainageMatchResult(
                chainage = Chainage(999_999L), // Should never be used when GPS fix is lost!
                confidence = MatchConfidence.NO_MATCH,
                detectedSide = "LHS",
                source = ChainageSource.KMZ
            ),
            route = route,
            hasGpsFix = false,
            projectRoadSide = RoadSide.LHS
        )
        assertEquals("200+120", gpsLoss.chainageText)
        assertNull(gpsLoss.reliableSide)
        assertEquals("", gpsLoss.directionArrow)
        assertFalse(gpsLoss.isLiveGpsValid)

        // 4. Uncertain match with Unknown side: retains chainage without showing false direction
        val uncertain = holder.resolve(
            matchResult = ChainageMatchResult(
                chainage = Chainage(200_500L),
                confidence = MatchConfidence.LOW,
                isUncertain = true,
                detectedSide = "Unknown"
            ),
            route = route,
            hasGpsFix = true,
            projectRoadSide = RoadSide.NONE
        )
        assertEquals("200+120", uncertain.chainageText)
        assertNull(uncertain.reliableSide)
    }

    @Test
    fun `screensaver gating predicate blocks dialogs, keyboard, active input and background state`() {
        assertTrue(
            shouldAllowScreensaver(
                isLifecycleResumed = true,
                isDialogOpen = false,
                isKeyboardOpen = false,
                isInputFocused = false
            )
        )
        assertFalse(
            shouldAllowScreensaver(
                isLifecycleResumed = false,
                isDialogOpen = false,
                isKeyboardOpen = false,
                isInputFocused = false
            )
        )
        assertFalse(
            shouldAllowScreensaver(
                isLifecycleResumed = true,
                isDialogOpen = true,
                isKeyboardOpen = false,
                isInputFocused = false
            )
        )
        assertFalse(
            shouldAllowScreensaver(
                isLifecycleResumed = true,
                isDialogOpen = false,
                isKeyboardOpen = true,
                isInputFocused = false
            )
        )
        assertFalse(
            shouldAllowScreensaver(
                isLifecycleResumed = true,
                isDialogOpen = false,
                isKeyboardOpen = false,
                isInputFocused = true
            )
        )
    }

    @Test
    fun `screensaver opens after 20s inactivity, closes on touch, preserves scroll, cycles continuously, and blocks during dialogs`() {
        val route = sampleRoute()
        val holder = ScreensaverChainageStateHolder()

        var matchState by mutableStateOf(
            ChainageMatchResult(
                chainage = Chainage(200_050L),
                confidence = MatchConfidence.HIGH,
                detectedSide = "LHS",
                source = ChainageSource.KMZ
            )
        )
        var gpsPointState by mutableStateOf<GeoPoint?>(GeoPoint(29.502, 77.701))
        var isBlockingDialogOpen by mutableStateOf(false)
        var isScreensaverVisible by mutableStateOf(false)
        var showTriggerCount by mutableIntStateOf(0)
        var targetScrollToApply by mutableIntStateOf(0)
        var observedScrollValue by mutableIntStateOf(0)

        composeTestRule.mainClock.autoAdvance = false

        composeTestRule.setContent {
            val scrollState = rememberScrollState()
            observedScrollValue = scrollState.value
            LaunchedEffect(targetScrollToApply) {
                if (targetScrollToApply > 0) {
                    scrollState.scrollTo(targetScrollToApply)
                }
            }

            val displayState = remember(matchState, gpsPointState != null) {
                holder.resolve(
                    matchResult = matchState,
                    route = route,
                    hasGpsFix = gpsPointState != null,
                    projectRoadSide = RoadSide.NONE
                )
            }

            HomeScreensaverCoordinator(
                isBlockingUiOpen = isBlockingDialogOpen,
                inactivityTimeoutMs = HOME_SCREENSAVER_INACTIVITY_TIMEOUT_MS,
                isScreensaverVisible = isScreensaverVisible,
                onShowScreensaver = {
                    showTriggerCount += 1
                    isScreensaverVisible = true
                },
                onDismissScreensaver = {
                    isScreensaverVisible = false
                },
                screensaverContent = { dismiss ->
                    ChainageScreensaverOverlay(
                        displayState = displayState,
                        location = gpsPointState,
                        bearing = 45f,
                        compassHeading = 45f,
                        routeData = route,
                        isDarkAppTheme = true,
                        onDismiss = dismiss
                    )
                }
            ) { interactionModifier ->
                Column(
                    modifier = interactionModifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .testTag("home_scroll_container")
                ) {
                    // Home cards that must NEVER appear inside the screensaver overlay
                    Text("NH-58 Highway Project")
                    Text("Muzzafarnagar-Haridwar Stretch")
                    Text("NEXT KM STONE 201+000")
                    Text("950 m Away")
                    Text("KMZ ACTIVE")
                    Button(
                        onClick = {},
                        modifier = Modifier.testTag("home_action_button")
                    ) {
                        Text("Survey")
                    }
                    Box(modifier = Modifier.fillMaxWidth().height(1200.dp))
                }
            }
        }

        // Scroll Home Page down to verify scroll preservation
        composeTestRule.runOnIdle {
            targetScrollToApply = 450
        }
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        assertEquals(450, observedScrollValue)

        // Reset timer via touch at 15s -> must NOT trigger at 19.5s after reset
        composeTestRule.mainClock.advanceTimeBy(15_000L)
        composeTestRule.onNodeWithTag("home_scroll_container").performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(19_500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_chainage_screensaver").assertDoesNotExist()
        assertEquals(0, showTriggerCount)

        // Advance past exact 20-second mark -> Screensaver appears!
        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_chainage_screensaver").assertIsDisplayed()
        assertEquals(1, showTriggerCount)

        // Verify ONLY the three required elements exist (NO forbidden labels/buttons/telemetry)
        composeTestRule.onNodeWithTag("screensaver_glass_map").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_chainage_text").assertIsDisplayed()
        composeTestRule.onNodeWithTag("screensaver_chainage_text").assertTextEquals("200+050")
        composeTestRule.onNodeWithTag("screensaver_direction_badge").assertIsDisplayed()

        val screensaverRoot = composeTestRule.onNodeWithTag("home_chainage_screensaver").fetchSemanticsNode()
        fun collectTexts(node: androidx.compose.ui.semantics.SemanticsNode): List<String> {
            val own = mutableListOf<String>()
            if (node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Text)) {
                own += node.config[androidx.compose.ui.semantics.SemanticsProperties.Text].map { it.text }
            }
            for (child in node.children) {
                own += collectTexts(child)
            }
            return own
        }
        val renderedTexts = collectTexts(screensaverRoot).joinToString(" ")
        assertTrue(renderedTexts.contains("200+050"))
        assertTrue(renderedTexts.contains("LHS"))
        listOf("NH-58", "Stretch", "NEXT KM", "Away", "KMZ", "CUSTOM", "MANUAL", "GPS", "Survey", "Tap").forEach { forbidden ->
            assertFalse("Screensaver must not contain '$forbidden'", renderedTexts.contains(forbidden, ignoreCase = true))
        }

        // Verify live chainage & direction update dynamically while screensaver is open
        composeTestRule.runOnIdle {
            matchState = ChainageMatchResult(
                chainage = Chainage(200_180L),
                confidence = MatchConfidence.HIGH,
                detectedSide = "RHS",
                source = ChainageSource.KMZ
            )
        }
        composeTestRule.mainClock.advanceTimeBy(400L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screensaver_chainage_text").assertTextEquals("200+180")
        val updatedTexts = collectTexts(
            composeTestRule.onNodeWithTag("home_chainage_screensaver").fetchSemanticsNode()
        ).joinToString(" ")
        assertTrue(updatedTexts.contains("RHS"))

        // Verify GPS loss retains last valid chainage ("200+180") and hides direction indicator
        composeTestRule.runOnIdle {
            gpsPointState = null
            matchState = ChainageMatchResult(
                chainage = null,
                confidence = MatchConfidence.NO_MATCH,
                detectedSide = "Unknown"
            )
        }
        composeTestRule.mainClock.advanceTimeBy(400L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("screensaver_chainage_text").assertTextEquals("200+180")
        composeTestRule.onNodeWithTag("screensaver_direction_badge").assertDoesNotExist()

        // Single touch anywhere on screensaver closes it immediately AND preserves Home scroll position
        composeTestRule.onNodeWithTag("home_chainage_screensaver").performTouchInput { click() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_chainage_screensaver").assertDoesNotExist()
        assertEquals(450, observedScrollValue)

        // Restore GPS & verify continuous second cycle: < 20s stays closed, >= 20s re-opens
        composeTestRule.runOnIdle {
            gpsPointState = GeoPoint(29.504, 77.702)
            matchState = ChainageMatchResult(
                chainage = Chainage(200_250L),
                confidence = MatchConfidence.HIGH,
                detectedSide = "LHS",
                source = ChainageSource.KMZ
            )
        }
        composeTestRule.mainClock.advanceTimeBy(19_500L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_chainage_screensaver").assertDoesNotExist()

        composeTestRule.mainClock.advanceTimeBy(600L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_chainage_screensaver").assertIsDisplayed()
        assertEquals(2, showTriggerCount)

        // Open a blocking modal/dialog -> screensaver closes immediately and stays closed past 20s
        composeTestRule.runOnIdle {
            isBlockingDialogOpen = true
        }
        composeTestRule.mainClock.advanceTimeBy(25_000L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("home_chainage_screensaver").assertDoesNotExist()
        assertEquals(2, showTriggerCount)
    }
}
