package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.core.app.ApplicationProvider
import com.example.ui.MainViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.AmoledCtecsColorTokens
import com.example.ui.theme.LightCtecsColorTokens
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsRedesignAndDonationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    private fun idleMain() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    // =========================================================================
    // PART A: SETTINGS PAGE REDESIGN, TRUE BLACK DARK MODE & ALWAYS-VISIBLE APP INFO
    // =========================================================================

    @Test
    fun themeTokens_darkModeIsTrueBlackAndLightModeIsCleanWhite() {
        assertEquals(Color(0xFF000000), AmoledCtecsColorTokens.background)
        assertTrue(AmoledCtecsColorTokens.isDark)
        assertEquals(Color(0xFFFFFFFF), LightCtecsColorTokens.background)
        assertFalse(LightCtecsColorTokens.isDark)
    }

    @Test
    fun settingsScreen_appInfoIsAlwaysVisibleWithoutDropdownAndThemeSwitchesInstantly() {
        val vm = MainViewModel(app)
        idleMain()

        composeRule.setContent {
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = mode) {
                SettingsScreen(viewModel = vm, onNavigateBack = {})
            }
        }
        composeRule.waitForIdle()

        // 1. Dark & Light minimalist theme buttons exist at top without duplicate dropdown
        composeRule.onNodeWithTag("settings_appearance_section").assertIsDisplayed()
        composeRule.onNodeWithTag("theme_mode_dark").assertIsDisplayed()
        composeRule.onNodeWithTag("theme_mode_light").assertIsDisplayed()
        composeTestCheckNoDuplicateTitle()

        // Instant theme switch & persistence
        composeRule.onNodeWithTag("theme_mode_light").performClick()
        composeRule.waitUntil(3000L) { vm.themeMode.value == "light" }
        assertEquals("light", vm.themeMode.value)

        composeRule.onNodeWithTag("theme_mode_dark").performClick()
        composeRule.waitUntil(3000L) { vm.themeMode.value == "dark" }
        assertEquals("dark", vm.themeMode.value)

        // 2. Existing settings categories are preserved
        composeRule.onNodeWithText("Project Settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Camera, Photo & Video Settings").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Backup & Restore").performScrollTo().assertIsDisplayed()

        // 3. App Information is ALWAYS visible without any dropdown/expand click
        composeRule.onAllNodesWithText("Permissions and App Information").assertCountEquals(0)
        composeRule.onNodeWithTag("settings_app_info_section").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("app_info_name").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("app_info_version").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("app_info_developer_credit")
            .performScrollTo()
            .assertIsDisplayed()
            .assertTextContains("Made with ❤️ madhureddyainala")

        // 4. Buy Me a Coffee button is completely removed
        composeRule.onAllNodesWithTag("buy_me_a_coffee_button").assertCountEquals(0)
    }

    private fun composeTestCheckNoDuplicateTitle() {
        composeRule.onAllNodesWithText("Appearance & Theme").assertCountEquals(0)
    }
}
