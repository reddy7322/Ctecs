package com.example

import android.app.Activity
import android.app.Application
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.donation.AmountValidationResult
import com.example.donation.DonationActionResult
import com.example.donation.DonationConfig
import com.example.donation.DonationRepository
import com.example.donation.DonationStatus
import com.example.donation.DonationTransactionEntity
import com.example.donation.DonationWebhookVerifier
import com.example.donation.UpiQrGenerator
import com.example.ui.MainViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.theme.AmoledCtecsColorTokens
import com.example.ui.theme.LightCtecsColorTokens
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    private lateinit var inMemoryDb: AppDatabase
    private lateinit var donationRepo: DonationRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        inMemoryDb = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        donationRepo = DonationRepository(app, inMemoryDb.donationDao())
    }

    @After
    fun tearDown() {
        inMemoryDb.close()
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
        composeRule.onAllNodesWithText("Appearance & Theme").assertCountEquals(0)

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

        // 4. Exactly ONE Buy Me a Coffee button directly inside App Information
        composeRule.onAllNodesWithTag("buy_me_a_coffee_button").assertCountEquals(1)
        composeRule.onNodeWithTag("buy_me_a_coffee_button").performScrollTo().assertIsDisplayed()
    }

    // =========================================================================
    // PART B: IMMUTABLE RECIPIENT CONFIG, VALIDATION & DYNAMIC QR
    // =========================================================================

    @Test
    fun donationConfig_enforcesExactRecipientDetailsAndStrictAmountValidation() {
        assertEquals("tvtelugu@ptyes", DonationConfig.UPI_ID)
        assertEquals("madhu", DonationConfig.RECIPIENT_NAME)
        assertEquals("INR", DonationConfig.CURRENCY)
        assertEquals("CTECS Coffee Support", DonationConfig.DESCRIPTION)

        val upiUri = DonationConfig.buildUpiPaymentUri(50.0, "CTECS998877")
        val parsedUri = android.net.Uri.parse(upiUri)
        assertTrue(upiUri.startsWith("upi://pay?"))
        assertEquals("tvtelugu@ptyes", parsedUri.getQueryParameter("pa"))
        assertEquals("madhu", parsedUri.getQueryParameter("pn"))
        assertFalse("Recipient name must NEVER be TVTelugu", parsedUri.getQueryParameter("pn").equals("TVTelugu", ignoreCase = true))
        assertEquals("50.00", parsedUri.getQueryParameter("am"))
        assertEquals("INR", parsedUri.getQueryParameter("cu"))
        assertEquals("CTECS998877", parsedUri.getQueryParameter("tr"))

        // Strict validation: rejects empty, 0, negative, non-numeric, out-of-range
        assertTrue(DonationConfig.validateAmount("") is AmountValidationResult.Invalid)
        assertTrue(DonationConfig.validateAmount("0") is AmountValidationResult.Invalid)
        assertTrue(DonationConfig.validateAmount("-25") is AmountValidationResult.Invalid)
        assertTrue(DonationConfig.validateAmount("abc") is AmountValidationResult.Invalid)
        assertTrue(DonationConfig.validateAmount("100000") is AmountValidationResult.Invalid)

        val valid = DonationConfig.validateAmount("75.50")
        assertTrue(valid is AmountValidationResult.Valid)
        assertEquals(75.50, (valid as AmountValidationResult.Valid).amountInr, 0.0001)
    }

    @Test
    fun upiQrGenerator_producesDistinctScannableMatricesForDifferentAmounts() {
        val uri10 = DonationConfig.buildUpiPaymentUri(10.0, "CTECS_ORDER_A")
        val uri100 = DonationConfig.buildUpiPaymentUri(100.0, "CTECS_ORDER_B")
        val qr10 = UpiQrGenerator.encode(uri10)
        val qr100 = UpiQrGenerator.encode(uri100)

        assertTrue("QR version matrix must be at least 21x21", qr10.size >= 21)
        assertFalse("Dynamic QRs for different orders must differ", qr10.modules.contentEquals(qr100.modules))

        val bmp = UpiQrGenerator.generateBitmap(uri10, targetSizePx = 256)
        assertTrue(bmp.width >= 180 && bmp.height >= 180 && bmp.width == bmp.height)
    }

    // =========================================================================
    // PART C: IDEMPOTENCY, STRICT NON-AUTO-PAID VERIFICATION, USER SCOPING & ADMIN
    // =========================================================================

    @Test
    fun donationLifecycle_preventsDuplicatesAndNeverAutoMarksPaidOnIntentOrUtr() = runBlocking {
        val idemKey = DonationConfig.generateIdempotencyKey()
        val first = donationRepo.createOrReuseDonationOrder(20.0, idemKey)
        val duplicate = donationRepo.createOrReuseDonationOrder(20.0, idemKey)

        assertTrue(first is DonationActionResult.Success)
        assertTrue(duplicate is DonationActionResult.Success)
        val order = (first as DonationActionResult.Success).data
        assertEquals(order.orderRef, (duplicate as DonationActionResult.Success).data.orderRef)
        assertEquals(1, donationRepo.getOwnDonations().size)
        assertEquals(DonationStatus.CREATED, order.parsedStatus)

        // Returning from UPI app with RESULT_OK + Status=SUCCESS MUST NEVER mark PAID!
        val afterUpiSuccess = donationRepo.handleUpiAppReturn(
            orderRef = order.orderRef,
            resultCode = Activity.RESULT_OK,
            responseString = "txnId=UPI123&Status=SUCCESS&ApprovalRefNo=4444"
        )
        assertTrue(afterUpiSuccess is DonationActionResult.Success)
        assertEquals(DonationStatus.PENDING, (afterUpiSuccess as DonationActionResult.Success).data.parsedStatus)

        // Submitting UTR marks UNDER_REVIEW only — NEVER PAID!
        val afterUtr = donationRepo.submitUtrForReview(order.orderRef, "432198765432")
        assertTrue(afterUtr is DonationActionResult.Success)
        val reviewed = (afterUtr as DonationActionResult.Success).data
        assertEquals(DonationStatus.UNDER_REVIEW, reviewed.parsedStatus)
        assertEquals("432198765432", reviewed.utr)

        // Cancellation test on a separate order
        val secondOrder = (donationRepo.createOrReuseDonationOrder(50.0, DonationConfig.generateIdempotencyKey()) as DonationActionResult.Success).data
        val cancelled = donationRepo.handleUpiAppReturn(secondOrder.orderRef, Activity.RESULT_CANCELED, null)
        assertEquals(DonationStatus.CANCELLED, (cancelled as DonationActionResult.Success).data.parsedStatus)
    }

    @Test
    fun userHistory_isStrictlyScopedToCurrentDonorOnly() = runBlocking {
        val ownOrder = (donationRepo.createOrReuseDonationOrder(10.0, DonationConfig.generateIdempotencyKey()) as DonationActionResult.Success).data

        // Insert another user's transaction directly into DB
        inMemoryDb.donationDao().insertTransaction(
            DonationTransactionEntity(
                orderRef = "CTECS_OTHER_USER_1",
                idempotencyKey = "other_idem_key_123456",
                ownerUserId = "donor_someone_else",
                amountInr = 100.0,
                upiUri = DonationConfig.buildUpiPaymentUri(100.0, "CTECS_OTHER_USER_1"),
                status = DonationStatus.PAID.name,
                createdAtMs = 1_700_000_000_000L,
                updatedAtMs = 1_700_000_000_000L,
                expiresAtMs = 1_700_000_900_000L
            )
        )

        val ownList = donationRepo.getOwnDonations()
        assertEquals(1, ownList.size)
        assertEquals(ownOrder.orderRef, ownList.first().orderRef)
    }

    @Test
    fun adminVerification_requiresAuthenticationAndEvidenceAndCreatesAuditLog() = runBlocking {
        val order = (donationRepo.createOrReuseDonationOrder(100.0, DonationConfig.generateIdempotencyKey()) as DonationActionResult.Success).data
        donationRepo.submitUtrForReview(order.orderRef, "511122223333")

        // Unauthenticated verification must fail
        val unauth = donationRepo.adminVerifyTransaction(
            orderRef = order.orderRef,
            approveAsPaid = true,
            independentEvidence = "Bank credit matched",
            verificationReason = "Statement check"
        )
        assertTrue(unauth is DonationActionResult.Error)

        // Claim local admin access & verify wrong passphrase fails after logout
        val claim = donationRepo.authenticateAdmin("madhu_admin", "StrongPass#2026", isInitialClaim = true)
        assertTrue(claim is DonationActionResult.Success)
        donationRepo.logoutAdmin()

        val badLogin = donationRepo.authenticateAdmin("madhu_admin", "WrongPass#9999", isInitialClaim = false)
        assertTrue(badLogin is DonationActionResult.Error)

        val goodLogin = donationRepo.authenticateAdmin("madhu_admin", "StrongPass#2026", isInitialClaim = false)
        assertTrue(goodLogin is DonationActionResult.Success)

        // Missing evidence must be rejected
        val noEvidence = donationRepo.adminVerifyTransaction(
            orderRef = order.orderRef,
            approveAsPaid = true,
            independentEvidence = "ok",
            verificationReason = "Verified"
        )
        assertTrue(noEvidence is DonationActionResult.Error)

        // Authenticated verification with evidence + reason succeeds & writes audit log
        val verified = donationRepo.adminVerifyTransaction(
            orderRef = order.orderRef,
            approveAsPaid = true,
            independentEvidence = "HDFC statement credit INR 100.00 UTR 511122223333",
            verificationReason = "Confirmed received in tvtelugu@ptyes linked bank account"
        )
        assertTrue(verified is DonationActionResult.Success)
        assertEquals(DonationStatus.PAID, (verified as DonationActionResult.Success).data.parsedStatus)

        val logs = donationRepo.getAuditLogsForOrder(order.orderRef)
        assertTrue(logs.any { it.actorRole == "ADMIN" && it.newStatus == "PAID" && it.actorId == "madhu_admin" })

        // CSV formula-injection guard
        val sanitized = DonationRepository.sanitizeCsvCell("=CMD|' /C calc'!A0")
        assertTrue(sanitized.startsWith("\"'="))
    }

    // =========================================================================
    // PART D: SIGNED WEBHOOK VERIFICATION (RAW BODY HMAC, REPLAY, DEDUP, AMOUNT)
    // =========================================================================

    @Test
    fun webhookVerifier_validatesHmacSignatureReplayWindowDedupAndAmount() {
        val secret = "whsec_ctecs_unit_test_secret"
        val nowSec = 1_750_000_000L
        val rawBody = """{"event_id":"evt_paid_1","order_ref":"CTECS555","amount":50.00,"currency":"INR","event":"order.paid"}"""
        val validHeader = DonationWebhookVerifier.signPayload(rawBody, secret, nowSec)

        // 1. Valid signature + amount + currency -> Accepted
        val ok = DonationWebhookVerifier.verifyAndParseWebhook(
            rawBody = rawBody,
            signatureHeader = validHeader,
            webhookSecret = secret,
            expectedOrderRef = "CTECS555",
            expectedAmountInr = 50.0,
            currentStatus = DonationStatus.PENDING,
            processedEventIds = emptySet(),
            nowSeconds = nowSec + 20
        )
        assertTrue(ok is DonationWebhookVerifier.VerificationResult.Accepted)
        assertEquals(DonationStatus.PAID, (ok as DonationWebhookVerifier.VerificationResult.Accepted).targetStatus)

        // 2. Tampered body -> Rejected
        val tampered = DonationWebhookVerifier.verifyAndParseWebhook(
            rawBody = """{"event_id":"evt_paid_1","order_ref":"CTECS555","amount":500.00,"currency":"INR","event":"order.paid"}""",
            signatureHeader = validHeader,
            webhookSecret = secret,
            expectedOrderRef = "CTECS555",
            expectedAmountInr = 500.0,
            currentStatus = DonationStatus.PENDING,
            processedEventIds = emptySet(),
            nowSeconds = nowSec + 20
        )
        assertTrue(tampered is DonationWebhookVerifier.VerificationResult.Rejected)

        // 3. Replayed timestamp (> 5 min old) -> Rejected
        val replayed = DonationWebhookVerifier.verifyAndParseWebhook(
            rawBody = rawBody,
            signatureHeader = validHeader,
            webhookSecret = secret,
            expectedOrderRef = "CTECS555",
            expectedAmountInr = 50.0,
            currentStatus = DonationStatus.PENDING,
            processedEventIds = emptySet(),
            nowSeconds = nowSec + 360
        )
        assertTrue(replayed is DonationWebhookVerifier.VerificationResult.Rejected)

        // 4. Duplicate event_id -> Rejected as duplicate
        val dup = DonationWebhookVerifier.verifyAndParseWebhook(
            rawBody = rawBody,
            signatureHeader = validHeader,
            webhookSecret = secret,
            expectedOrderRef = "CTECS555",
            expectedAmountInr = 50.0,
            currentStatus = DonationStatus.PAID,
            processedEventIds = setOf("evt_paid_1"),
            nowSeconds = nowSec + 20
        )
        assertTrue(dup is DonationWebhookVerifier.VerificationResult.Rejected)

        // 5. Amount mismatch -> Rejected
        val wrongAmount = DonationWebhookVerifier.verifyAndParseWebhook(
            rawBody = rawBody,
            signatureHeader = validHeader,
            webhookSecret = secret,
            expectedOrderRef = "CTECS555",
            expectedAmountInr = 100.0,
            currentStatus = DonationStatus.PENDING,
            processedEventIds = emptySet(),
            nowSeconds = nowSec + 20
        )
        assertTrue(wrongAmount is DonationWebhookVerifier.VerificationResult.Rejected)
    }

    // =========================================================================
    // PART E: ULTRA-COMPACT ICON-FIRST BUY ME A COFFEE POPUP REDESIGN TESTS
    // =========================================================================

    @Test
    fun buyMeACoffeePopup_ultraCompactIconOnlyFlow_preservesBackendSecurityAndState() {
        val vm = MainViewModel(app)
        idleMain()

        composeRule.setContent {
            val mode by vm.themeMode.collectAsStateWithLifecycle()
            MyApplicationTheme(themeMode = mode) {
                SettingsScreen(viewModel = vm, onNavigateBack = {})
            }
        }
        composeRule.waitForIdle()

        // 1. Tap Buy Me a Coffee inside always-visible App Info -> opens compact popup without leaving Settings
        composeRule.onNodeWithTag("buy_me_a_coffee_button").performScrollTo().performClick()
        idleMain()
        composeRule.waitUntil(3000L) {
            idleMain()
            composeRule.onAllNodesWithTag("buy_me_a_coffee_sheet").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("buy_me_a_coffee_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("coffee_cup_animation").assertIsDisplayed()
        composeRule.onNodeWithTag("coffee_sheet_close_btn").assertIsDisplayed()

        // 2. Zero unnecessary descriptive text, subtitles, coffee descriptions, or bulky headings
        composeRule.onAllNodesWithText("Support the continued development", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Small Coffee", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Big Support", substring = true, ignoreCase = true).assertCountEquals(0)
        composeRule.onAllNodesWithText("Continue to Payment", substring = true, ignoreCase = true).assertCountEquals(0)

        // 3. Compact amount grid (☕ ₹10, ☕ ₹20, ☕ ₹50, ☕ ₹100 + Custom edit pill) & selection indicator
        composeRule.onNodeWithTag("coffee_preset_10").assertIsDisplayed()
        composeRule.onNodeWithTag("coffee_preset_20").assertIsDisplayed()
        composeRule.onNodeWithTag("coffee_preset_50").assertIsDisplayed()
        composeRule.onNodeWithTag("coffee_preset_100").assertIsDisplayed()
        composeRule.onNodeWithTag("coffee_preset_custom").assertIsDisplayed()
        composeRule.onNodeWithTag("coffee_amount_selected_indicator", useUnmergedTree = true).assertIsDisplayed()

        // 4. All 6 icon-based payment buttons are visible with zero scrolling
        composeRule.onNodeWithTag("coffee_pay_gpay_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("coffee_pay_phonepe_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("coffee_pay_paytm_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("coffee_pay_upi_app_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("coffee_pay_qr_btn").assertIsDisplayed()
        composeRule.onNodeWithTag("coffee_copy_upi_btn").assertIsDisplayed()

        // 5. Invalid custom amount rejected cleanly before creating any order
        composeRule.onNodeWithTag("coffee_preset_custom").performClick()
        composeRule.onNodeWithTag("coffee_custom_amount_input").performTextInput("0")
        composeRule.onNodeWithTag("coffee_pay_qr_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("coffee_amount_error", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(runBlocking { vm.donationRepository.getOwnDonations() }.isEmpty())

        // 6. Select ₹50 -> tap QR icon -> creates secure order for tvtelugu@ptyes (madhu, INR) & shows dynamic QR
        composeRule.onNodeWithTag("coffee_preset_50").performClick()
        composeRule.onNodeWithTag("coffee_pay_qr_btn").performClick()
        composeRule.waitUntil(4000L) {
            composeRule.onAllNodesWithTag("coffee_dynamic_qr_image").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("coffee_dynamic_qr_image").assertIsDisplayed()

        // 7. Must NEVER show verified checkmark for an unverified/PENDING order
        composeRule.onAllNodesWithTag("coffee_verified_checkmark").assertCountEquals(0)
        val createdOrder = runBlocking { vm.donationRepository.getOwnDonations().firstOrNull() }
        assertTrue(createdOrder != null)
        assertEquals(DonationConfig.UPI_ID, createdOrder!!.upiId)
        assertEquals(DonationConfig.RECIPIENT_NAME, createdOrder.recipientName)
        assertEquals(DonationConfig.CURRENCY, createdOrder.currency)
        assertEquals(50.0, createdOrder.amountInr, 0.001)
        assertNotEquals(DonationStatus.PAID, createdOrder.parsedStatus)

        // 8. Copy UPI icon copies immutable UPI ID
        composeRule.onNodeWithTag("coffee_copy_upi_btn").performClick()
        composeRule.waitForIdle()

        // 9. Closing popup via tiny × icon dismisses cleanly WITHOUT deleting or cancelling transaction
        composeRule.onNodeWithTag("coffee_sheet_close_btn").performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag("buy_me_a_coffee_sheet").assertCountEquals(0)

        val stillSaved = runBlocking { vm.donationRepository.getOwnDonations() }
        assertTrue("Closing popup must NEVER delete donation record", stillSaved.any { it.orderRef == createdOrder.orderRef })
        assertNotEquals(
            "Closing popup must NOT cancel active transaction",
            DonationStatus.CANCELLED,
            stillSaved.first { it.orderRef == createdOrder.orderRef }.parsedStatus
        )

        // 10. Reopening in Light Mode preserves selected amount & active transaction indicator seamlessly
        vm.setThemeMode("light")
        composeRule.waitUntil(3000L) { vm.themeMode.value == "light" }
        composeRule.onNodeWithTag("buy_me_a_coffee_button").performScrollTo().performClick()
        idleMain()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("buy_me_a_coffee_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("donation_status_pending").assertIsDisplayed()
    }

    @Test
    fun buyMeACoffeePopup_showsTinyVerifiedCheckmarkOnlyAfterTrustedPaidConfirmation() {
        // Create an order and simulate raw UPI app return Status=SUCCESS (must remain PENDING!)
        val order = runBlocking {
            val created = (donationRepo.createOrReuseDonationOrder(100.0, DonationConfig.generateIdempotencyKey()) as DonationActionResult.Success).data
            donationRepo.handleUpiAppReturn(created.orderRef, Activity.RESULT_OK, "Status=SUCCESS")
            donationRepo.authenticateAdmin("madhu_admin", "StrongPass#2026", isInitialClaim = true)
            created
        }

        composeRule.setContent {
            MyApplicationTheme(themeMode = "dark") {
                com.example.ui.settings.BuyMeACoffeeBottomSheet(
                    repository = donationRepo,
                    restoredOrderRef = order.orderRef,
                    onDismiss = {},
                    onToast = {}
                )
            }
        }
        composeRule.waitForIdle()

        // While PENDING (even after UPI app returned Status=SUCCESS), verified checkmark MUST NOT appear
        composeRule.onNodeWithTag("donation_status_pending").assertIsDisplayed()
        composeRule.onAllNodesWithTag("coffee_verified_checkmark").assertCountEquals(0)

        // Now perform trusted verification -> PAID
        val verified = runBlocking {
            donationRepo.adminVerifyTransaction(
                orderRef = order.orderRef,
                approveAsPaid = true,
                independentEvidence = "Bank credit INR 100.00 verified",
                verificationReason = "Statement match"
            )
        }
        assertTrue(verified is DonationActionResult.Success)

        // Popup automatically displays the tiny animated green checkmark after trusted PAID verification
        composeRule.waitUntil(4000L) {
            idleMain()
            composeRule.onAllNodesWithTag("coffee_verified_checkmark").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("coffee_verified_checkmark").assertIsDisplayed()
        composeRule.onNodeWithTag("donation_status_paid").assertIsDisplayed()
    }
}
