package com.example

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.PreferencesManager
import com.example.settings.SettingSaveEntry
import com.example.settings.SettingSavePhase
import com.example.settings.SettingsAutoSaveCoordinator
import com.example.ui.AppTab
import com.example.ui.MainViewModel
import com.example.ui.loader.CtecsStartupLoader
import com.example.ui.loader.StartupInitializationController
import com.example.ui.loader.StartupLoaderUiState
import com.example.ui.loader.StartupTask
import com.example.ui.loader.resolveStartupLogoRes
import com.example.ui.settings.LocalSettingsAutoSaveCoordinator
import com.example.ui.settings.MinimalistThemeSelectorBar
import com.example.ui.settings.SettingsSaveConfirmationBadge
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoSaveAndStartupLoaderTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val app: Application
        get() = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        PreferencesManager.debugWriteFailureInterceptor = null
    }

    // =========================================================================
    // PART A: AUTOMATIC SETTINGS SAVE & SMART ANIMATED CONFIRMATION TESTS
    // =========================================================================

    @Test
    fun autoSave_onlyShowsSavedAfterRealPersistenceSucceeds_andAutoHides() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val coordinator = SettingsAutoSaveCoordinator(scope = scope, savedVisibleDurationMs = 150L)
            val gate = CompletableDeferred<Unit>()

            coordinator.saveSetting(
                settingKey = "theme_mode",
                sectionKey = "appearance",
                valueSignature = "light".hashCode()
            ) {
                gate.await()
            }

            // Before storage completes, phase MUST be SAVING and NEVER SAVED
            val whileSaving = coordinator.getEntry("theme_mode")
            assertNotNull(whileSaving)
            assertEquals(SettingSavePhase.SAVING, whileSaving!!.phase)

            // Complete real write
            gate.complete(Unit)

            val afterSaved = coordinator.getEntry("theme_mode")
            assertNotNull(afterSaved)
            assertEquals(SettingSavePhase.SAVED, afterSaved!!.phase)

            // Wait for auto-hide (~150ms in test config)
            Thread.sleep(240L)
            val afterHide = coordinator.getEntry("theme_mode")
            assertNotNull(afterHide)
            assertEquals(SettingSavePhase.IDLE, afterHide!!.phase)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun autoSave_rapidEditsDebounceAndPreserveLatestValue_withoutDuplicateWrites() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val coordinator = SettingsAutoSaveCoordinator(scope = scope, savedVisibleDurationMs = 1300L)
            val writeCount = AtomicInteger(0)
            val lastWritten = java.util.concurrent.atomic.AtomicReference("")

            // Simulate 8 rapid slider/text changes inside the debounce window
            for (i in 1..8) {
                val value = "NH-44_PKG_$i"
                coordinator.saveSetting(
                    settingKey = "project_profile",
                    sectionKey = "project",
                    valueSignature = value.hashCode(),
                    debounceMs = 80L
                ) {
                    writeCount.incrementAndGet()
                    lastWritten.set(value)
                }
            }

            Thread.sleep(250L)
            assertEquals("Debounced rapid changes must execute only once", 1, writeCount.get())
            assertEquals("Latest edited value must be preserved", "NH-44_PKG_8", lastWritten.get())
            assertEquals(SettingSavePhase.SAVED, coordinator.getEntry("project_profile")?.phase)

            // Re-submitting the identical value must NOT write again or re-animate
            val revBeforeDuplicate = coordinator.getEntry("project_profile")?.revision
            coordinator.saveSetting(
                settingKey = "project_profile",
                sectionKey = "project",
                valueSignature = "NH-44_PKG_8".hashCode(),
                debounceMs = 0L
            ) {
                writeCount.incrementAndGet()
            }
            Thread.sleep(60L)
            assertEquals("Unchanged duplicate value must be skipped", 1, writeCount.get())
            assertEquals(revBeforeDuplicate, coordinator.getEntry("project_profile")?.revision)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun autoSave_themeChangePersistsAcrossRestart_andShowsAnimatedCheckmarkOnUI() = runBlocking {
        val viewModel = MainViewModel(app)
        viewModel.setThemeMode("dark")
        PreferencesManager.awaitPendingWrites()

        composeRule.setContent {
            val mode by viewModel.themeMode.collectAsStateWithLifecycleForTest()
            MyApplicationTheme(themeMode = mode) {
                CompositionLocalProvider(
                    LocalSettingsAutoSaveCoordinator provides viewModel.settingsAutoSaveCoordinator
                ) {
                    MinimalistThemeSelectorBar(
                        themeMode = mode,
                        isCurrentlyDark = mode != "light",
                        onSelectThemeMode = { viewModel.setThemeMode(it) }
                    )
                }
            }
        }

        // Toggle to Light mode
        composeRule.onNodeWithTag("theme_mode_light").performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithTag("setting_save_saved_theme_mode").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("setting_save_saved_theme_mode").assertIsDisplayed()

        // Simulate process death / restart and verify saved preference survives
        PreferencesManager.awaitPendingWrites()
        PreferencesManager.clearMemoryCacheForProcessRestart()
        val reloaded = PreferencesManager(app).getInitialThemeMode()
        assertEquals("light", reloaded)
    }

    @Test
    fun autoSave_storageFailureShowsFailedAndRetryRecoversWithoutFalseSaved() {
        val viewModel = MainViewModel(app)
        // Start in dark so switching to light is a real change
        runBlocking {
            viewModel.setThemeMode("dark")
            PreferencesManager.awaitPendingWrites()
        }

        // Inject simulated disk/storage failure
        PreferencesManager.debugWriteFailureInterceptor = { IOException("Simulated disk full") }

        composeRule.setContent {
            val mode by viewModel.themeMode.collectAsStateWithLifecycleForTest()
            MyApplicationTheme(themeMode = mode) {
                CompositionLocalProvider(
                    LocalSettingsAutoSaveCoordinator provides viewModel.settingsAutoSaveCoordinator
                ) {
                    MinimalistThemeSelectorBar(
                        themeMode = mode,
                        isCurrentlyDark = mode != "light",
                        onSelectThemeMode = { viewModel.setThemeMode(it) }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("theme_mode_light").performClick()

        // Must enter FAILED state and NEVER display SAVED while failing
        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithTag("setting_save_failed_theme_mode").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(
            "SAVED indicator must NEVER appear after a failed write",
            composeRule.onAllNodesWithTag("setting_save_saved_theme_mode").fetchSemanticsNodes().isEmpty()
        )
        // User's chosen value is retained in memory
        assertEquals("light", viewModel.themeMode.value)

        // Clear simulated failure and tap Retry
        PreferencesManager.debugWriteFailureInterceptor = null
        composeRule.onNodeWithTag("setting_save_failed_theme_mode").performClick()

        composeRule.waitUntil(timeoutMillis = 3_000L) {
            composeRule.onAllNodesWithTag("setting_save_saved_theme_mode").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("setting_save_saved_theme_mode").assertIsDisplayed()

        runBlocking {
            PreferencesManager.awaitPendingWrites()
            PreferencesManager.clearMemoryCacheForProcessRestart()
            assertEquals("light", PreferencesManager(app).getInitialThemeMode())
        }
    }

    @Test
    fun autoSave_confirmationBadgeCausesZeroVerticalLayoutShiftAcrossAllPhases_darkAndLight() {
        var currentEntry by mutableStateOf<SettingSaveEntry?>(null)
        var currentTheme by mutableStateOf("dark")
        val measuredHeights = mutableListOf<Float>()

        composeRule.setContent {
            MyApplicationTheme(themeMode = currentTheme) {
                Column(
                    modifier = Modifier.onGloballyPositioned { coords ->
                        measuredHeights.add(coords.boundsInRoot().height)
                    }
                ) {
                    SettingsSaveConfirmationBadge(
                        entry = currentEntry,
                        onRetry = {}
                    )
                }
            }
        }

        val phases = listOf(
            null,
            SettingSaveEntry("k", "s", SettingSavePhase.SAVING, 1L),
            SettingSaveEntry("k", "s", SettingSavePhase.SAVED, 1L),
            SettingSaveEntry("k", "s", SettingSavePhase.FAILED, 2L, "Error"),
            SettingSaveEntry("k", "s", SettingSavePhase.IDLE, 2L)
        )

        for (theme in listOf("dark", "light")) {
            currentTheme = theme
            measuredHeights.clear()
            for (phaseEntry in phases) {
                currentEntry = phaseEntry
                composeRule.waitForIdle()
            }
            assertTrue("Heights should be measured", measuredHeights.isNotEmpty())
            val firstHeight = measuredHeights.first()
            assertTrue(
                "Badge container height must remain constant across IDLE/SAVING/SAVED/FAILED in $theme mode (got $measuredHeights)",
                measuredHeights.all { kotlin.math.abs(it - firstHeight) <= 1f }
            )
        }
    }

    @Test
    fun settingsScreen_hasNoManualSaveOrApplyButton_andPreservesAppInfoAndBuyMeACoffee() {
        val viewModel = MainViewModel(app)
        composeRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                SettingsScreen(viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()

        // Verify large auto-save banner and Done button are completely removed while Appearance/App Info remain intact
        composeRule.onNodeWithTag("settings_done_button").assertDoesNotExist()
        assertTrue(
            "Large 'Preferences saved automatically' banner must be removed",
            composeRule.onAllNodesWithText("Preferences saved automatically", substring = true, ignoreCase = true)
                .fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithTag("settings_appearance_section").assertIsDisplayed()
    }

    // =========================================================================
    // PART B: SMART ANIMATED APP STARTUP LOADER REDESIGN TESTS
    // =========================================================================

    @Test
    fun startupLoader_oldSmartGlassPageLoaderIsCompletelyRemoved() {
        var foundOldClass = false
        try {
            Class.forName("com.example.ui.loader.SmartGlassPageLoaderKt")
            foundOldClass = true
        } catch (_: ClassNotFoundException) {
            foundOldClass = false
        }
        assertFalse("Old SmartGlassPageLoader popup class must be completely removed", foundOldClass)
    }

    @Test
    fun startupLoader_transparentLogoAssetsHaveGenuineAlphaAndAutoSelectByTheme() {
        assertEquals(R.drawable.ctecs_logo_dark, resolveStartupLogoRes(isDark = true))
        assertEquals(R.drawable.ctecs_logo_light, resolveStartupLogoRes(isDark = false))

        for (resId in listOf(R.drawable.ctecs_logo_dark, R.drawable.ctecs_logo_light)) {
            val bmp = BitmapFactory.decodeResource(app.resources, resId)
            assertNotNull("Transparent CTECS logo drawable must decode cleanly", bmp)
            assertTrue("Logo must have alpha channel", bmp.hasAlpha())
            val topLeftAlpha = Color.alpha(bmp.getPixel(0, 0))
            assertEquals("Corners must be genuinely transparent (alpha == 0)", 0, topLeftAlpha)
        }
    }

    @Test
    fun startupLoader_weightedProgressUpdatesTruthfully_neverFabricates100BeforeCompletion() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = StartupInitializationController(scope = scope, completionTransitionDelayMs = 0L)
            val task1Gate = CompletableDeferred<Unit>()
            val task2Gate = CompletableDeferred<Unit>()

            controller.startIfNeeded(
                listOf(
                    StartupTask("t1", "Loading preferences...", 0.5f, isEssential = true) { task1Gate.await() },
                    StartupTask("t2", "Preparing services...", 0.5f, isEssential = true) { task2Gate.await() }
                )
            )

            // Wait briefly for IO task 1 to start
            Thread.sleep(60L)
            val s0 = controller.uiState.value
            assertTrue(s0.isVisible)
            assertFalse(s0.isCompleted)
            assertTrue("Must not fabricate 100% before tasks complete", s0.displayPercentage < 100)

            // Complete task 1 -> ~50%
            task1Gate.complete(Unit)
            Thread.sleep(80L)
            val s1 = controller.uiState.value
            assertTrue(s1.isVisible)
            assertFalse(s1.isCompleted)
            assertEquals(50, s1.displayPercentage)

            // Complete task 2 -> 100% then hidden (since delay = 0L)
            task2Gate.complete(Unit)
            Thread.sleep(80L)
            val s2 = controller.uiState.value
            assertTrue(s2.isCompleted)
            assertEquals(100, s2.displayPercentage)
            assertFalse("Loader must disappear after startup finishes", s2.isVisible)
            assertTrue(controller.hasFinishedSuccessfully)

            // Calling startIfNeeded again MUST NOT re-show loader (runs ONCE per startup)
            controller.startIfNeeded(
                listOf(StartupTask("t3", "Should not rerun", 1f, isEssential = true) {})
            )
            assertFalse("Startup loader must never reappear after completing once", controller.uiState.value.isVisible)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun startupLoader_essentialFailureShowsRetryAndNever100_nonEssentialFailureDoesNotBlock() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = StartupInitializationController(scope = scope, completionTransitionDelayMs = 0L)
            var shouldFailEssential = true
            var prefsLoadedCount = 0

            controller.startIfNeeded(
                listOf(
                    StartupTask("prefs", "Loading preferences...", 0.3f, isEssential = true) {
                        prefsLoadedCount++
                    },
                    StartupTask("optional_telemetry", "Checking telemetry...", 0.0f, isEssential = false) {
                        throw IllegalStateException("Non-essential error")
                    },
                    StartupTask("db", "Initialising CTECS...", 0.7f, isEssential = true) {
                        if (shouldFailEssential) {
                            throw IOException("Storage temporarily locked")
                        }
                    }
                )
            )

            Thread.sleep(100L)
            val failedState = controller.uiState.value
            assertTrue(failedState.isVisible)
            assertFalse(failedState.isCompleted)
            assertTrue("Failed startup must NEVER display 100%", failedState.displayPercentage < 100)
            assertEquals("Storage temporarily locked", failedState.errorMessage)
            assertEquals(1, prefsLoadedCount)

            // Retry: already completed tasks (prefs) are preserved and not re-run; db now succeeds
            shouldFailEssential = false
            controller.retry()
            Thread.sleep(100L)

            val recoveredState = controller.uiState.value
            assertTrue(recoveredState.isCompleted)
            assertNull(recoveredState.errorMessage)
            assertFalse(recoveredState.isVisible)
            assertEquals("Already loaded preferences must be preserved on retry", 1, prefsLoadedCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun startupLoader_rendersMinimalistTransparentUI_inDarkLightIndeterminateAndErrorStates() {
        var state by mutableStateOf(
            StartupLoaderUiState(
                isVisible = true,
                progress = 0.42f,
                isIndeterminate = false,
                statusMessage = "Loading preferences...",
                isCompleted = false,
                errorMessage = null
            )
        )
        var theme by mutableStateOf("dark")
        var retryClicks = 0

        composeRule.setContent {
            MyApplicationTheme(themeMode = theme) {
                CtecsStartupLoader(
                    state = state,
                    onRetry = { retryClicks++ }
                )
            }
        }

        // Dark mode determinate state
        composeRule.onNodeWithTag("ctecs_startup_loader").assertIsDisplayed()
        composeRule.onNodeWithTag("ctecs_startup_logo").assertIsDisplayed()
        composeRule.onNodeWithTag("ctecs_startup_percentage").assertTextContains("Loading... 42%")
        composeRule.onNodeWithTag("ctecs_startup_progress").assertIsDisplayed()
        composeRule.onNodeWithTag("ctecs_startup_status").assertTextContains("Loading preferences...")

        // Light mode indeterminate state (never fabricates percentage)
        theme = "light"
        state = state.copy(isIndeterminate = true, statusMessage = "Initialising CTECS...")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ctecs_startup_percentage").assertTextContains("Loading...")

        // Error + Retry state
        state = state.copy(isIndeterminate = false, errorMessage = "Unable to open database")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ctecs_startup_error").assertTextContains("Unable to open database")
        composeRule.onNodeWithTag("ctecs_startup_retry_btn").performClick()
        assertEquals(1, retryClicks)
    }

    @Test
    fun startupLoader_runsOnceInMainViewModel_andNeverReappearsDuringTabNavigation() {
        val viewModel = MainViewModel(app)

        // Wait for startup tasks + 220ms fade delay to finish
        val deadline = System.currentTimeMillis() + 3_000L
        while (viewModel.startupLoaderState.value.isVisible && System.currentTimeMillis() < deadline) {
            Thread.sleep(25L)
        }

        assertTrue(viewModel.startupInitializationController.hasFinishedSuccessfully)
        assertFalse("Startup loader must hide once initialization finishes", viewModel.startupLoaderState.value.isVisible)
        assertFalse("Compatibility isPageLoading must be false after startup", viewModel.isPageLoading.value)

        // Navigate across Camera, Photos, Routes, Weather, Settings, and back to Home
        for (tab in listOf(AppTab.CAMERA, AppTab.PHOTOS, AppTab.ROUTES, AppTab.WEATHER, AppTab.SETTINGS, AppTab.HOME)) {
            viewModel.setTab(tab)
            assertFalse(
                "Startup loader must NEVER reappear when navigating to $tab",
                viewModel.startupLoaderState.value.isVisible
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateWithLifecycleForTest(): androidx.compose.runtime.State<T> {
    return this.collectAsState()
}
