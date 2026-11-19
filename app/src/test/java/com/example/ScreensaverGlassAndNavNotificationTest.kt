package com.example

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.example.location.LocationState
import com.example.location.LocationStatus
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.GeoBounds
import com.example.model.GeoPoint
import com.example.model.HomeScreensaverSettings
import com.example.model.HudDirectionPosition
import com.example.model.HudElementSize
import com.example.model.HudFontStyle
import com.example.model.MatchConfidence
import com.example.model.NavigationNotificationSettings
import com.example.model.ProjectProfile
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import com.example.model.ScreensaverDisplayMode
import com.example.navigation.LockScreenNavStateResolver
import com.example.navigation.LockScreenNavigationService
import com.example.navigation.LockScreenNotificationFormatter
import com.example.ui.MainViewModel
import com.example.ui.home.ChainageScreensaverOverlay
import com.example.ui.home.DashboardMirrorScaleXKey
import com.example.ui.home.ScreensaverDisplayState
import com.example.ui.settings.NavigationNotificationSettingsSectionContent
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreensaverGlassAndNavNotificationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun sampleRoute(): RouteData {
        return RouteData(
            id = "route-glass-test",
            name = "NH-334 Roorkee",
            fileName = "nh334.kmz",
            markers = emptyList(),
            alignments = listOf(
                RouteAlignment(
                    id = "mainline",
                    name = "Mainline",
                    points = listOf(
                        GeoPoint(29.850, 77.880),
                        GeoPoint(29.860, 77.890)
                    ),
                    direction = "INCREASING"
                )
            ),
            bounds = GeoBounds(29.84, 29.87, 77.87, 77.90),
            startChainage = Chainage(190_000L),
            endChainage = Chainage(210_000L)
        )
    }

    private fun rectsOverlap(a: Rect, b: Rect, tolerancePx: Float = 2f): Boolean {
        return a.left < b.right - tolerancePx &&
            a.right > b.left + tolerancePx &&
            a.top < b.bottom - tolerancePx &&
            a.bottom > b.top + tolerancePx
    }

    @Test
    fun `screensaver chainage never clips across lengths orientations extra large digital or mirror mode`() {
        val testChainages = listOf("9+950", "96+067", "196+494", "196+999", "201+860")
        val viewports = listOf(
            Triple(320.dp, 640.dp, ScreensaverDisplayMode.PORTRAIT),
            Triple(640.dp, 360.dp, ScreensaverDisplayMode.LANDSCAPE),
            Triple(800.dp, 380.dp, ScreensaverDisplayMode.MIRROR)
        )

        var activeChainage by mutableStateOf("196+494")
        var viewportW by mutableStateOf(640.dp)
        var viewportH by mutableStateOf(360.dp)
        var activeSettings by mutableStateOf(
            HomeScreensaverSettings(
                displayMode = ScreensaverDisplayMode.LANDSCAPE,
                autoOrientationEnabled = false,
                chainageFontSize = HudElementSize.EXTRA_LARGE,
                chainageFontStyle = HudFontStyle.DIGITAL,
                directionPosition = HudDirectionPosition.INLINE,
                showSpeedometer = true,
                showDirectionSide = true,
                showLocation = true,
                miniMapVisible = true
            )
        )

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = viewportW, height = viewportH)) {
                ChainageScreensaverOverlay(
                    displayState = ScreensaverDisplayState(
                        chainageText = activeChainage,
                        reliableSide = "RHS",
                        directionArrow = "↓",
                        isLiveGpsValid = true,
                        isIncreasingTrend = false,
                        locationName = "Badrabad-Rajputna Road",
                        speedText = "72 km/h",
                        speedKmh = 72
                    ),
                    location = GeoPoint(29.855, 77.885),
                    bearing = 180f,
                    compassHeading = 180f,
                    routeData = sampleRoute(),
                    screensaverSettings = activeSettings,
                    onToggleMirrorQuickMode = { _, _ -> },
                    onDismiss = {}
                )
            }
        }

        for ((w, h, mode) in viewports) {
            for (ch in testChainages) {
                composeTestRule.runOnIdle {
                    viewportW = w
                    viewportH = h
                    activeChainage = ch
                    activeSettings = activeSettings.copy(
                        displayMode = mode,
                        mirrorModePreferred = (mode == ScreensaverDisplayMode.MIRROR)
                    )
                }
                composeTestRule.waitForIdle()

                val rootNode = composeTestRule.onNodeWithTag("screensaver_dashboard_root").fetchSemanticsNode()
                val chainageNode = composeTestRule.onNodeWithTag("screensaver_chainage_text").fetchSemanticsNode()
                val dirNode = composeTestRule.onNodeWithTag("screensaver_direction_badge").fetchSemanticsNode()
                val locNode = composeTestRule.onNodeWithTag("screensaver_location_name").fetchSemanticsNode()

                composeTestRule.onNodeWithTag("screensaver_chainage_text").assertIsDisplayed()
                composeTestRule.onNodeWithTag("screensaver_chainage_text").assertTextEquals(ch)

                val rootRect = rootNode.boundsInRoot
                val chainageRect = chainageNode.boundsInRoot
                val dirRect = dirNode.boundsInRoot
                val locRect = locNode.boundsInRoot

                assertTrue(
                    "Chainage '$ch' clipped on left for $mode (${w}x$h): chainage=$chainageRect root=$rootRect",
                    chainageRect.left >= rootRect.left - 1f
                )
                assertTrue(
                    "Chainage '$ch' clipped on right for $mode (${w}x$h): chainage=$chainageRect root=$rootRect",
                    chainageRect.right <= rootRect.right + 1f
                )
                assertTrue(
                    "Chainage '$ch' clipped vertically for $mode (${w}x$h): chainage=$chainageRect root=$rootRect",
                    chainageRect.top >= rootRect.top - 1f && chainageRect.bottom <= rootRect.bottom + 1f
                )
                assertFalse(
                    "Chainage '$ch' overlapped direction badge in $mode (${w}x$h)",
                    rectsOverlap(chainageRect, dirRect)
                )
                assertFalse(
                    "Chainage '$ch' overlapped location label in $mode (${w}x$h)",
                    rectsOverlap(chainageRect, locRect)
                )

                val expectedScaleX = if (mode == ScreensaverDisplayMode.MIRROR) -1f else 1f
                assertEquals(expectedScaleX, rootNode.config[DashboardMirrorScaleXKey], 0.001f)
            }
        }
    }

    @Test
    fun `notification formatter produces exact structured text, zero-speed, stale-GPS status, and obeys all toggles`() {
        val resolver = LockScreenNavStateResolver()
        val t0 = 1_700_000_000_000L
        val stationaryRhs = resolver.resolve(
            locationState = LocationState(
                point = GeoPoint(29.855, 77.885),
                accuracy = 4.0f,
                speed = 0.0f,
                status = LocationStatus.READY,
                lastFixTime = t0
            ),
            matchResult = ChainageMatchResult(
                chainage = Chainage(196_494L),
                confidence = MatchConfidence.HIGH,
                detectedSide = "RHS",
                source = ChainageSource.KMZ
            ),
            routeData = sampleRoute(),
            projectProfile = ProjectProfile(highwayId = "NH-334", roadSide = RoadSide.RHS),
            locationName = "Roorkee Bypass",
            nowMs = t0
        )

        val defaultFormatted = LockScreenNotificationFormatter.format(stationaryRhs)
        assertEquals("CH 196+494 | RHS ↓", defaultFormatted.title)
        assertEquals("Next KM 196+000 | 494 m | 0 km/h", defaultFormatted.contentText)
        assertEquals("196+494", defaultFormatted.shortCriticalText)
        assertTrue(defaultFormatted.expandedText?.contains("Location: Roorkee Bypass") == true)
        assertTrue(defaultFormatted.expandedText?.contains("GPS: Live tracking") == true)

        // Disable road side & next KM & expanded & live chip -> toggles strictly respected
        val stripped = LockScreenNotificationFormatter.format(
            navState = stationaryRhs,
            settings = NavigationNotificationSettings(
                showChainage = true,
                showRoadSide = false,
                showNextKmMarker = false,
                showDistanceRemaining = true,
                showSpeed = false,
                showLocation = false,
                expandedInfoEnabled = false,
                liveNotificationEnabled = false
            )
        )
        assertEquals("CH 196+494", stripped.title)
        assertEquals("494 m", stripped.contentText)
        assertNull(stripped.expandedText)
        assertNull(stripped.shortCriticalText)

        // Lost / stale GPS -> shows "-- km/h" cleanly and reports last GPS update in expanded text
        val staleGps = resolver.resolve(
            locationState = LocationState(point = null, status = LocationStatus.ACQUIRING),
            matchResult = ChainageMatchResult(confidence = MatchConfidence.NO_MATCH),
            routeData = sampleRoute(),
            projectProfile = ProjectProfile(highwayId = "NH-334", roadSide = RoadSide.RHS),
            locationName = null,
            nowMs = t0 + 25_000L
        )
        val staleFormatted = LockScreenNotificationFormatter.format(staleGps)
        assertEquals("-- km/h", staleFormatted.contentText)
        assertTrue(staleFormatted.expandedText?.contains("Last GPS update") == true)
    }

    @Test
    fun `navigation service uses monochrome vector icon, live update channel, and lock screen visibility toggles`() {
        val controller = Robolectric.buildService(LockScreenNavigationService::class.java).create()
        val service = controller.get()

        val resolver = LockScreenNavStateResolver()
        val t0 = 1_700_000_000_000L
        val navState = resolver.resolve(
            locationState = LocationState(
                point = GeoPoint(29.855, 77.885),
                accuracy = 4.0f,
                speed = 12.0f,
                status = LocationStatus.READY,
                lastFixTime = t0
            ),
            matchResult = ChainageMatchResult(
                chainage = Chainage(196_494L),
                confidence = MatchConfidence.HIGH,
                detectedSide = "RHS",
                source = ChainageSource.KMZ
            ),
            routeData = sampleRoute(),
            projectProfile = ProjectProfile(highwayId = "NH-334", roadSide = RoadSide.RHS),
            locationName = "Haridwar Corridor",
            nowMs = t0
        )

        val publicNotif = service.buildNotification(
            navState = navState,
            settings = NavigationNotificationSettings(showOnLockScreen = true, liveNotificationEnabled = true)
        )
        assertNotEquals(R.drawable.ic_launcher_foreground, publicNotif.smallIcon.resId)
        assertEquals(R.drawable.ic_stat_ctecs_nav, publicNotif.smallIcon.resId)
        assertEquals(Notification.CATEGORY_NAVIGATION, publicNotif.category)
        assertTrue((publicNotif.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        assertTrue((publicNotif.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0)
        assertEquals(NotificationCompat.VISIBILITY_PUBLIC, publicNotif.visibility)

        val privateNotif = service.buildNotification(
            navState = navState,
            settings = NavigationNotificationSettings(showOnLockScreen = false, liveNotificationEnabled = false)
        )
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, privateNotif.visibility)

        val nm = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel(LockScreenNavigationService.NOTIFICATION_CHANNEL_ID)
        assertNotNull(channel)
        assertNotEquals(NotificationManager.IMPORTANCE_MIN, channel!!.importance)
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())

        controller.destroy()
    }

    @Test
    fun `navigation notification settings UI switches, JSON round-trip, and ViewModel DataStore persistence work cleanly`() = runBlocking {
        val customized = NavigationNotificationSettings(
            enabled = true,
            showChainage = true,
            showRoadSide = false,
            showNextKmMarker = true,
            showDistanceRemaining = false,
            showSpeed = true,
            showLocation = false,
            showOnLockScreen = false,
            expandedInfoEnabled = false,
            liveNotificationEnabled = true
        )
        assertEquals(customized, NavigationNotificationSettings.fromJsonString(customized.toJsonString()))

        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = MainViewModel(app)
        idleMain()

        vm.updateNavigationNotificationSettings {
            it.copy(showRoadSide = false, showOnLockScreen = false, liveNotificationEnabled = false)
        }
        repeat(6) {
            idleMain()
            delay(80L)
        }
        assertFalse(vm.navigationNotificationSettings.value.showRoadSide)
        assertFalse(vm.navigationNotificationSettings.value.showOnLockScreen)
        assertFalse(vm.navigationNotificationSettings.value.liveNotificationEnabled)

        vm.resetNavigationNotificationSettings()
        repeat(6) {
            idleMain()
            delay(80L)
        }
        assertEquals(NavigationNotificationSettings.defaultSettings(), vm.navigationNotificationSettings.value)

        var uiNotif by mutableStateOf(NavigationNotificationSettings.defaultSettings())
        composeTestRule.setContent {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                NavigationNotificationSettingsSectionContent(
                    notifSettings = uiNotif,
                    accentColor = Color(0xFF0EA5E9),
                    onToggleNotificationEnabled = { en -> uiNotif = uiNotif.copy(enabled = en) },
                    onUpdateNotifSettings = { t -> uiNotif = t(uiNotif) },
                    onResetDefaults = { uiNotif = NavigationNotificationSettings.defaultSettings() }
                )
            }
        }

        composeTestRule.onNodeWithTag("nav_notif_preview_title").assertTextEquals("CH 196+494 | RHS ↓")
        composeTestRule.onNodeWithTag("nav_notif_preview_chip").assertTextEquals("196+494")

        composeTestRule.onNodeWithTag("nav_notif_switch_road_side").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertFalse(uiNotif.showRoadSide)
        composeTestRule.onNodeWithTag("nav_notif_preview_title").assertTextEquals("CH 196+494")

        composeTestRule.onNodeWithTag("nav_notif_switch_live").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertFalse(uiNotif.liveNotificationEnabled)
        composeTestRule.onNodeWithTag("nav_notif_preview_chip").assertDoesNotExist()
    }
}
