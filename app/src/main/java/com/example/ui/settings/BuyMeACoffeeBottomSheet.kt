package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.donation.AmountValidationResult
import com.example.donation.DonationActionResult
import com.example.donation.DonationConfig
import com.example.donation.DonationRepository
import com.example.donation.DonationStatus
import com.example.donation.DonationTransactionEntity
import com.example.donation.UpiIntentResolution
import com.example.donation.UpiQrGenerator
import com.example.ui.theme.CtecsTheme
import com.example.ui.theme.SmartFadeDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class PaymentActionSlot {
    GOOGLE_PAY,
    PHONE_PE,
    PAYTM,
    BHIM,
    OTHER_UPI,
    QR,
    COPY_LINK
}

private val COMPACT_PRESET_AMOUNTS = listOf(10.0, 20.0, 50.0, 100.0)

/**
 * Compact animated status badge retained for shared usage (e.g. DonationAdminPanel).
 */
@Composable
internal fun DonationStatusBadge(
    status: DonationStatus,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val context = LocalContext.current
    val reducedMotion = remember(context) { isSystemReducedMotionEnabled(context) }

    val badgeColor = when (status) {
        DonationStatus.PAID -> c.success
        DonationStatus.UNDER_REVIEW -> c.coffeeAccent
        DonationStatus.PENDING, DonationStatus.CREATED -> c.warning
        DonationStatus.FAILED -> c.error
        DonationStatus.CANCELLED, DonationStatus.EXPIRED -> c.textMuted
    }

    val isPulsing = !reducedMotion && (
        status == DonationStatus.PENDING ||
            status == DonationStatus.CREATED ||
            status == DonationStatus.UNDER_REVIEW
        )
    val pulseAlpha = if (isPulsing) {
        val infinite = rememberInfiniteTransition(label = "donation_status_pulse_${status.name}")
        val animated by infinite.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "status_dot_alpha"
        )
        animated
    } else {
        1f
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(badgeColor.copy(alpha = if (c.isDark) 0.17f else 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.5.dp)
            .testTag("donation_status_${status.name.lowercase(Locale.US)}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        when (status) {
            DonationStatus.PAID -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(11.dp)
                )
            }
            DonationStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = badgeColor,
                    modifier = Modifier.size(11.dp)
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(badgeColor.copy(alpha = pulseAlpha))
                )
            }
        }
        Text(
            text = status.displayLabel.uppercase(Locale.US),
            color = badgeColor,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.45.sp,
            maxLines = 1
        )
    }
}

/**
 * Ultra-compact, icon-first Buy Me a Coffee popup.
 *
 * - Zero descriptive paragraphs, headings, subtitles, or text-heavy sections.
 * - Minimal AMOLED dark glass / adaptive light popup with small coffee icon & tiny close (×).
 * - Equal-sized compact amount buttons (☕ ₹10, ☕ ₹20, ☕ ₹50, ☕ ₹100) + compact Custom edit pill.
 * - Authentic icon-only payment grid (Google Pay, PhonePe, Paytm, Other UPI, QR, Copy UPI).
 * - Preserves exact UPI ID ("tvtelugu@ptyes"), recipient ("madhu"), INR currency, dynamic QR,
 *   native UPI intent flow, transaction state across returns, and trusted-backend-only PAID checkmark.
 */
@Composable
fun BuyMeACoffeeBottomSheet(
    repository: DonationRepository,
    onDismiss: () -> Unit,
    onToast: (String) -> Unit,
    restoredOrderRef: String? = null,
    onActiveOrderRefChange: (String?) -> Unit = {}
) {
    val c = CtecsTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reducedMotion = remember(context) { isSystemReducedMotionEnabled(context) }
    val installedUpiIcons = rememberInstalledUpiAppIcons()

    // Smooth lightweight entrance animation
    var popupEntered by remember { mutableStateOf(reducedMotion) }
    LaunchedEffect(Unit) { popupEntered = true }

    val popupAlpha by animateFloatAsState(
        targetValue = if (popupEntered) 1f else 0f,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 170, easing = FastOutSlowInEasing),
        label = "coffee_popup_alpha"
    )
    val popupScale by animateFloatAsState(
        targetValue = if (popupEntered) 1f else 0.95f,
        animationSpec = if (reducedMotion) {
            tween(0)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
        },
        label = "coffee_popup_scale"
    )

    // Saveable state so selected amount & transaction survive UPI app switches and theme changes
    var selectedPresetAmount by rememberSaveable { mutableStateOf(20.0) }
    var isCustomSelected by rememberSaveable { mutableStateOf(false) }
    var customAmountInput by rememberSaveable { mutableStateOf("") }
    var activeOrderRef by rememberSaveable { mutableStateOf(restoredOrderRef) }
    var currentAttemptIdempotencyKey by rememberSaveable { mutableStateOf(DonationConfig.generateIdempotencyKey()) }
    var showQrPanel by rememberSaveable { mutableStateOf(false) }

    var loadingSlot by remember { mutableStateOf<PaymentActionSlot?>(null) }
    var isRefreshingStatus by remember { mutableStateOf(false) }
    var showInvalidAmountHighlight by remember { mutableStateOf(false) }
    var justCopiedPaymentLink by remember { mutableStateOf(false) }
    var lastFailedBrand by remember { mutableStateOf<UpiAppBrand?>(null) }
    var hasLaunchError by remember { mutableStateOf(false) }
    var latestCreatedOrder by remember { mutableStateOf<DonationTransactionEntity?>(null) }
    var hydratedAmountFromOrderRef by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(activeOrderRef) {
        onActiveOrderRefChange(activeOrderRef)
    }

    LaunchedEffect(justCopiedPaymentLink) {
        if (justCopiedPaymentLink) {
            delay(1400L)
            justCopiedPaymentLink = false
        }
    }

    val ownDonationsFlow = remember(repository) { repository.observeOwnDonations() }
    val ownDonations by ownDonationsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val activeOrder: DonationTransactionEntity? = remember(ownDonations, activeOrderRef, latestCreatedOrder) {
        activeOrderRef?.let { ref ->
            ownDonations.firstOrNull { it.orderRef == ref }
                ?: latestCreatedOrder?.takeIf { it.orderRef == ref }
        }
    }

    // Restore selected amount automatically when reopening an existing saved transaction
    LaunchedEffect(activeOrder?.orderRef) {
        val restored = activeOrder ?: return@LaunchedEffect
        if (hydratedAmountFromOrderRef != restored.orderRef) {
            hydratedAmountFromOrderRef = restored.orderRef
            if (COMPACT_PRESET_AMOUNTS.any { kotlin.math.abs(it - restored.amountInr) < 0.005 }) {
                isCustomSelected = false
                selectedPresetAmount = COMPACT_PRESET_AMOUNTS.first { kotlin.math.abs(it - restored.amountInr) < 0.005 }
            } else {
                isCustomSelected = true
                customAmountInput = if (restored.amountInr % 1.0 == 0.0) {
                    restored.amountInr.toLong().toString()
                } else {
                    DonationConfig.formatUpiAmountParam(restored.amountInr)
                }
            }
        }
    }

    val effectiveAmountInr: Double? = remember(isCustomSelected, customAmountInput, selectedPresetAmount) {
        if (isCustomSelected) {
            when (val res = DonationConfig.validateAmount(customAmountInput)) {
                is AmountValidationResult.Valid -> res.amountInr
                is AmountValidationResult.Invalid -> null
            }
        } else {
            selectedPresetAmount
        }
    }

    val isVerifiedPaid = activeOrder?.parsedStatus == DonationStatus.PAID

    val cupFillFraction = remember(activeOrder, effectiveAmountInr, isVerifiedPaid) {
        when {
            isVerifiedPaid -> 0.96f
            effectiveAmountInr != null -> DonationConfig.coffeeFillFractionFor(effectiveAmountInr)
            activeOrder != null -> DonationConfig.coffeeFillFractionFor(activeOrder.amountInr)
            else -> 0.25f
        }
    }

    // Generate dynamic UPI QR off the main thread using the exact validated UPI payment URI
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(showQrPanel, activeOrder?.orderRef, activeOrder?.upiUri, activeOrder?.amountInr) {
        val currentOrder = activeOrder
        if (!showQrPanel || currentOrder == null) {
            qrBitmap = null
            return@LaunchedEffect
        }
        val validatedQrUri = repository.resolveValidatedPaymentUri(currentOrder).toString()
        qrBitmap = withContext(Dispatchers.IO) {
            runCatching { UpiQrGenerator.generateBitmap(validatedQrUri, targetSizePx = 440) }.getOrNull()
        }
    }

    // Native Android UPI intent launcher — preserves popup & state, NEVER marks PAID on return!
    val upiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val ref = activeOrderRef ?: return@rememberLauncherForActivityResult
        val rawResponse = result.data?.getStringExtra("response")
            ?: result.data?.dataString
        scope.launch {
            val handled = repository.handleUpiAppReturn(ref, result.resultCode, rawResponse)
            if (handled is DonationActionResult.Success) {
                latestCreatedOrder = handled.data
            }
            // Quietly poll trusted backend if configured (only trusted backend can confirm PAID)
            if (repository.isBackendConfigured) {
                val refreshed = repository.refreshStatusFromTrustedBackend(ref)
                if (refreshed is DonationActionResult.Success) {
                    latestCreatedOrder = refreshed.data
                }
            }
        }
    }

    fun selectAmountAndResetAttemptIfChanged(newAmount: Double?, custom: Boolean) {
        showInvalidAmountHighlight = false
        val previousAmount = effectiveAmountInr
        isCustomSelected = custom
        if (!custom && newAmount != null) {
            selectedPresetAmount = newAmount
        }
        if (newAmount != null && previousAmount != null && kotlin.math.abs(newAmount - previousAmount) >= 0.005) {
            if (activeOrder == null || !isVerifiedPaid) {
                currentAttemptIdempotencyKey = DonationConfig.generateIdempotencyKey()
                if (showQrPanel) {
                    showQrPanel = false
                }
            }
        }
    }

    fun resolveValidatedAmount(): Double? {
        return if (isCustomSelected) {
            when (val res = DonationConfig.validateAmount(customAmountInput)) {
                is AmountValidationResult.Valid -> {
                    showInvalidAmountHighlight = false
                    res.amountInr
                }
                is AmountValidationResult.Invalid -> {
                    showInvalidAmountHighlight = true
                    null
                }
            }
        } else {
            showInvalidAmountHighlight = false
            selectedPresetAmount
        }
    }

    /**
     * Ensures an idempotent order exists for the currently selected amount.
     * Reuses the active non-terminal order if its amount matches, otherwise creates a new one.
     */
    suspend fun ensureOrderForAmount(amount: Double): DonationTransactionEntity? {
        val current = activeOrder
        if (current != null &&
            !current.parsedStatus.isTerminal &&
            kotlin.math.abs(current.amountInr - amount) < 0.005
        ) {
            return current
        }
        if (current != null && (current.parsedStatus.isTerminal || kotlin.math.abs(current.amountInr - amount) >= 0.005)) {
            currentAttemptIdempotencyKey = DonationConfig.generateIdempotencyKey()
        }
        return when (
            val created = repository.createOrReuseDonationOrder(
                amountInr = amount,
                idempotencyKey = currentAttemptIdempotencyKey
            )
        ) {
            is DonationActionResult.Success -> {
                latestCreatedOrder = created.data
                activeOrderRef = created.data.orderRef
                hydratedAmountFromOrderRef = created.data.orderRef
                created.data
            }
            is DonationActionResult.Error -> {
                showInvalidAmountHighlight = true
                null
            }
        }
    }

    fun initiateUpiAppPayment(slot: PaymentActionSlot, targetBrand: UpiAppBrand?) {
        val amount = resolveValidatedAmount() ?: return
        if (loadingSlot != null) return
        loadingSlot = slot
        hasLaunchError = false
        lastFailedBrand = null

        scope.launch {
            try {
                val order = ensureOrderForAmount(amount) ?: return@launch
                val resolution = withContext(Dispatchers.IO) {
                    repository.buildCompatibleUpiIntent(
                        order = order,
                        targetPackage = targetBrand?.packageName,
                        targetDisplayName = targetBrand?.displayName
                    )
                }

                when (resolution) {
                    is UpiIntentResolution.LaunchReady -> {
                        val initRes = repository.markPaymentInitiated(
                            orderRef = order.orderRef,
                            paymentMethod = resolution.paymentMethodLabel
                        )
                        if (initRes is DonationActionResult.Success) {
                            latestCreatedOrder = initRes.data
                        }
                        runCatching {
                            upiLauncher.launch(resolution.intent)
                        }.onFailure { err ->
                            hasLaunchError = true
                            lastFailedBrand = targetBrand
                            val failRes = repository.recordPaymentLaunchFailure(
                                orderRef = order.orderRef,
                                paymentMethod = resolution.paymentMethodLabel,
                                technicalReason = err.javaClass.simpleName
                            )
                            if (failRes is DonationActionResult.Success) {
                                latestCreatedOrder = failRes.data
                            }
                            showQrPanel = true
                        }
                    }

                    is UpiIntentResolution.TargetAppUnavailable -> {
                        // Selected UPI app is not installed or cannot handle this URI:
                        // never force an incompatible app; try chooser if other UPI apps exist,
                        // otherwise seamlessly reveal Dynamic QR while keeping selected amount.
                        val fallbackResolution = withContext(Dispatchers.IO) {
                            repository.buildCompatibleUpiIntent(order = order, targetPackage = null)
                        }
                        if (fallbackResolution is UpiIntentResolution.LaunchReady) {
                            val initRes = repository.markPaymentInitiated(
                                orderRef = order.orderRef,
                                paymentMethod = fallbackResolution.paymentMethodLabel
                            )
                            if (initRes is DonationActionResult.Success) {
                                latestCreatedOrder = initRes.data
                            }
                            runCatching {
                                upiLauncher.launch(fallbackResolution.intent)
                            }.onFailure { err ->
                                hasLaunchError = true
                                lastFailedBrand = targetBrand
                                val failRes = repository.recordPaymentLaunchFailure(
                                    orderRef = order.orderRef,
                                    paymentMethod = fallbackResolution.paymentMethodLabel,
                                    technicalReason = err.javaClass.simpleName
                                )
                                if (failRes is DonationActionResult.Success) {
                                    latestCreatedOrder = failRes.data
                                }
                                showQrPanel = true
                            }
                        } else {
                            hasLaunchError = true
                            lastFailedBrand = targetBrand
                            val res = repository.markPaymentInitiated(order.orderRef, paymentMethod = "UPI QR Fallback")
                            if (res is DonationActionResult.Success) {
                                latestCreatedOrder = res.data
                            }
                            showQrPanel = true
                        }
                    }

                    is UpiIntentResolution.NoUpiAppInstalled -> {
                        // No compatible UPI app on device — fall back cleanly to Dynamic QR
                        val res = repository.markPaymentInitiated(order.orderRef, paymentMethod = "UPI QR Fallback")
                        if (res is DonationActionResult.Success) {
                            latestCreatedOrder = res.data
                        }
                        showQrPanel = true
                    }

                    is UpiIntentResolution.InvalidPaymentUri -> {
                        hasLaunchError = true
                        lastFailedBrand = targetBrand
                        val failRes = repository.recordPaymentLaunchFailure(
                            orderRef = order.orderRef,
                            paymentMethod = targetBrand?.displayName ?: "UPI Intent",
                            technicalReason = resolution.reason
                        )
                        if (failRes is DonationActionResult.Success) {
                            latestCreatedOrder = failRes.data
                        }
                    }
                }
            } finally {
                loadingSlot = null
            }
        }
    }

    fun toggleQrPayment() {
        if (showQrPanel && activeOrder != null && effectiveAmountInr != null &&
            kotlin.math.abs(activeOrder.amountInr - effectiveAmountInr) < 0.005
        ) {
            showQrPanel = false
            return
        }
        val amount = resolveValidatedAmount() ?: return
        if (loadingSlot != null) return
        loadingSlot = PaymentActionSlot.QR
        hasLaunchError = false

        scope.launch {
            try {
                val order = ensureOrderForAmount(amount) ?: return@launch
                val initRes = repository.markPaymentInitiated(order.orderRef, paymentMethod = "UPI QR")
                if (initRes is DonationActionResult.Success) {
                    latestCreatedOrder = initRes.data
                }
                showQrPanel = true
            } finally {
                loadingSlot = null
            }
        }
    }

    /**
     * Copies the validated standard UPI payment link for the selected amount to the clipboard
     * without ever displaying recipient details in any toast or UI text.
     */
    fun copyPaymentLink() {
        val amount = resolveValidatedAmount() ?: return
        if (loadingSlot != null) return
        loadingSlot = PaymentActionSlot.COPY_LINK

        scope.launch {
            try {
                val order = ensureOrderForAmount(amount) ?: return@launch
                val validatedLink = repository.resolveValidatedPaymentUri(order).toString()
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText("Payment Link", validatedLink))
                justCopiedPaymentLink = true
            } finally {
                loadingSlot = null
            }
        }
    }

    fun retryCurrentPayment() {
        hasLaunchError = false
        val brandToRetry = lastFailedBrand
        lastFailedBrand = null
        val slot = when (brandToRetry) {
            UpiAppBrand.GOOGLE_PAY -> PaymentActionSlot.GOOGLE_PAY
            UpiAppBrand.PHONE_PE -> PaymentActionSlot.PHONE_PE
            UpiAppBrand.PAYTM -> PaymentActionSlot.PAYTM
            UpiAppBrand.BHIM -> PaymentActionSlot.BHIM
            null -> PaymentActionSlot.OTHER_UPI
        }
        initiateUpiAppPayment(slot = slot, targetBrand = brandToRetry)
    }

    fun shareActiveQr(order: DonationTransactionEntity) {
        scope.launch {
            val validatedQrUri = repository.resolveValidatedPaymentUri(order).toString()
            val uri = withContext(Dispatchers.IO) {
                UpiQrGenerator.saveShareableQrPng(context, validatedQrUri, order.orderRef)
            } ?: return@launch
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching {
                context.startActivity(Intent.createChooser(shareIntent, null))
            }
        }
    }

    fun refreshOrderStatus(order: DonationTransactionEntity) {
        if (isRefreshingStatus) return
        isRefreshingStatus = true
        scope.launch {
            try {
                val res = repository.refreshStatusFromTrustedBackend(order.orderRef)
                if (res is DonationActionResult.Success) {
                    latestCreatedOrder = res.data
                }
            } finally {
                isRefreshingStatus = false
            }
        }
    }

    // Deep AMOLED true black in dark theme; clean minimalist white in light theme
    val glassSurfaceColor = if (c.isDark) Color(0xF7000000) else Color(0xFAFFFFFF)
    val glassBorderColor = if (c.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.07f)
    val buttonSurfaceColor = if (c.isDark) Color(0xFF111113) else Color(0xFFF3F4F6)
    val selectedSurfaceColor = if (c.isDark) c.coffeeAccent.copy(alpha = 0.18f) else c.coffeeAccent.copy(alpha = 0.13f)
    @Suppress("UNUSED_EXPRESSION")
    onToast // Retained for caller compatibility; never displays recipient details.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = glassSurfaceColor,
            contentColor = c.textPrimary,
            border = BorderStroke(0.75.dp, glassBorderColor),
            shadowElevation = if (c.isDark) 8.dp else 14.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .widthIn(max = 312.dp)
                .fillMaxWidth()
                .scale(popupScale)
                .alpha(popupAlpha.coerceAtLeast(0.01f))
                .testTag("buy_me_a_coffee_sheet")
        ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Minimal top row: left tiny status indicator | center small coffee icon | right tiny close (×)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        // Top-left: tiny transaction status / compact retry indicator (only when an order exists)
                        if (activeOrder != null) {
                            CompactTransactionStatusControl(
                                order = activeOrder,
                                isRefreshing = isRefreshingStatus,
                                hasLaunchError = hasLaunchError,
                                reducedMotion = reducedMotion,
                                onRefresh = { refreshOrderStatus(activeOrder) },
                                onRetry = ::retryCurrentPayment,
                                modifier = Modifier.align(Alignment.CenterStart)
                            )
                        }

                        // Center: small animated coffee icon
                        AnimatedCoffeeCup(
                            fillFraction = cupFillFraction,
                            size = 38.dp,
                            isVerifiedPaid = isVerifiedPaid,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .testTag("coffee_cup_animation")
                        )

                        // Top-right: tiny close (×) icon inside a 48dp accessible hit target
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(48.dp)
                                .testTag("coffee_sheet_close_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = c.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Trusted backend verified-only checkmark bar (NEVER shown for unverified/PENDING orders)
                    AnimatedVisibility(
                        visible = isVerifiedPaid && activeOrder != null,
                        enter = fadeIn(tween(if (reducedMotion) 0 else 180)) + expandVertically(),
                        exit = fadeOut(tween(if (reducedMotion) 0 else 120)) + shrinkVertically()
                    ) {
                        activeOrder?.let { paidOrder ->
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(c.success.copy(alpha = if (c.isDark) 0.16f else 0.12f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("donation_verified_success_card"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                TinyAnimatedVerifiedCheckmark(
                                    reducedMotion = reducedMotion,
                                    color = c.success,
                                    modifier = Modifier.testTag("coffee_verified_checkmark")
                                )
                                Text(
                                    text = DonationConfig.formatInrAmount(paidOrder.amountInr),
                                    color = c.success,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Compact equal-sized Amount Selection Grid (☕ ₹10, ☕ ₹20, ☕ ₹50, ☕ ₹100 + Custom edit pill)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactAmountTile(
                                amountInr = 10.0,
                                selected = !isCustomSelected && selectedPresetAmount == 10.0,
                                reducedMotion = reducedMotion,
                                surfaceColor = buttonSurfaceColor,
                                selectedSurfaceColor = selectedSurfaceColor,
                                onClick = { selectAmountAndResetAttemptIfChanged(10.0, custom = false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coffee_preset_10")
                            )
                            CompactAmountTile(
                                amountInr = 20.0,
                                selected = !isCustomSelected && selectedPresetAmount == 20.0,
                                reducedMotion = reducedMotion,
                                surfaceColor = buttonSurfaceColor,
                                selectedSurfaceColor = selectedSurfaceColor,
                                onClick = { selectAmountAndResetAttemptIfChanged(20.0, custom = false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coffee_preset_20")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CompactAmountTile(
                                amountInr = 50.0,
                                selected = !isCustomSelected && selectedPresetAmount == 50.0,
                                reducedMotion = reducedMotion,
                                surfaceColor = buttonSurfaceColor,
                                selectedSurfaceColor = selectedSurfaceColor,
                                onClick = { selectAmountAndResetAttemptIfChanged(50.0, custom = false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coffee_preset_50")
                            )
                            CompactAmountTile(
                                amountInr = 100.0,
                                selected = !isCustomSelected && selectedPresetAmount == 100.0,
                                reducedMotion = reducedMotion,
                                surfaceColor = buttonSurfaceColor,
                                selectedSurfaceColor = selectedSurfaceColor,
                                onClick = { selectAmountAndResetAttemptIfChanged(100.0, custom = false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coffee_preset_100")
                            )
                        }

                        // Compact Custom Amount button with small edit icon + inline numeric field
                        CompactCustomAmountTile(
                            selected = isCustomSelected,
                            value = customAmountInput,
                            isError = showInvalidAmountHighlight,
                            reducedMotion = reducedMotion,
                            surfaceColor = buttonSurfaceColor,
                            selectedSurfaceColor = selectedSurfaceColor,
                            onSelect = { selectAmountAndResetAttemptIfChanged(effectiveAmountInr, custom = true) },
                            onValueChange = { raw ->
                                val filtered = raw.filter { it.isDigit() || it == '.' }.take(8)
                                customAmountInput = filtered
                                showInvalidAmountHighlight = false
                                isCustomSelected = true
                                currentAttemptIdempotencyKey = DonationConfig.generateIdempotencyKey()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("coffee_preset_custom")
                        )
                    }

                    SmartFadeDivider(color = c.subtleSeparator)

                    // Compact Icon-Only Payment Grid (zero text labels, zero recipient details):
                    // Row 1: Google Pay | PhonePe | Paytm | BHIM
                    // Row 2: Other UPI  | QR      | Copy Payment Link
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PaymentIconButton(
                                contentDesc = "Google Pay",
                                isLoading = loadingSlot == PaymentActionSlot.GOOGLE_PAY,
                                enabled = loadingSlot == null,
                                containerColor = buttonSurfaceColor,
                                onClick = { initiateUpiAppPayment(PaymentActionSlot.GOOGLE_PAY, UpiAppBrand.GOOGLE_PAY) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coffee_pay_gpay_btn")
                            ) {
                                AuthenticUpiBrandIcon(
                                    brand = UpiAppBrand.GOOGLE_PAY,
                                    installedIcon = installedUpiIcons.iconFor(UpiAppBrand.GOOGLE_PAY),
                                    size = 22.dp
                                )
                            }

                            PaymentIconButton(
                                contentDesc = "PhonePe",
                                isLoading = loadingSlot == PaymentActionSlot.PHONE_PE,
                                enabled = loadingSlot == null,
                                containerColor = buttonSurfaceColor,
                                onClick = { initiateUpiAppPayment(PaymentActionSlot.PHONE_PE, UpiAppBrand.PHONE_PE) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coffee_pay_phonepe_btn")
                            ) {
                                AuthenticUpiBrandIcon(
                                    brand = UpiAppBrand.PHONE_PE,
                                    installedIcon = installedUpiIcons.iconFor(UpiAppBrand.PHONE_PE),
                                    size = 22.dp
                                )
                            }

                            PaymentIconButton(
                                contentDesc = "Paytm",
                                isLoading = loadingSlot == PaymentActionSlot.PAYTM,
                                enabled = loadingSlot == null,
                                containerColor = buttonSurfaceColor,
                                onClick = { initiateUpiAppPayment(PaymentActionSlot.PAYTM, UpiAppBrand.PAYTM) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coffee_pay_paytm_btn")
                            ) {
                                AuthenticUpiBrandIcon(
                                    brand = UpiAppBrand.PAYTM,
                                    installedIcon = installedUpiIcons.iconFor(UpiAppBrand.PAYTM),
                                    size = 22.dp
                                )
                            }

                            PaymentIconButton(
                                contentDesc = "BHIM",
                                isLoading = loadingSlot == PaymentActionSlot.BHIM,
                                enabled = loadingSlot == null,
                                containerColor = buttonSurfaceColor,
                                onClick = { initiateUpiAppPayment(PaymentActionSlot.BHIM, UpiAppBrand.BHIM) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coffee_pay_bhim_btn")
                            ) {
                                AuthenticUpiBrandIcon(
                                    brand = UpiAppBrand.BHIM,
                                    installedIcon = installedUpiIcons.iconFor(UpiAppBrand.BHIM),
                                    size = 22.dp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PaymentIconButton(
                                contentDesc = "Other UPI",
                                isLoading = loadingSlot == PaymentActionSlot.OTHER_UPI,
                                enabled = loadingSlot == null,
                                containerColor = buttonSurfaceColor,
                                onClick = { initiateUpiAppPayment(PaymentActionSlot.OTHER_UPI, targetBrand = null) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coffee_pay_upi_app_btn")
                            ) {
                                OtherUpiVectorMark(size = 22.dp)
                            }

                            PaymentIconButton(
                                contentDesc = "UPI QR",
                                isLoading = loadingSlot == PaymentActionSlot.QR,
                                enabled = loadingSlot == null,
                                containerColor = if (showQrPanel) selectedSurfaceColor else buttonSurfaceColor,
                                onClick = ::toggleQrPayment,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coffee_pay_qr_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QrCode2,
                                    contentDescription = null,
                                    tint = if (showQrPanel) c.coffeeAccent else c.textPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            PaymentIconButton(
                                contentDesc = "Copy payment link",
                                isLoading = loadingSlot == PaymentActionSlot.COPY_LINK,
                                enabled = loadingSlot == null,
                                containerColor = if (justCopiedPaymentLink) {
                                    c.success.copy(alpha = if (c.isDark) 0.16f else 0.12f)
                                } else {
                                    buttonSurfaceColor
                                },
                                onClick = ::copyPaymentLink,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("coffee_copy_upi_btn")
                            ) {
                                AnimatedContent(
                                    targetState = justCopiedPaymentLink,
                                    transitionSpec = {
                                        if (reducedMotion) {
                                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                        } else {
                                            (fadeIn(tween(140)) + scaleIn(initialScale = 0.8f, animationSpec = tween(160)))
                                                .togetherWith(fadeOut(tween(100)))
                                        }
                                    },
                                    label = "copy_link_icon_morph"
                                ) { copied ->
                                    Icon(
                                        imageVector = if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                                        contentDescription = null,
                                        tint = if (copied) c.success else c.textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Compact Dynamic UPI QR Panel (revealed ONLY when QR icon is tapped; zero descriptive text)
                    AnimatedVisibility(
                        visible = showQrPanel && activeOrder != null,
                        enter = fadeIn(tween(if (reducedMotion) 0 else 170)) + expandVertically(),
                        exit = fadeOut(tween(if (reducedMotion) 0 else 120)) + shrinkVertically()
                    ) {
                        activeOrder?.let { qrOrder ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(164.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val bmp = qrBitmap
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "UPI QR",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .testTag("coffee_dynamic_qr_image")
                                        )
                                    } else {
                                        CircularProgressIndicator(
                                            color = c.coffeeAccent,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { shareActiveQr(qrOrder) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(buttonSurfaceColor)
                                            .testTag("coffee_share_qr_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Share,
                                            contentDescription = "Share QR",
                                            tint = c.textPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { refreshOrderStatus(qrOrder) },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(buttonSurfaceColor)
                                            .testTag("coffee_qr_refresh_btn")
                                    ) {
                                        if (isRefreshingStatus) {
                                            CircularProgressIndicator(
                                                color = c.coffeeAccent,
                                                strokeWidth = 1.8.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = "Refresh status",
                                                tint = c.textPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
private fun CompactAmountTile(
    amountInr: Double,
    selected: Boolean,
    reducedMotion: Boolean,
    surfaceColor: Color,
    selectedSurfaceColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val bg by animateColorAsState(
        targetValue = if (selected) selectedSurfaceColor else surfaceColor,
        animationSpec = tween(if (reducedMotion) 0 else 150),
        label = "amount_tile_bg"
    )
    val borderCol by animateColorAsState(
        targetValue = if (selected) c.coffeeAccent.copy(alpha = 0.7f) else Color.Transparent,
        animationSpec = tween(if (reducedMotion) 0 else 150),
        label = "amount_tile_border"
    )
    val scale by animateFloatAsState(
        targetValue = if (selected && !reducedMotion) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "amount_tile_scale"
    )

    Surface(
        shape = RoundedCornerShape(13.dp),
        color = bg,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "☕",
                    fontSize = 13.sp
                )
                Text(
                    text = "₹${amountInr.toInt()}",
                    color = if (selected) c.coffeeAccent else c.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(tween(if (reducedMotion) 0 else 140)) + scaleIn(initialScale = 0.4f),
                    exit = fadeOut(tween(if (reducedMotion) 0 else 100))
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(c.coffeeAccent)
                            .testTag("coffee_amount_selected_indicator")
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactCustomAmountTile(
    selected: Boolean,
    value: String,
    isError: Boolean,
    reducedMotion: Boolean,
    surfaceColor: Color,
    selectedSurfaceColor: Color,
    onSelect: () -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val bg by animateColorAsState(
        targetValue = when {
            isError -> c.error.copy(alpha = if (c.isDark) 0.16f else 0.10f)
            selected -> selectedSurfaceColor
            else -> surfaceColor
        },
        animationSpec = tween(if (reducedMotion) 0 else 150),
        label = "custom_tile_bg"
    )
    val borderCol by animateColorAsState(
        targetValue = when {
            isError -> c.error.copy(alpha = 0.85f)
            selected -> c.coffeeAccent.copy(alpha = 0.7f)
            else -> Color.Transparent
        },
        animationSpec = tween(if (reducedMotion) 0 else 150),
        label = "custom_tile_border"
    )

    Surface(
        shape = RoundedCornerShape(13.dp),
        color = bg,
        border = BorderStroke(1.dp, borderCol),
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(role = Role.RadioButton, onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = "Custom amount",
                tint = when {
                    isError -> c.error
                    selected -> c.coffeeAccent
                    else -> c.textSecondary
                },
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "₹",
                color = when {
                    isError -> c.error
                    selected -> c.coffeeAccent
                    else -> c.textPrimary
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (selected || value.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = c.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(c.coffeeAccent),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("coffee_custom_amount_input"),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (value.isEmpty()) {
                                Text(
                                    text = "0",
                                    color = c.textMuted,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
            if (isError) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = "Invalid amount",
                    tint = c.error,
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("coffee_amount_error")
                )
            } else if (selected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(c.coffeeAccent)
                        .testTag("coffee_amount_selected_indicator")
                )
            }
        }
    }
}

@Composable
private fun PaymentIconButton(
    contentDesc: String,
    isLoading: Boolean,
    enabled: Boolean,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val c = CtecsTheme.colors
    Surface(
        shape = RoundedCornerShape(13.dp),
        color = containerColor,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(13.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = contentDesc }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = c.coffeeAccent,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(18.dp)
                        .testTag("coffee_preparing_payment_indicator")
                )
            } else {
                icon()
            }
        }
    }
}

@Composable
private fun CompactTransactionStatusControl(
    order: DonationTransactionEntity,
    isRefreshing: Boolean,
    hasLaunchError: Boolean,
    reducedMotion: Boolean,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = CtecsTheme.colors
    val status = order.parsedStatus
    val showFailureRetry = hasLaunchError ||
        status == DonationStatus.FAILED ||
        status == DonationStatus.CANCELLED ||
        status == DonationStatus.EXPIRED

    val dotColor = when (status) {
        DonationStatus.PAID -> c.success
        DonationStatus.UNDER_REVIEW -> c.coffeeAccent
        DonationStatus.PENDING, DonationStatus.CREATED -> c.warning
        DonationStatus.FAILED -> c.error
        DonationStatus.CANCELLED, DonationStatus.EXPIRED -> c.textMuted
    }

    val isPulsing = !reducedMotion && !showFailureRetry && (
        status == DonationStatus.CREATED ||
            status == DonationStatus.PENDING ||
            status == DonationStatus.UNDER_REVIEW
        )
    val pulseAlpha = if (isPulsing) {
        val infinite = rememberInfiniteTransition(label = "compact_tx_pulse")
        val animated by infinite.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "compact_tx_dot_alpha"
        )
        animated
    } else {
        1f
    }

    if (showFailureRetry) {
        Row(
            modifier = modifier
                .testTag("donation_status_${status.name.lowercase(Locale.US)}"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = status.displayLabel,
                tint = if (status == DonationStatus.FAILED || hasLaunchError) c.error else c.textMuted,
                modifier = Modifier.size(15.dp)
            )
            IconButton(
                onClick = onRetry,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("coffee_retry_payment_btn")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Retry payment",
                    tint = c.coffeeAccent,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        return
    }

    IconButton(
        onClick = onRefresh,
        enabled = !isRefreshing && status != DonationStatus.PAID,
        modifier = modifier
            .size(48.dp)
            .testTag("donation_status_${status.name.lowercase(Locale.US)}")
    ) {
        when {
            isRefreshing -> {
                CircularProgressIndicator(
                    color = c.coffeeAccent,
                    strokeWidth = 1.8.dp,
                    modifier = Modifier.size(15.dp)
                )
            }
            status == DonationStatus.PAID -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Verified",
                    tint = c.success,
                    modifier = Modifier.size(16.dp)
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(dotColor.copy(alpha = pulseAlpha))
                        .semantics { contentDescription = status.displayLabel }
                )
            }
        }
    }
}

@Composable
private fun TinyAnimatedVerifiedCheckmark(
    reducedMotion: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    var target by remember { mutableFloatStateOf(if (reducedMotion) 1f else 0f) }
    LaunchedEffect(Unit) { target = 1f }
    val drawProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 280, easing = FastOutSlowInEasing),
        label = "verified_check_progress"
    )

    Canvas(
        modifier = modifier
            .size(16.dp)
            .semantics { contentDescription = "Payment verified" }
    ) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            color = color.copy(alpha = 0.22f * drawProgress),
            radius = r,
            center = center
        )
        drawCircle(
            color = color.copy(alpha = 0.85f),
            radius = r - 1.dp.toPx(),
            center = center,
            style = Stroke(width = 1.4.dp.toPx())
        )

        val checkPath = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.52f)
            lineTo(size.width * 0.44f, size.height * 0.68f)
            lineTo(size.width * 0.72f, size.height * 0.34f)
        }
        val measure = PathMeasure()
        measure.setPath(checkPath, false)
        val segment = Path()
        measure.getSegment(0f, measure.length * drawProgress.coerceIn(0f, 1f), segment, true)
        drawPath(
            path = segment,
            color = color,
            style = Stroke(
                width = 1.75.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
