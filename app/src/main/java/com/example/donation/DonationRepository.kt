package com.example.donation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class AdminSession(
    val adminId: String,
    val bearerToken: String,
    val isBackendAuthenticated: Boolean,
    val authenticatedAtMs: Long
)

data class InstalledUpiApp(
    val packageName: String,
    val displayName: String
)

sealed class DonationActionResult<out T> {
    data class Success<T>(val data: T, val message: String = "") : DonationActionResult<T>()
    data class Error(val message: String) : DonationActionResult<Nothing>()
}

sealed class UpiIntentResolution {
    data class LaunchReady(val intent: Intent, val paymentMethodLabel: String, val validatedUri: Uri) : UpiIntentResolution()
    data class TargetAppUnavailable(val requestedPackage: String) : UpiIntentResolution()
    data object NoUpiAppInstalled : UpiIntentResolution()
    data class InvalidPaymentUri(val reason: String) : UpiIntentResolution()
}

/**
 * Coordinates CTECS Buy Me a Coffee donations, local Room transaction storage,
 * HTTPS communication with the trusted CTECS backend (never embedding gateway secrets
 * in the APK), native UPI intent helpers, UTR review submission, and protected
 * administrator verification with immutable audit trails.
 */
class DonationRepository(
    private val context: Context,
    private val donationDao: DonationDao,
    private val backendBaseUrl: String = "",
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val orderMutex = Mutex()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()

    private val _adminSession = MutableStateFlow<AdminSession?>(null)
    val adminSession: StateFlow<AdminSession?> = _adminSession.asStateFlow()

    val currentOwnerUserId: String
        get() {
            val existing = prefs.getString(KEY_DONOR_OWNER_ID, null)
            if (!existing.isNullOrBlank()) return existing
            val created = "donor_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            prefs.edit().putString(KEY_DONOR_OWNER_ID, created).apply()
            return created
        }

    val isBackendConfigured: Boolean
        get() = isValidTrustedBackendUrl(backendBaseUrl)

    val hasLocalAdminClaimed: Boolean
        get() = !prefs.getString(KEY_ADMIN_PASS_HASH, null).isNullOrBlank()

    fun observeOwnDonations(): Flow<List<DonationTransactionEntity>> =
        donationDao.observeTransactionsForOwner(currentOwnerUserId)

    suspend fun getOwnDonations(): List<DonationTransactionEntity> =
        donationDao.getTransactionsForOwner(currentOwnerUserId)

    fun observeAdminTransactions(): Flow<List<DonationTransactionEntity>> =
        donationDao.observeAllTransactionsAdmin()

    fun observeTotalVerifiedInr(): Flow<Double> =
        donationDao.observeTotalVerifiedInr()

    fun observeAuditLogs(): Flow<List<DonationAuditLogEntity>> =
        donationDao.observeAllAuditLogs()

    suspend fun getAuditLogsForOrder(orderRef: String): List<DonationAuditLogEntity> =
        donationDao.getAuditLogsForOrder(orderRef)

    /**
     * Queries Android PackageManager for compatible installed UPI handler apps for [upiUri],
     * explicitly excluding ordinary web browsers so a browser or WebView is never launched.
     */
    fun getCompatibleUpiApps(upiUri: Uri = DonationConfig.buildUpiProbeUri()): List<InstalledUpiApp> {
        return try {
            val probeIntent = Intent(Intent.ACTION_VIEW, upiUri)
            val pm = context.packageManager
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    probeIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(probeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolved.mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (isOrdinaryBrowserPackage(pkg)) return@mapNotNull null
                val label = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                InstalledUpiApp(pkg, label)
            }.distinctBy { it.packageName }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Queries Android PackageManager for compatible installed UPI apps without
     * forcing a single payment provider.
     */
    fun getInstalledUpiApps(): List<InstalledUpiApp> = getCompatibleUpiApps(DonationConfig.buildUpiProbeUri())

    /**
     * Resolves and validates the UPI payment [Uri] for [order]. If a persisted legacy order
     * carries an older unsupported or malformed URI, safely regenerates the standard validated URI.
     */
    fun resolveValidatedPaymentUri(order: DonationTransactionEntity): Uri {
        return when (val checked = DonationConfig.validateUpiPaymentUri(order.upiUri, order.amountInr)) {
            is UpiUriValidationResult.Valid -> checked.uri
            is UpiUriValidationResult.Invalid -> DonationConfig.buildUpiPaymentAndroidUri(order.amountInr)
        }
    }

    /**
     * Builds a validated, compatible native Android `ACTION_VIEW` UPI payment [Intent]
     * without setting any unsupported MIME type and without forcing incompatible apps or browsers.
     */
    fun buildCompatibleUpiIntent(
        order: DonationTransactionEntity,
        targetPackage: String? = null,
        targetDisplayName: String? = null
    ): UpiIntentResolution {
        val validatedUri = resolveValidatedPaymentUri(order)
        val recheck = DonationConfig.validateUpiPaymentUri(validatedUri.toString(), order.amountInr)
        if (recheck is UpiUriValidationResult.Invalid) {
            return UpiIntentResolution.InvalidPaymentUri(recheck.reason)
        }

        val compatibleApps = getCompatibleUpiApps(validatedUri)
        if (compatibleApps.isEmpty()) {
            return UpiIntentResolution.NoUpiAppInstalled
        }

        if (!targetPackage.isNullOrBlank()) {
            val exactMatch = compatibleApps.firstOrNull { it.packageName == targetPackage }
                ?: return UpiIntentResolution.TargetAppUnavailable(targetPackage)
            val targetedIntent = Intent(Intent.ACTION_VIEW, validatedUri).apply {
                setPackage(exactMatch.packageName)
            }
            val resolvesDirectly = canResolveIntent(targetedIntent)
            if (!resolvesDirectly) {
                return UpiIntentResolution.TargetAppUnavailable(targetPackage)
            }
            return UpiIntentResolution.LaunchReady(
                intent = targetedIntent,
                paymentMethodLabel = targetDisplayName ?: exactMatch.displayName,
                validatedUri = validatedUri
            )
        }

        val baseIntent = Intent(Intent.ACTION_VIEW, validatedUri)
        val chooserIntent = Intent.createChooser(baseIntent, null)
        return UpiIntentResolution.LaunchReady(
            intent = chooserIntent,
            paymentMethodLabel = "UPI Intent",
            validatedUri = validatedUri
        )
    }

    private fun canResolveIntent(intent: Intent): Boolean {
        return try {
            val pm = context.packageManager
            val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            }
            matches.any { info ->
                val pkg = info.activityInfo?.packageName
                !pkg.isNullOrBlank() && !isOrdinaryBrowserPackage(pkg)
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates a unique donation order with strict idempotency protection so duplicate
     * taps or retries never spawn duplicate orders.
     */
    suspend fun createOrReuseDonationOrder(
        amountInr: Double,
        idempotencyKey: String,
        nowMs: Long = System.currentTimeMillis()
    ): DonationActionResult<DonationTransactionEntity> = withContext(Dispatchers.IO) {
        val validation = DonationConfig.validateAmount(DonationConfig.formatUpiAmountParam(amountInr))
        if (validation is AmountValidationResult.Invalid) {
            return@withContext DonationActionResult.Error(validation.message)
        }
        val cleanKey = idempotencyKey.trim()
        if (cleanKey.length < 8) {
            return@withContext DonationActionResult.Error("Invalid idempotency key.")
        }

        orderMutex.withLock {
            // 1. Idempotent lookup: return existing non-expired order for this attempt
            val existing = donationDao.getByIdempotencyKey(cleanKey)
            if (existing != null) {
                val repairedUri = resolveValidatedPaymentUri(existing).toString()
                if (repairedUri != existing.upiUri) {
                    val normalized = existing.copy(upiUri = repairedUri, updatedAtMs = nowMs)
                    donationDao.updateTransaction(normalized)
                    return@withLock DonationActionResult.Success(normalized, "Reused existing donation order.")
                }
                return@withLock DonationActionResult.Success(existing, "Reused existing donation order.")
            }

            val orderRef = DonationConfig.generateOrderRef(nowMs)
            val canonicalUpiUri = DonationConfig.buildUpiPaymentUri(amountInr)
            var gatewayOrderId = ""
            var resolvedUpiUri = canonicalUpiUri
            var synced = false

            // 2. If trusted CTECS backend HTTPS URL is configured (wrapping mynkdev-21/upi-gateway
            // server-side with credentials kept exclusively on backend), create order server-side
            if (isBackendConfigured) {
                val remoteResult = createOrderOnTrustedBackend(
                    orderRef = orderRef,
                    amountInr = amountInr,
                    idempotencyKey = cleanKey
                )
                if (remoteResult != null) {
                    gatewayOrderId = remoteResult.gatewayOrderId
                    val validatedRemote = DonationConfig.validateUpiPaymentUri(remoteResult.upiUri, amountInr)
                    if (validatedRemote is UpiUriValidationResult.Valid) {
                        resolvedUpiUri = validatedRemote.uri.toString()
                    }
                    synced = true
                }
            }

            val entity = DonationTransactionEntity(
                orderRef = orderRef,
                gatewayOrderId = gatewayOrderId,
                idempotencyKey = cleanKey,
                ownerUserId = currentOwnerUserId,
                amountInr = amountInr,
                currency = DonationConfig.CURRENCY,
                upiId = DonationConfig.UPI_ID,
                recipientName = DonationConfig.RECIPIENT_NAME,
                description = DonationConfig.DESCRIPTION,
                upiUri = resolvedUpiUri,
                status = DonationStatus.CREATED.name,
                paymentMethod = "UPI",
                utr = "",
                confirmationRef = "",
                backendSynced = synced,
                createdAtMs = nowMs,
                updatedAtMs = nowMs,
                verifiedAtMs = null,
                expiresAtMs = nowMs + DonationConfig.ORDER_EXPIRY_MS
            )
            donationDao.insertTransaction(entity)
            donationDao.insertAuditLog(
                DonationAuditLogEntity(
                    orderRef = orderRef,
                    previousStatus = "NONE",
                    newStatus = DonationStatus.CREATED.name,
                    actorRole = "DONOR",
                    actorId = currentOwnerUserId,
                    evidence = if (synced) "Trusted backend order $gatewayOrderId" else "Local dynamic UPI order created",
                    reason = "Donation order initialized for ${DonationConfig.formatInrAmount(amountInr)}",
                    timestampMs = nowMs
                )
            )
            DonationActionResult.Success(entity, "Donation order created.")
        }
    }

    /**
     * Marks an order as PENDING when the user launches a UPI app or displays the QR.
     * Never transitions to PAID.
     */
    suspend fun markPaymentInitiated(
        orderRef: String,
        paymentMethod: String = "UPI Intent",
        nowMs: Long = System.currentTimeMillis()
    ): DonationActionResult<DonationTransactionEntity> = withContext(Dispatchers.IO) {
        val current = donationDao.getByOrderRef(orderRef)
            ?: return@withContext DonationActionResult.Error("Donation order not found.")
        if (current.parsedStatus.isTerminal) {
            return@withContext DonationActionResult.Success(current)
        }
        val updated = current.copy(
            status = DonationStatus.PENDING.name,
            paymentMethod = paymentMethod,
            updatedAtMs = nowMs
        )
        donationDao.updateTransaction(updated)
        donationDao.insertAuditLog(
            DonationAuditLogEntity(
                orderRef = orderRef,
                previousStatus = current.status,
                newStatus = DonationStatus.PENDING.name,
                actorRole = "DONOR",
                actorId = currentOwnerUserId,
                evidence = paymentMethod,
                reason = "UPI payment flow initiated; awaiting trusted verification",
                timestampMs = nowMs
            )
        )
        DonationActionResult.Success(updated, "Payment pending verification.")
    }

    /**
     * Records a sanitized payment launch failure while preserving the transaction record so
     * the user can immediately select another compatible UPI app or scan Dynamic QR.
     */
    suspend fun recordPaymentLaunchFailure(
        orderRef: String,
        paymentMethod: String,
        technicalReason: String,
        nowMs: Long = System.currentTimeMillis()
    ): DonationActionResult<DonationTransactionEntity> = withContext(Dispatchers.IO) {
        val current = donationDao.getByOrderRef(orderRef)
            ?: return@withContext DonationActionResult.Error("Donation order not found.")
        if (current.parsedStatus == DonationStatus.PAID) {
            return@withContext DonationActionResult.Success(current)
        }
        val safeReason = DonationConfig.sanitizeForLog(technicalReason).ifBlank { "UPI app launch unavailable" }
        val updated = current.copy(
            status = DonationStatus.FAILED.name,
            paymentMethod = paymentMethod,
            updatedAtMs = nowMs
        )
        donationDao.updateTransaction(updated)
        donationDao.insertAuditLog(
            DonationAuditLogEntity(
                orderRef = orderRef,
                previousStatus = current.status,
                newStatus = DonationStatus.FAILED.name,
                actorRole = "CLIENT_INTENT",
                actorId = currentOwnerUserId,
                evidence = paymentMethod,
                reason = safeReason,
                timestampMs = nowMs
            )
        )
        DonationActionResult.Success(updated, "Unable to open payment app.")
    }

    /**
     * Handles the raw Android ActivityResult returned by a native UPI app.
     *
     * CRITICAL SECURITY RULE:
     * NEVER marks a donation PAID based on UPI app return data (`Status=SUCCESS` or `RESULT_OK`),
     * because intent results are client-side and untrusted. Only cancellation/failure is recorded
     * immediately; otherwise the donation remains strictly PENDING until trusted server verification.
     */
    suspend fun handleUpiAppReturn(
        orderRef: String,
        resultCode: Int,
        responseString: String?,
        nowMs: Long = System.currentTimeMillis()
    ): DonationActionResult<DonationTransactionEntity> = withContext(Dispatchers.IO) {
        val current = donationDao.getByOrderRef(orderRef)
            ?: return@withContext DonationActionResult.Error("Donation order not found.")
        if (current.parsedStatus == DonationStatus.PAID) {
            return@withContext DonationActionResult.Success(current)
        }

        val parsedStatusParam = extractUpiResponseParam(responseString, "Status")?.uppercase(Locale.US)
        val newStatus = when {
            resultCode == Activity.RESULT_CANCELED && parsedStatusParam.isNullOrBlank() -> DonationStatus.CANCELLED
            parsedStatusParam == "FAILURE" || parsedStatusParam == "FAILED" -> DonationStatus.FAILED
            parsedStatusParam == "CANCELLED" || parsedStatusParam == "CANCELED" -> DonationStatus.CANCELLED
            else -> DonationStatus.PENDING // NEVER PAID on client intent return!
        }

        val updated = current.copy(
            status = newStatus.name,
            updatedAtMs = nowMs
        )
        donationDao.updateTransaction(updated)
        donationDao.insertAuditLog(
            DonationAuditLogEntity(
                orderRef = orderRef,
                previousStatus = current.status,
                newStatus = newStatus.name,
                actorRole = "CLIENT_INTENT",
                actorId = currentOwnerUserId,
                evidence = "resultCode=$resultCode, statusParam=${parsedStatusParam ?: "NONE"}",
                reason = if (newStatus == DonationStatus.PENDING) {
                    "Returned from UPI app — kept PENDING pending trusted verification"
                } else {
                    "UPI app reported ${newStatus.displayLabel}"
                },
                timestampMs = nowMs
            )
        )
        val msg = when (newStatus) {
            DonationStatus.CANCELLED -> "Payment cancelled. You can retry or change the amount anytime."
            DonationStatus.FAILED -> "UPI app reported payment failure."
            else -> "Payment submitted in UPI app. Status remains Pending until verified — you may also submit your 12-digit UTR for review."
        }
        DonationActionResult.Success(updated, msg)
    }

    /**
     * Accepts a donor-submitted UTR reference for generic UPI ID payments.
     * Strictly transitions the transaction to [DonationStatus.UNDER_REVIEW] — NEVER [DonationStatus.PAID].
     */
    suspend fun submitUtrForReview(
        orderRef: String,
        rawUtr: String,
        nowMs: Long = System.currentTimeMillis()
    ): DonationActionResult<DonationTransactionEntity> = withContext(Dispatchers.IO) {
        val cleanUtr = rawUtr.trim().uppercase(Locale.US)
        if (!Regex("^[A-Z0-9]{8,22}$").matches(cleanUtr)) {
            return@withContext DonationActionResult.Error("Enter a valid UTR / UPI reference number (8–22 letters/digits).")
        }
        val current = donationDao.getByOrderRef(orderRef)
            ?: return@withContext DonationActionResult.Error("Donation order not found.")
        if (current.ownerUserId != currentOwnerUserId) {
            return@withContext DonationActionResult.Error("Not authorized to update this donation record.")
        }
        if (current.parsedStatus == DonationStatus.PAID) {
            return@withContext DonationActionResult.Success(current, "Donation is already verified.")
        }

        if (isBackendConfigured) {
            submitUtrToTrustedBackend(orderRef, cleanUtr)
        }

        val updated = current.copy(
            utr = cleanUtr,
            status = DonationStatus.UNDER_REVIEW.name,
            updatedAtMs = nowMs
        )
        donationDao.updateTransaction(updated)
        donationDao.insertAuditLog(
            DonationAuditLogEntity(
                orderRef = orderRef,
                previousStatus = current.status,
                newStatus = DonationStatus.UNDER_REVIEW.name,
                actorRole = "DONOR",
                actorId = currentOwnerUserId,
                evidence = "UTR:$cleanUtr",
                reason = "Donor submitted UTR; queued for authorized verification (not auto-confirmed)",
                timestampMs = nowMs
            )
        )
        DonationActionResult.Success(
            updated,
            "UTR recorded. Status is Under Review until verified by administrator or payment server."
        )
    }

    suspend fun cancelOrder(
        orderRef: String,
        nowMs: Long = System.currentTimeMillis()
    ): DonationActionResult<DonationTransactionEntity> = withContext(Dispatchers.IO) {
        val current = donationDao.getByOrderRef(orderRef)
            ?: return@withContext DonationActionResult.Error("Donation order not found.")
        if (current.parsedStatus == DonationStatus.PAID) {
            return@withContext DonationActionResult.Error("Verified donation cannot be cancelled.")
        }
        val updated = current.copy(status = DonationStatus.CANCELLED.name, updatedAtMs = nowMs)
        donationDao.updateTransaction(updated)
        donationDao.insertAuditLog(
            DonationAuditLogEntity(
                orderRef = orderRef,
                previousStatus = current.status,
                newStatus = DonationStatus.CANCELLED.name,
                actorRole = "DONOR",
                actorId = currentOwnerUserId,
                evidence = "User cancellation",
                reason = "Donor cancelled pending payment",
                timestampMs = nowMs
            )
        )
        DonationActionResult.Success(updated, "Donation cancelled.")
    }

    /**
     * Polls trusted CTECS backend for authoritative payment verification status, and
     * marks stale CREATED/PENDING orders EXPIRED after their TTL window.
     */
    suspend fun refreshStatusFromTrustedBackend(
        orderRef: String,
        nowMs: Long = System.currentTimeMillis()
    ): DonationActionResult<DonationTransactionEntity> = withContext(Dispatchers.IO) {
        val current = donationDao.getByOrderRef(orderRef)
            ?: return@withContext DonationActionResult.Error("Donation order not found.")

        // Expire stale unpaid orders
        if ((current.parsedStatus == DonationStatus.CREATED || current.parsedStatus == DonationStatus.PENDING) &&
            nowMs > current.expiresAtMs
        ) {
            val expired = current.copy(status = DonationStatus.EXPIRED.name, updatedAtMs = nowMs)
            donationDao.updateTransaction(expired)
            donationDao.insertAuditLog(
                DonationAuditLogEntity(
                    orderRef = orderRef,
                    previousStatus = current.status,
                    newStatus = DonationStatus.EXPIRED.name,
                    actorRole = "SYSTEM",
                    actorId = "expiry_check",
                    evidence = "TTL exceeded",
                    reason = "Dynamic QR session expired without verified payment",
                    timestampMs = nowMs
                )
            )
            return@withContext DonationActionResult.Success(expired, "Payment session expired.")
        }

        if (!isBackendConfigured) {
            return@withContext DonationActionResult.Success(
                current,
                "Awaiting server/admin verification. Live gateway verification requires the trusted CTECS backend URL."
            )
        }

        val remote = fetchOrderStatusFromTrustedBackend(orderRef)
            ?: return@withContext DonationActionResult.Error("Unable to reach verification server. Status remains ${current.parsedStatus.displayLabel}.")

        // Strict validation: never accept PAID unless backend explicitly marks serverVerified=true and matches amount & INR
        val nextStatus = if (remote.status == DonationStatus.PAID) {
            val amountMatches = kotlin.math.abs(remote.amountInr - current.amountInr) <= 0.009
            val currencyMatches = remote.currency.equals(DonationConfig.CURRENCY, ignoreCase = true)
            if (remote.serverVerified && amountMatches && currencyMatches) DonationStatus.PAID else current.parsedStatus
        } else {
            remote.status
        }

        if (nextStatus != current.parsedStatus || remote.confirmationRef.isNotBlank()) {
            val updated = current.copy(
                status = nextStatus.name,
                confirmationRef = remote.confirmationRef.ifBlank { current.confirmationRef },
                utr = remote.utr.ifBlank { current.utr },
                verifiedAtMs = if (nextStatus == DonationStatus.PAID) (remote.verifiedAtMs ?: nowMs) else current.verifiedAtMs,
                updatedAtMs = nowMs,
                backendSynced = true
            )
            donationDao.updateTransaction(updated)
            donationDao.insertAuditLog(
                DonationAuditLogEntity(
                    orderRef = orderRef,
                    previousStatus = current.status,
                    newStatus = nextStatus.name,
                    actorRole = "TRUSTED_BACKEND",
                    actorId = "ctecs_backend",
                    evidence = remote.confirmationRef.ifBlank { "Server status check" },
                    reason = "Status synchronized from trusted CTECS backend",
                    timestampMs = nowMs
                )
            )
            return@withContext DonationActionResult.Success(updated, "Status updated: ${nextStatus.displayLabel}")
        }
        DonationActionResult.Success(current, "Current status: ${current.parsedStatus.displayLabel}")
    }

    // =========================================================================
    // PROTECTED ADMINISTRATOR AUTHENTICATION & VERIFICATION
    // =========================================================================

    /**
     * Authenticates an administrator. If a trusted backend is configured, verifies against
     * `POST /api/admin/auth/login`. In local preview/offline mode, supports a secure
     * one-time first-run admin passphrase claim hashed with salted PBKDF2-HMAC-SHA256.
     */
    suspend fun authenticateAdmin(
        adminId: String,
        passphrase: String,
        isInitialClaim: Boolean = false
    ): DonationActionResult<AdminSession> = withContext(Dispatchers.IO) {
        val cleanId = adminId.trim()
        if (cleanId.length < 3) {
            return@withContext DonationActionResult.Error("Enter a valid administrator ID (at least 3 characters).")
        }
        if (passphrase.length < 8) {
            return@withContext DonationActionResult.Error("Administrator passphrase must be at least 8 characters.")
        }

        if (isBackendConfigured) {
            val remoteToken = loginAdminOnTrustedBackend(cleanId, passphrase)
                ?: return@withContext DonationActionResult.Error("Administrator authentication failed on server.")
            val session = AdminSession(
                adminId = cleanId,
                bearerToken = remoteToken,
                isBackendAuthenticated = true,
                authenticatedAtMs = System.currentTimeMillis()
            )
            _adminSession.value = session
            return@withContext DonationActionResult.Success(session, "Administrator authenticated.")
        }

        val storedHash = prefs.getString(KEY_ADMIN_PASS_HASH, null)
        val storedSalt = prefs.getString(KEY_ADMIN_PASS_SALT, null)
        val storedAdminId = prefs.getString(KEY_ADMIN_ID, null)

        if (storedHash.isNullOrBlank() || storedSalt.isNullOrBlank()) {
            if (!isInitialClaim) {
                return@withContext DonationActionResult.Error("No administrator claimed yet. Complete first-run admin setup.")
            }
            val salt = ByteArray(16).also { secureRandom.nextBytes(it) }
            val saltHex = salt.toHexString()
            val hashHex = pbkdf2Hex(passphrase, salt)
            prefs.edit()
                .putString(KEY_ADMIN_ID, cleanId)
                .putString(KEY_ADMIN_PASS_SALT, saltHex)
                .putString(KEY_ADMIN_PASS_HASH, hashHex)
                .apply()
            val session = AdminSession(
                adminId = cleanId,
                bearerToken = "local_${DonationConfig.generateIdempotencyKey()}",
                isBackendAuthenticated = false,
                authenticatedAtMs = System.currentTimeMillis()
            )
            _adminSession.value = session
            return@withContext DonationActionResult.Success(session, "Administrator access claimed and unlocked.")
        }

        val saltBytes = hexToBytes(storedSalt)
        val computedHash = pbkdf2Hex(passphrase, saltBytes)
        val idMatches = storedAdminId.equals(cleanId, ignoreCase = true)
        val passMatches = MessageDigest.isEqual(
            computedHash.toByteArray(Charsets.UTF_8),
            storedHash.toByteArray(Charsets.UTF_8)
        )
        if (!idMatches || !passMatches) {
            return@withContext DonationActionResult.Error("Invalid administrator credentials.")
        }

        val session = AdminSession(
            adminId = cleanId,
            bearerToken = "local_${DonationConfig.generateIdempotencyKey()}",
            isBackendAuthenticated = false,
            authenticatedAtMs = System.currentTimeMillis()
        )
        _adminSession.value = session
        DonationActionResult.Success(session, "Administrator unlocked.")
    }

    fun logoutAdmin() {
        _adminSession.value = null
    }

    /**
     * Protected administrator manual verification with mandatory independent evidence,
     * verification reason, and immutable audit history.
     */
    suspend fun adminVerifyTransaction(
        orderRef: String,
        approveAsPaid: Boolean,
        independentEvidence: String,
        verificationReason: String,
        nowMs: Long = System.currentTimeMillis()
    ): DonationActionResult<DonationTransactionEntity> = withContext(Dispatchers.IO) {
        val session = _adminSession.value
            ?: return@withContext DonationActionResult.Error("Administrator authentication required.")

        val cleanEvidence = independentEvidence.trim()
        val cleanReason = verificationReason.trim()
        if (cleanEvidence.length < 6) {
            return@withContext DonationActionResult.Error("Independent bank/statement verification evidence is required.")
        }
        if (cleanReason.length < 5) {
            return@withContext DonationActionResult.Error("Verification reason is required for the audit log.")
        }

        val current = donationDao.getByOrderRef(orderRef)
            ?: return@withContext DonationActionResult.Error("Donation order not found.")
        if (current.parsedStatus == DonationStatus.PAID && approveAsPaid) {
            return@withContext DonationActionResult.Success(current, "Already verified.")
        }

        if (isBackendConfigured && session.isBackendAuthenticated) {
            val remoteOk = postAdminVerifyToBackend(
                bearerToken = session.bearerToken,
                orderRef = orderRef,
                approveAsPaid = approveAsPaid,
                evidence = cleanEvidence,
                reason = cleanReason
            )
            if (!remoteOk) {
                return@withContext DonationActionResult.Error("Backend rejected verification request.")
            }
        }

        val newStatus = if (approveAsPaid) DonationStatus.PAID else DonationStatus.FAILED
        val confirmRef = if (approveAsPaid) {
            "VER-${current.utr.ifBlank { orderRef.takeLast(6) }}-${nowMs % 10000}"
        } else {
            current.confirmationRef
        }

        val updated = current.copy(
            status = newStatus.name,
            confirmationRef = confirmRef,
            verifiedAtMs = if (approveAsPaid) nowMs else current.verifiedAtMs,
            updatedAtMs = nowMs
        )
        donationDao.updateTransaction(updated)
        donationDao.insertAuditLog(
            DonationAuditLogEntity(
                orderRef = orderRef,
                previousStatus = current.status,
                newStatus = newStatus.name,
                actorRole = "ADMIN",
                actorId = session.adminId,
                evidence = cleanEvidence,
                reason = cleanReason,
                timestampMs = nowMs
            )
        )
        DonationActionResult.Success(
            updated,
            if (approveAsPaid) "Donation verified as PAID with audit record." else "Donation marked FAILED with audit record."
        )
    }

    /**
     * Generates a formula-injection-safe CSV export of donations for authenticated administrators.
     */
    suspend fun exportAdminReportCsv(transactions: List<DonationTransactionEntity>): DonationActionResult<Uri> =
        withContext(Dispatchers.IO) {
            if (_adminSession.value == null) {
                return@withContext DonationActionResult.Error("Administrator authentication required to export reports.")
            }
            return@withContext try {
                val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val sb = StringBuilder()
                sb.appendLine("OrderRef,DateTime,AmountINR,Currency,Recipient,UpiId,Method,Status,UTR,ConfirmationRef")
                transactions.forEach { tx ->
                    val cols = listOf(
                        tx.orderRef,
                        fmt.format(Date(tx.createdAtMs)),
                        DonationConfig.formatUpiAmountParam(tx.amountInr),
                        tx.currency,
                        tx.recipientName,
                        tx.upiId,
                        tx.paymentMethod,
                        tx.status,
                        tx.utr,
                        tx.confirmationRef
                    ).joinToString(",") { sanitizeCsvCell(it) }
                    sb.appendLine(cols)
                }
                val outDir = File(context.cacheDir, "donation_reports").apply { mkdirs() }
                val outFile = File(outDir, "ctecs_donations_${System.currentTimeMillis()}.csv")
                outFile.writeText(sb.toString(), Charsets.UTF_8)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
                DonationActionResult.Success(uri, "Donation report exported.")
            } catch (e: Exception) {
                DonationActionResult.Error("Failed to export report: ${e.message}")
            }
        }

    // =========================================================================
    // TRUSTED BACKEND HTTPS CLIENT HELPERS (ZERO GATEWAY SECRETS IN APK)
    // =========================================================================

    private data class RemoteOrderResponse(val gatewayOrderId: String, val upiUri: String)
    private data class RemoteStatusResponse(
        val status: DonationStatus,
        val serverVerified: Boolean,
        val amountInr: Double,
        val currency: String,
        val utr: String,
        val confirmationRef: String,
        val verifiedAtMs: Long?
    )

    private fun createOrderOnTrustedBackend(
        orderRef: String,
        amountInr: Double,
        idempotencyKey: String
    ): RemoteOrderResponse? {
        return try {
            val payload = JSONObject().apply {
                put("order_ref", orderRef)
                put("amount", amountInr)
                put("currency", DonationConfig.CURRENCY)
                put("description", DonationConfig.DESCRIPTION)
                put("donor_id", currentOwnerUserId)
            }.toString()
            val req = Request.Builder()
                .url("${backendBaseUrl.trimEnd('/')}/api/donations/orders")
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Donor-Session", currentOwnerUserId)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = JSONObject(resp.body?.string().orEmpty())
                RemoteOrderResponse(
                    gatewayOrderId = body.optString("gateway_order_id", body.optString("id", "")),
                    upiUri = body.optString("upi_uri", "")
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun submitUtrToTrustedBackend(orderRef: String, utr: String): Boolean {
        return try {
            val payload = JSONObject().put("utr", utr).toString()
            val req = Request.Builder()
                .url("${backendBaseUrl.trimEnd('/')}/api/donations/orders/${Uri.encode(orderRef)}/utr")
                .header("X-Donor-Session", currentOwnerUserId)
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun fetchOrderStatusFromTrustedBackend(orderRef: String): RemoteStatusResponse? {
        return try {
            val req = Request.Builder()
                .url("${backendBaseUrl.trimEnd('/')}/api/donations/orders/${Uri.encode(orderRef)}")
                .header("X-Donor-Session", currentOwnerUserId)
                .get()
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = JSONObject(resp.body?.string().orEmpty())
                RemoteStatusResponse(
                    status = DonationStatus.fromRaw(body.optString("status")),
                    serverVerified = body.optBoolean("verified", false),
                    amountInr = body.optDouble("amount", Double.NaN),
                    currency = body.optString("currency", ""),
                    utr = body.optString("utr", ""),
                    confirmationRef = body.optString("confirmation_ref", ""),
                    verifiedAtMs = if (body.has("verified_at_ms") && !body.isNull("verified_at_ms")) body.optLong("verified_at_ms") else null
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun loginAdminOnTrustedBackend(adminId: String, passphrase: String): String? {
        return try {
            val payload = JSONObject()
                .put("admin_id", adminId)
                .put("passphrase", passphrase)
                .toString()
            val req = Request.Builder()
                .url("${backendBaseUrl.trimEnd('/')}/api/admin/auth/login")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = JSONObject(resp.body?.string().orEmpty())
                body.optString("token", "").takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun postAdminVerifyToBackend(
        bearerToken: String,
        orderRef: String,
        approveAsPaid: Boolean,
        evidence: String,
        reason: String
    ): Boolean {
        return try {
            val payload = JSONObject()
                .put("approve", approveAsPaid)
                .put("evidence", evidence)
                .put("reason", reason)
                .toString()
            val req = Request.Builder()
                .url("${backendBaseUrl.trimEnd('/')}/api/admin/donations/${Uri.encode(orderRef)}/verify")
                .header("Authorization", "Bearer $bearerToken")
                .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val PREFS_NAME = "ctecs_donation_secure_prefs"
        private const val KEY_DONOR_OWNER_ID = "donor_owner_id"
        private const val KEY_ADMIN_ID = "admin_id"
        private const val KEY_ADMIN_PASS_SALT = "admin_pass_salt"
        private const val KEY_ADMIN_PASS_HASH = "admin_pass_hash"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val ORDINARY_BROWSER_PACKAGES = setOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.brave.browser",
            "com.microsoft.emmx",
            "com.sec.android.app.sbrowser",
            "com.duckduckgo.mobile.android",
            "com.android.browser"
        )

        fun isOrdinaryBrowserPackage(packageName: String): Boolean {
            val lower = packageName.trim().lowercase(Locale.US)
            return lower in ORDINARY_BROWSER_PACKAGES ||
                lower.contains(".browser") ||
                lower.contains("webview")
        }

        fun isValidTrustedBackendUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return false
            val parsed = try {
                Uri.parse(trimmed)
            } catch (_: Exception) {
                return false
            }
            return parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()
        }

        internal fun extractUpiResponseParam(response: String?, key: String): String? {
            if (response.isNullOrBlank()) return null
            return response.split("&")
                .map { it.split("=", limit = 2) }
                .firstOrNull { it.size == 2 && it[0].trim().equals(key, ignoreCase = true) }
                ?.get(1)
                ?.trim()
        }

        internal fun sanitizeCsvCell(raw: String): String {
            val cleaned = raw.replace("\r", " ").replace("\n", " ")
            val formulaSafe = if (cleaned.isNotEmpty() && cleaned[0] in charArrayOf('=', '+', '-', '@', '\t')) {
                "'$cleaned"
            } else {
                cleaned
            }
            return "\"${formulaSafe.replace("\"", "\"\"")}\""
        }

        private fun pbkdf2Hex(passphrase: String, salt: ByteArray): String {
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, 120_000, 256)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return factory.generateSecret(spec).encoded.toHexString()
        }

        private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

        private fun hexToBytes(hex: String): ByteArray {
            val len = hex.length / 2
            return ByteArray(len) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
    }
}
