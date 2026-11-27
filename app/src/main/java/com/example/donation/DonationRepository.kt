package com.example.donation

import android.app.Activity
import android.content.ComponentName
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
    data class ProviderRestricted(val restriction: ProviderRestriction) : UpiIntentResolution()
    data class TargetAppUnavailable(val requestedPackage: String) : UpiIntentResolution()
    data object NoUpiAppInstalled : UpiIntentResolution()
    data class InvalidPaymentUri(val reason: String) : UpiIntentResolution()
}

sealed class UpiAppAvailability {
    data class Compatible(val app: InstalledUpiApp) : UpiAppAvailability()
    data class RestrictedByProvider(val restriction: ProviderRestriction) : UpiAppAvailability()
    data class Unavailable(val packageName: String) : UpiAppAvailability()
}

data class UpiReturnEvaluation(
    val transaction: DonationTransactionEntity,
    val failureCategory: UpiFailureCategory,
    val responseCode: String,
    val providerMarkedUnavailable: Boolean,
    val allowImmediateRetry: Boolean
)

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

    private val _restrictedProviders = MutableStateFlow<Map<String, ProviderRestriction>>(loadPersistedProviderRestrictions())
    val restrictedProvidersFlow: StateFlow<Map<String, ProviderRestriction>> = _restrictedProviders.asStateFlow()

    private val _lastReturnEvaluation = MutableStateFlow<UpiReturnEvaluation?>(null)
    val lastReturnEvaluation: StateFlow<UpiReturnEvaluation?> = _lastReturnEvaluation.asStateFlow()

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

    fun getRestrictedProvider(packageName: String?): ProviderRestriction? {
        if (packageName.isNullOrBlank()) return null
        return _restrictedProviders.value[packageName]
    }

    /**
     * Marks a specific UPI payment provider package as restricted/unavailable for this donation
     * flow after a genuine provider-side security decline or UPI Risk Policy rejection (e.g. Paytm).
     * Never attempts to bypass provider security controls.
     */
    fun markProviderRestricted(
        packageName: String,
        displayName: String,
        category: UpiFailureCategory,
        responseCode: String = "",
        sanitizedReason: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return
        val entry = ProviderRestriction(
            packageName = cleanPkg,
            displayName = displayName.ifBlank { cleanPkg },
            category = category,
            responseCode = responseCode.trim().take(24),
            sanitizedReason = DonationConfig.sanitizeForLog(sanitizedReason).ifBlank { "Provider security restriction" },
            restrictedAtMs = nowMs
        )
        val updated = _restrictedProviders.value.toMutableMap().apply { put(cleanPkg, entry) }
        _restrictedProviders.value = updated
        persistProviderRestrictions(updated)
    }

    /**
     * Clears a provider restriction only after official provider/bank resolution has been completed.
     */
    fun clearProviderRestriction(packageName: String) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isEmpty()) return
        val updated = _restrictedProviders.value.toMutableMap().apply { remove(cleanPkg) }
        _restrictedProviders.value = updated
        persistProviderRestrictions(updated)
    }

    /**
     * Queries Android PackageManager for compatible installed UPI handler apps for [upiUri],
     * explicitly requiring exported `ACTION_VIEW` `upi://pay` handlers and excluding ordinary
     * web browsers so a browser or incompatible package is never reported as supported.
     */
    fun getCompatibleUpiApps(
        upiUri: Uri = DonationConfig.buildUpiProbeUri(),
        excludeRestricted: Boolean = false
    ): List<InstalledUpiApp> {
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
            val restricted = if (excludeRestricted) _restrictedProviders.value.keys else emptySet()
            resolved.mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                if (!activityInfo.exported) return@mapNotNull null
                val pkg = activityInfo.packageName ?: return@mapNotNull null
                if (isOrdinaryBrowserPackage(pkg)) return@mapNotNull null
                if (pkg in restricted) return@mapNotNull null
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
     * Evaluates the real runtime availability of a specific UPI package:
     * never treats mere package installation as UPI payment support, and respects
     * confirmed provider security/risk-policy restrictions.
     */
    fun getUpiAppAvailability(
        packageName: String,
        upiUri: Uri = DonationConfig.buildUpiProbeUri()
    ): UpiAppAvailability {
        val restriction = getRestrictedProvider(packageName)
        if (restriction != null) {
            return UpiAppAvailability.RestrictedByProvider(restriction)
        }
        val compatible = getCompatibleUpiApps(upiUri, excludeRestricted = true)
        val match = compatible.firstOrNull { it.packageName == packageName }
            ?: return UpiAppAvailability.Unavailable(packageName)
        val targetedIntent = Intent(Intent.ACTION_VIEW, upiUri).apply { setPackage(packageName) }
        return if (canResolveIntent(targetedIntent)) {
            UpiAppAvailability.Compatible(match)
        } else {
            UpiAppAvailability.Unavailable(packageName)
        }
    }

    /**
     * Resolves and validates the UPI payment [Uri] for [order]. If a persisted legacy order
     * carries an older unsupported or malformed URI (including `pa=tvtelugu%40ptyes` or `&tr=`),
     * safely regenerates the standard validated URI.
     */
    fun resolveValidatedPaymentUri(order: DonationTransactionEntity): Uri {
        return when (val checked = DonationConfig.validateUpiPaymentUri(order.upiUri, order.amountInr)) {
            is UpiUriValidationResult.Valid -> checked.uri
            is UpiUriValidationResult.Invalid -> DonationConfig.buildUpiPaymentAndroidUri(order.amountInr)
        }
    }

    /**
     * Builds a validated, compatible native Android `ACTION_VIEW` UPI payment [Intent]
     * without setting any unsupported MIME type, without forcing incompatible apps or browsers,
     * and without repeatedly launching providers restricted by UPI Risk Policy / security declines.
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

        if (!targetPackage.isNullOrBlank()) {
            val restriction = getRestrictedProvider(targetPackage)
            if (restriction != null) {
                return UpiIntentResolution.ProviderRestricted(restriction)
            }
        }

        val allCompatibleApps = getCompatibleUpiApps(validatedUri, excludeRestricted = false)
        if (allCompatibleApps.isEmpty()) {
            return UpiIntentResolution.NoUpiAppInstalled
        }

        if (!targetPackage.isNullOrBlank()) {
            val exactMatch = allCompatibleApps.firstOrNull { it.packageName == targetPackage }
                ?: return UpiIntentResolution.TargetAppUnavailable(targetPackage)
            val targetedIntent = Intent(Intent.ACTION_VIEW, validatedUri).apply {
                setPackage(exactMatch.packageName)
            }
            if (!canResolveIntent(targetedIntent)) {
                return UpiIntentResolution.TargetAppUnavailable(targetPackage)
            }
            return UpiIntentResolution.LaunchReady(
                intent = targetedIntent,
                paymentMethodLabel = targetDisplayName ?: exactMatch.displayName,
                validatedUri = validatedUri
            )
        }

        val unrestrictedApps = getCompatibleUpiApps(validatedUri, excludeRestricted = true)
        if (unrestrictedApps.isEmpty()) {
            val firstRestriction = allCompatibleApps
                .firstNotNullOfOrNull { getRestrictedProvider(it.packageName) }
            if (firstRestriction != null) {
                return UpiIntentResolution.ProviderRestricted(firstRestriction)
            }
            return UpiIntentResolution.NoUpiAppInstalled
        }

        val baseIntent = Intent(Intent.ACTION_VIEW, validatedUri)
        val chooserIntent = Intent.createChooser(baseIntent, null)
        // Exclude security-restricted provider components from Android's chooser (API 24+)
        val excludedComponents = resolveComponentsForPackages(validatedUri, _restrictedProviders.value.keys)
        if (excludedComponents.isNotEmpty()) {
            chooserIntent.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excludedComponents.toTypedArray())
        }
        return UpiIntentResolution.LaunchReady(
            intent = chooserIntent,
            paymentMethodLabel = "UPI Intent",
            validatedUri = validatedUri
        )
    }

    private fun resolveComponentsForPackages(upiUri: Uri, packages: Set<String>): List<ComponentName> {
        if (packages.isEmpty()) return emptyList()
        return try {
            val pm = context.packageManager
            val probe = Intent(Intent.ACTION_VIEW, upiUri)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    probe,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            }
            resolved.mapNotNull { info ->
                val ai = info.activityInfo ?: return@mapNotNull null
                if (ai.packageName in packages && !ai.name.isNullOrBlank()) {
                    ComponentName(ai.packageName, ai.name)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
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
     * CRITICAL SECURITY & COMPLIANCE RULES:
     * - NEVER marks a donation PAID based on UPI app return data (`Status=SUCCESS` or `RESULT_OK`),
     *   because intent results are client-side and untrusted.
     * - Classifies provider security declines, UPI Risk Policy rejections (Paytm), unsupported
     *   request errors (BHIM), VPA unavailability, bank declines, network issues, and user
     *   cancellations independently.
     * - When a provider declines for security/risk policy, records the sanitized failure code
     *   and marks that provider unavailable rather than repeatedly initiating failed retries.
     */
    suspend fun handleUpiAppReturn(
        orderRef: String,
        resultCode: Int,
        responseString: String?,
        nowMs: Long = System.currentTimeMillis(),
        launchedPackage: String? = null,
        paymentMethodLabel: String? = null
    ): DonationActionResult<DonationTransactionEntity> = withContext(Dispatchers.IO) {
        val detailed = handleUpiAppReturnDetailed(
            orderRef = orderRef,
            resultCode = resultCode,
            responseString = responseString,
            nowMs = nowMs,
            launchedPackage = launchedPackage,
            paymentMethodLabel = paymentMethodLabel
        )
        when (detailed) {
            is DonationActionResult.Success -> DonationActionResult.Success(detailed.data.transaction, detailed.message)
            is DonationActionResult.Error -> detailed
        }
    }

    suspend fun handleUpiAppReturnDetailed(
        orderRef: String,
        resultCode: Int,
        responseString: String?,
        nowMs: Long = System.currentTimeMillis(),
        launchedPackage: String? = null,
        paymentMethodLabel: String? = null
    ): DonationActionResult<UpiReturnEvaluation> = withContext(Dispatchers.IO) {
        val current = donationDao.getByOrderRef(orderRef)
            ?: return@withContext DonationActionResult.Error("Donation order not found.")
        if (current.parsedStatus == DonationStatus.PAID) {
            val eval = UpiReturnEvaluation(
                transaction = current,
                failureCategory = UpiFailureCategory.NONE,
                responseCode = "",
                providerMarkedUnavailable = false,
                allowImmediateRetry = false
            )
            _lastReturnEvaluation.value = eval
            return@withContext DonationActionResult.Success(eval)
        }

        val parsedStatusParam = extractUpiResponseParam(responseString, "Status")?.uppercase(Locale.US)
        val responseCode = extractUpiResponseParam(responseString, "responseCode")
            ?: extractUpiResponseParam(responseString, "respCode")
            ?: ""
        val providerMessage = extractUpiResponseParam(responseString, "message")
            ?: extractUpiResponseParam(responseString, "errorMessage")
            ?: extractUpiResponseParam(responseString, "reason")
            ?: ""

        val failureCategory = classifyUpiReturnFailure(
            resultCode = resultCode,
            statusParam = parsedStatusParam,
            responseCode = responseCode,
            providerMessage = providerMessage,
            rawResponse = responseString,
            launchedPackage = launchedPackage
        )

        val newStatus = when (failureCategory) {
            UpiFailureCategory.USER_CANCELLED -> DonationStatus.CANCELLED
            UpiFailureCategory.UNSUPPORTED_REQUEST,
            UpiFailureCategory.PROVIDER_SECURITY_DECLINE,
            UpiFailureCategory.RECIPIENT_RISK_RESTRICTION,
            UpiFailureCategory.RECIPIENT_VPA_UNAVAILABLE,
            UpiFailureCategory.BANK_OR_PROVIDER_DECLINE,
            UpiFailureCategory.NETWORK_INTERRUPTION,
            UpiFailureCategory.APP_UNAVAILABLE -> DonationStatus.FAILED
            UpiFailureCategory.NONE -> DonationStatus.PENDING // NEVER PAID on client intent return!
        }

        val safeProviderNote = DonationConfig.sanitizeForLog(
            buildString {
                append(failureCategory.name)
                if (responseCode.isNotBlank()) append(" code=").append(responseCode.take(24))
                if (providerMessage.isNotBlank()) append(" msg=").append(providerMessage.take(120))
            }
        )

        var markedUnavailable = false
        val effectivePkg = launchedPackage?.trim().orEmpty()
        if (effectivePkg.isNotEmpty() && (
                failureCategory == UpiFailureCategory.PROVIDER_SECURITY_DECLINE ||
                    failureCategory == UpiFailureCategory.RECIPIENT_RISK_RESTRICTION
                )
        ) {
            markProviderRestricted(
                packageName = effectivePkg,
                displayName = paymentMethodLabel ?: current.paymentMethod,
                category = failureCategory,
                responseCode = responseCode,
                sanitizedReason = safeProviderNote,
                nowMs = nowMs
            )
            markedUnavailable = true
        }

        val updated = current.copy(
            status = newStatus.name,
            paymentMethod = paymentMethodLabel?.ifBlank { current.paymentMethod } ?: current.paymentMethod,
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
                evidence = DonationConfig.sanitizeForLog(
                    "pkg=${effectivePkg.ifBlank { "chooser" }}, resultCode=$resultCode, statusParam=${parsedStatusParam ?: "NONE"}, respCode=${responseCode.ifBlank { "NONE" }}"
                ),
                reason = when (newStatus) {
                    DonationStatus.PENDING -> "Returned from UPI app — kept PENDING awaiting trusted backend verification"
                    DonationStatus.CANCELLED -> "Payment cancelled in UPI app"
                    else -> "Provider reported failure ($safeProviderNote)"
                },
                timestampMs = nowMs
            )
        )

        val evaluation = UpiReturnEvaluation(
            transaction = updated,
            failureCategory = failureCategory,
            responseCode = responseCode,
            providerMarkedUnavailable = markedUnavailable,
            allowImmediateRetry = !failureCategory.blocksImmediateRetry && !markedUnavailable
        )
        _lastReturnEvaluation.value = evaluation

        val msg = when (failureCategory) {
            UpiFailureCategory.USER_CANCELLED -> "Payment cancelled."
            UpiFailureCategory.PROVIDER_SECURITY_DECLINE,
            UpiFailureCategory.RECIPIENT_RISK_RESTRICTION -> "Provider security policy declined payment."
            UpiFailureCategory.UNSUPPORTED_REQUEST -> "Request type not supported by selected payment app."
            UpiFailureCategory.RECIPIENT_VPA_UNAVAILABLE -> "Recipient payment address unavailable."
            UpiFailureCategory.NETWORK_INTERRUPTION -> "Network interruption during payment."
            UpiFailureCategory.BANK_OR_PROVIDER_DECLINE -> "Payment declined by bank or provider."
            UpiFailureCategory.APP_UNAVAILABLE -> "Selected payment app unavailable."
            UpiFailureCategory.NONE -> "Payment pending trusted verification."
        }
        DonationActionResult.Success(evaluation, msg)
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
                evidence = DonationConfig.sanitizeForLog(cleanEvidence),
                reason = DonationConfig.sanitizeForLog(cleanReason),
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

    private fun loadPersistedProviderRestrictions(): Map<String, ProviderRestriction> {
        val raw = prefs.getString(KEY_RESTRICTED_PROVIDERS_JSON, null) ?: return emptyMap()
        return try {
            val root = JSONObject(raw)
            val result = LinkedHashMap<String, ProviderRestriction>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val pkg = keys.next()
                val obj = root.optJSONObject(pkg) ?: continue
                val catName = obj.optString("category", UpiFailureCategory.PROVIDER_SECURITY_DECLINE.name)
                val cat = UpiFailureCategory.entries.firstOrNull { it.name == catName }
                    ?: UpiFailureCategory.PROVIDER_SECURITY_DECLINE
                result[pkg] = ProviderRestriction(
                    packageName = pkg,
                    displayName = obj.optString("displayName", pkg),
                    category = cat,
                    responseCode = obj.optString("responseCode", ""),
                    sanitizedReason = DonationConfig.sanitizeForLog(obj.optString("sanitizedReason", "")),
                    restrictedAtMs = obj.optLong("restrictedAtMs", 0L)
                )
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun persistProviderRestrictions(map: Map<String, ProviderRestriction>) {
        val root = JSONObject()
        map.forEach { (pkg, entry) ->
            val obj = JSONObject()
                .put("displayName", entry.displayName)
                .put("category", entry.category.name)
                .put("responseCode", entry.responseCode)
                .put("sanitizedReason", DonationConfig.sanitizeForLog(entry.sanitizedReason))
                .put("restrictedAtMs", entry.restrictedAtMs)
            root.put(pkg, obj)
        }
        prefs.edit().putString(KEY_RESTRICTED_PROVIDERS_JSON, root.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "ctecs_donation_secure_prefs"
        private const val KEY_DONOR_OWNER_ID = "donor_owner_id"
        private const val KEY_ADMIN_ID = "admin_id"
        private const val KEY_ADMIN_PASS_SALT = "admin_pass_salt"
        private const val KEY_ADMIN_PASS_HASH = "admin_pass_hash"
        private const val KEY_RESTRICTED_PROVIDERS_JSON = "restricted_upi_providers_json"
        const val PAYTM_PACKAGE_NAME = "net.one97.paytm"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val RISK_POLICY_CODES = setOf("RN", "RNF", "U16", "U28", "RM", "ZD", "RISK")
        private val SECURITY_DECLINE_CODES = setOf("XP", "ZM", "SEC", "SE")
        private val VPA_UNAVAILABLE_CODES = setOf("ZH", "U17", "U18", "XJ", "VPA_ERR")
        private val NETWORK_ERROR_CODES = setOf("U69", "U67", "NET", "TIMEOUT")

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

        /**
         * Validates that a configured backend verification URL uses HTTPS and points
         * to a public host (blocking loopback, private, and link-local metadata addresses).
         */
        fun isValidTrustedBackendUrl(url: String): Boolean {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return false
            val parsed = try {
                Uri.parse(trimmed)
            } catch (_: Exception) {
                return false
            }
            if (!parsed.scheme.equals("https", ignoreCase = true)) return false
            val host = parsed.host?.trim()?.lowercase(Locale.US).orEmpty()
            if (host.isEmpty()) return false
            if (host == "localhost" || host == "0.0.0.0" || host == "::1" || host == "[::1]") return false
            if (host.startsWith("127.") || host.startsWith("10.") || host.startsWith("169.254.") || host.startsWith("192.168.")) {
                return false
            }
            val octets = host.split(".")
            if (octets.size == 4 && octets[0] == "172") {
                val second = octets[1].toIntOrNull()
                if (second != null && second in 16..31) return false
            }
            return true
        }

        internal fun classifyUpiReturnFailure(
            resultCode: Int,
            statusParam: String?,
            responseCode: String,
            providerMessage: String,
            rawResponse: String?,
            launchedPackage: String?
        ): UpiFailureCategory {
            val codeUpper = responseCode.trim().uppercase(Locale.US)
            val decodedRaw = rawResponse?.let { runCatching { Uri.decode(it) }.getOrDefault(it) }
            val decodedMsg = runCatching { Uri.decode(providerMessage) }.getOrDefault(providerMessage)
            val combinedText = listOfNotNull(decodedMsg, decodedRaw)
                .joinToString(" ")
                .lowercase(Locale.US)

            if (statusParam == "CANCELLED" || statusParam == "CANCELED") {
                return UpiFailureCategory.USER_CANCELLED
            }
            if (resultCode == Activity.RESULT_CANCELED &&
                statusParam.isNullOrBlank() &&
                codeUpper.isEmpty() &&
                providerMessage.isBlank()
            ) {
                return UpiFailureCategory.USER_CANCELLED
            }

            // Error C: Unsupported request type (e.g., BHIM "This request type is not supported")
            if (combinedText.contains("not supported") ||
                combinedText.contains("unsupported") ||
                combinedText.contains("request type")
            ) {
                return UpiFailureCategory.UNSUPPORTED_REQUEST
            }

            // Error A: Provider UPI Risk Policy restriction (e.g., Paytm Risk Policy alert)
            if (codeUpper in RISK_POLICY_CODES ||
                combinedText.contains("risk policy") ||
                combinedText.contains("risk restriction") ||
                combinedText.contains("risk")
            ) {
                return UpiFailureCategory.RECIPIENT_RISK_RESTRICTION
            }

            // Error B: Security decline (e.g., Paytm "Your payment is declined for security reasons")
            if (codeUpper in SECURITY_DECLINE_CODES ||
                combinedText.contains("security reason") ||
                combinedText.contains("security policy") ||
                combinedText.contains("declined for security") ||
                (launchedPackage == PAYTM_PACKAGE_NAME &&
                    (statusParam == "FAILURE" || statusParam == "FAILED") &&
                    combinedText.contains("declined"))
            ) {
                return UpiFailureCategory.PROVIDER_SECURITY_DECLINE
            }

            // Recipient VPA unavailable / invalid
            if (codeUpper in VPA_UNAVAILABLE_CODES ||
                combinedText.contains("invalid vpa") ||
                combinedText.contains("vpa unavailable") ||
                combinedText.contains("account not found")
            ) {
                return UpiFailureCategory.RECIPIENT_VPA_UNAVAILABLE
            }

            // Network / timeout interruption
            if (codeUpper in NETWORK_ERROR_CODES ||
                combinedText.contains("network") ||
                combinedText.contains("timeout") ||
                combinedText.contains("timed out") ||
                combinedText.contains("no internet")
            ) {
                return UpiFailureCategory.NETWORK_INTERRUPTION
            }

            // Generic provider or bank decline
            if (statusParam == "FAILURE" || statusParam == "FAILED") {
                return UpiFailureCategory.BANK_OR_PROVIDER_DECLINE
            }

            // Submitted / Success / OK client returns stay PENDING until trusted verification
            return UpiFailureCategory.NONE
        }

        internal fun extractUpiResponseParam(response: String?, key: String): String? {
            if (response.isNullOrBlank()) return null
            val raw = response.split("&")
                .map { it.split("=", limit = 2) }
                .firstOrNull { it.size == 2 && it[0].trim().equals(key, ignoreCase = true) }
                ?.get(1)
                ?.trim()
                ?: return null
            return runCatching { Uri.decode(raw) }.getOrDefault(raw)
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
