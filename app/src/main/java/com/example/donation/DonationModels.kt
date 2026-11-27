package com.example.donation

import android.net.Uri
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.security.SecureRandom
import java.util.Locale

/**
 * Exact, immutable donation recipient configuration for CTECS Buy Me a Coffee.
 * Recipient UPI ID and Name MUST remain exact across dynamic QR, UPI intent,
 * history records, and verified confirmation screens.
 */
object DonationConfig {
    const val UPI_ID: String = "tvtelugu@ptyes"
    const val RECIPIENT_NAME: String = "madhu"
    const val CURRENCY: String = "INR"
    const val CURRENCY_SYMBOL: String = "₹"
    const val DESCRIPTION: String = "CTECS Coffee Support"

    const val DEFAULT_MIN_AMOUNT_INR: Double = 1.0
    const val DEFAULT_MAX_AMOUNT_INR: Double = 50_000.0
    const val ORDER_EXPIRY_MS: Long = 15 * 60 * 1000L // 15 minutes

    val PRESETS: List<DonationPreset> = listOf(
        DonationPreset(amountInr = 10.0, label = "₹10", description = "Small Coffee", fillFraction = 0.34f),
        DonationPreset(amountInr = 20.0, label = "₹20", description = "Coffee", fillFraction = 0.54f),
        DonationPreset(amountInr = 50.0, label = "₹50", description = "Double Coffee", fillFraction = 0.76f),
        DonationPreset(amountInr = 100.0, label = "₹100", description = "Big Support", fillFraction = 0.94f)
    )

    private val secureRandom = SecureRandom()

    fun formatInrAmount(amountInr: Double): String {
        return if (amountInr % 1.0 == 0.0) {
            "$CURRENCY_SYMBOL${amountInr.toLong()}"
        } else {
            String.format(Locale.US, "%s%.2f", CURRENCY_SYMBOL, amountInr)
        }
    }

    fun formatUpiAmountParam(amountInr: Double): String {
        return String.format(Locale.US, "%.2f", amountInr)
    }

    /**
     * Validates a user-entered custom INR amount strictly without silently modifying it.
     */
    fun validateAmount(
        rawInput: String,
        minInr: Double = DEFAULT_MIN_AMOUNT_INR,
        maxInr: Double = DEFAULT_MAX_AMOUNT_INR
    ): AmountValidationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return AmountValidationResult.Invalid("Please enter a donation amount in INR.")
        }
        if (trimmed.startsWith("-")) {
            return AmountValidationResult.Invalid("Amount cannot be negative.")
        }
        if (!Regex("^\\d+(\\.\\d{1,2})?$").matches(trimmed)) {
            return AmountValidationResult.Invalid("Enter a valid positive INR amount (up to 2 decimal places).")
        }
        val parsed = try {
            BigDecimal(trimmed).toDouble()
        } catch (_: Exception) {
            return AmountValidationResult.Invalid("Enter a valid numeric INR amount.")
        }
        if (!parsed.isFinite() || parsed <= 0.0) {
            return AmountValidationResult.Invalid("Amount must be greater than ₹0.")
        }
        if (parsed < minInr) {
            return AmountValidationResult.Invalid("Minimum donation amount is ${formatInrAmount(minInr)}.")
        }
        if (parsed > maxInr) {
            return AmountValidationResult.Invalid("Maximum donation amount is ${formatInrAmount(maxInr)}.")
        }
        return AmountValidationResult.Valid(parsed)
    }

    /**
     * Builds the canonical, standard NPCI UPI payment Android [Uri] using [Uri.Builder].
     *
     * Produces:
     * `upi://pay?pa=tvtelugu%40ptyes&pn=madhu&am=20.00&cu=INR&tn=CTECS%20Coffee%20Support`
     *
     * Key correctness guarantees:
     * - Encodes each query parameter exactly once (never double-encoding `%40` as `%2540`).
     * - Keeps internal recipient VPA (`tvtelugu@ptyes`) and name (`madhu`) intact in the payload.
     * - Omits merchant-only P2M parameters (`tr`, `mc`, `orgid`, `mode`) that cause BHIM and
     *   other UPI apps to reject personal VPAs with "This request type is not supported".
     */
    fun buildUpiPaymentAndroidUri(amountInr: Double): Uri {
        require(amountInr.isFinite() && amountInr > 0.0) { "Amount must be positive" }
        val amountStr = formatUpiAmountParam(amountInr)
        return Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", UPI_ID)
            .appendQueryParameter("pn", RECIPIENT_NAME)
            .appendQueryParameter("am", amountStr)
            .appendQueryParameter("cu", CURRENCY)
            .appendQueryParameter("tn", DESCRIPTION)
            .build()
    }

    /**
     * String form of the validated standard UPI payment URI.
     * [orderRef] is retained as an optional parameter for signature compatibility and local
     * tracking, but is intentionally NOT appended as a P2M `tr` parameter on personal VPAs.
     */
    fun buildUpiPaymentUri(amountInr: Double, @Suppress("UNUSED_PARAMETER") orderRef: String? = null): String {
        return buildUpiPaymentAndroidUri(amountInr).toString()
    }

    /**
     * Minimal probe URI built via [Uri.Builder] for detecting installed UPI handler apps.
     */
    fun buildUpiProbeUri(): Uri {
        return Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", UPI_ID)
            .appendQueryParameter("pn", RECIPIENT_NAME)
            .appendQueryParameter("cu", CURRENCY)
            .build()
    }

    /**
     * Strictly validates a UPI payment URI before launching any external UPI application or
     * rendering a QR code. Rejects non-UPI schemes, double-encoded parameters (`%2540`),
     * mismatched recipient/currency/amount, and unsigned merchant-only P2M parameters.
     */
    fun validateUpiPaymentUri(rawUri: String?, expectedAmountInr: Double): UpiUriValidationResult {
        val candidate = rawUri?.trim().orEmpty()
        if (candidate.isEmpty()) {
            return UpiUriValidationResult.Invalid("Empty payment URI.")
        }
        if (candidate.contains("%25", ignoreCase = true)) {
            return UpiUriValidationResult.Invalid("Double-encoded payment parameter detected.")
        }
        val parsed = try {
            Uri.parse(candidate)
        } catch (_: Exception) {
            return UpiUriValidationResult.Invalid("Malformed payment URI.")
        }
        if (!parsed.scheme.equals("upi", ignoreCase = true) ||
            !parsed.authority.equals("pay", ignoreCase = true)
        ) {
            return UpiUriValidationResult.Invalid("Unsupported payment URI scheme or host.")
        }

        val pa = parsed.getQueryParameter("pa")?.trim().orEmpty()
        if (!pa.equals(UPI_ID, ignoreCase = true) || pa.contains("%40", ignoreCase = true) || !pa.contains("@")) {
            return UpiUriValidationResult.Invalid("Invalid payee address parameter.")
        }
        val pn = parsed.getQueryParameter("pn")?.trim().orEmpty()
        if (!pn.equals(RECIPIENT_NAME, ignoreCase = true)) {
            return UpiUriValidationResult.Invalid("Invalid payee name parameter.")
        }
        val cu = parsed.getQueryParameter("cu")?.trim().orEmpty()
        if (!cu.equals(CURRENCY, ignoreCase = true)) {
            return UpiUriValidationResult.Invalid("Unsupported currency parameter.")
        }
        val amStr = parsed.getQueryParameter("am")?.trim().orEmpty()
        val parsedAmount = amStr.toDoubleOrNull()
        if (parsedAmount == null || !parsedAmount.isFinite() || parsedAmount <= 0.0 ||
            kotlin.math.abs(parsedAmount - expectedAmountInr) > 0.009
        ) {
            return UpiUriValidationResult.Invalid("Invalid or mismatched amount parameter.")
        }
        val tn = parsed.getQueryParameter("tn")?.trim().orEmpty()
        if (tn.isEmpty()) {
            return UpiUriValidationResult.Invalid("Missing transaction note parameter.")
        }

        // Prevent unsigned P2M-only parameters (tr, mc, orgid) that trigger
        // "This request type is not supported" on personal UPI VPAs in BHIM / UPI apps.
        val hasMerchantSignature = !parsed.getQueryParameter("sign").isNullOrBlank()
        if (!hasMerchantSignature) {
            val unsupportedP2mKeys = listOf("tr", "mc", "orgid")
            if (unsupportedP2mKeys.any { !parsed.getQueryParameter(it).isNullOrBlank() }) {
                return UpiUriValidationResult.Invalid("Unsupported merchant-only parameter for personal VPA.")
            }
        }

        return UpiUriValidationResult.Valid(parsed)
    }

    /**
     * Strips any recipient identifiers or raw UPI query strings from technical error/audit notes.
     */
    fun sanitizeForLog(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(UPI_ID, "[redacted]", ignoreCase = true)
            .replace(Uri.encode(UPI_ID), "[redacted]", ignoreCase = true)
            .replace(Regex("upi://pay\\S*", RegexOption.IGNORE_CASE), "upi://pay?[redacted]")
            .replace(Regex("\\b${Regex.escape(RECIPIENT_NAME)}\\b", RegexOption.IGNORE_CASE), "[redacted]")
    }

    fun generateOrderRef(nowMs: Long = System.currentTimeMillis()): String {
        val rand = ByteArray(4)
        secureRandom.nextBytes(rand)
        val hex = rand.joinToString("") { "%02X".format(it) }
        return "CTECS${nowMs % 100_000_000L}$hex"
    }

    fun generateIdempotencyKey(): String {
        val bytes = ByteArray(16)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun coffeeFillFractionFor(amountInr: Double?): Float {
        if (amountInr == null || amountInr <= 0.0) return 0.18f
        return when {
            amountInr <= 10.0 -> 0.34f
            amountInr <= 20.0 -> 0.54f
            amountInr <= 50.0 -> 0.76f
            amountInr <= 100.0 -> 0.92f
            else -> 0.96f
        }
    }
}

data class DonationPreset(
    val amountInr: Double,
    val label: String,
    val description: String,
    val fillFraction: Float
)

sealed class AmountValidationResult {
    data class Valid(val amountInr: Double) : AmountValidationResult()
    data class Invalid(val message: String) : AmountValidationResult()
}

sealed class UpiUriValidationResult {
    data class Valid(val uri: Uri) : UpiUriValidationResult()
    data class Invalid(val reason: String) : UpiUriValidationResult()
}

/**
 * Full donation payment lifecycle statuses.
 * Only trusted server-side verification or authenticated admin verification with
 * independent evidence may transition a transaction to [PAID].
 */
enum class DonationStatus(val displayLabel: String) {
    CREATED("Created"),
    PENDING("Pending"),
    UNDER_REVIEW("Under Review"),
    PAID("Paid"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired");

    val isTerminal: Boolean
        get() = this == PAID || this == FAILED || this == CANCELLED || this == EXPIRED

    companion object {
        fun fromRaw(raw: String?): DonationStatus {
            val norm = raw?.trim()?.uppercase(Locale.US)?.replace(" ", "_") ?: return PENDING
            return entries.firstOrNull { it.name == norm } ?: PENDING
        }
    }
}

@Entity(
    tableName = "donation_transactions",
    indices = [
        Index(value = ["idempotencyKey"], unique = true),
        Index(value = ["ownerUserId", "createdAtMs"]),
        Index(value = ["status", "createdAtMs"])
    ]
)
data class DonationTransactionEntity(
    @PrimaryKey
    val orderRef: String,
    val gatewayOrderId: String = "",
    val idempotencyKey: String,
    val ownerUserId: String,
    val amountInr: Double,
    val currency: String = DonationConfig.CURRENCY,
    val upiId: String = DonationConfig.UPI_ID,
    val recipientName: String = DonationConfig.RECIPIENT_NAME,
    val description: String = DonationConfig.DESCRIPTION,
    val upiUri: String,
    val status: String = DonationStatus.CREATED.name,
    val paymentMethod: String = "UPI",
    val utr: String = "",
    val confirmationRef: String = "",
    val backendSynced: Boolean = false,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val verifiedAtMs: Long? = null,
    val expiresAtMs: Long
) {
    val parsedStatus: DonationStatus
        get() = DonationStatus.fromRaw(status)
}

@Entity(
    tableName = "donation_audit_logs",
    indices = [Index(value = ["orderRef", "timestampMs"])]
)
data class DonationAuditLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val orderRef: String,
    val previousStatus: String,
    val newStatus: String,
    val actorRole: String,
    val actorId: String,
    val evidence: String,
    val reason: String,
    val timestampMs: Long
)

@Dao
interface DonationDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(entity: DonationTransactionEntity)

    @Update
    suspend fun updateTransaction(entity: DonationTransactionEntity)

    @Query("SELECT * FROM donation_transactions WHERE orderRef = :orderRef LIMIT 1")
    suspend fun getByOrderRef(orderRef: String): DonationTransactionEntity?

    @Query("SELECT * FROM donation_transactions WHERE idempotencyKey = :idempotencyKey LIMIT 1")
    suspend fun getByIdempotencyKey(idempotencyKey: String): DonationTransactionEntity?

    @Query("SELECT * FROM donation_transactions WHERE ownerUserId = :ownerUserId ORDER BY createdAtMs DESC")
    fun observeTransactionsForOwner(ownerUserId: String): Flow<List<DonationTransactionEntity>>

    @Query("SELECT * FROM donation_transactions WHERE ownerUserId = :ownerUserId ORDER BY createdAtMs DESC")
    suspend fun getTransactionsForOwner(ownerUserId: String): List<DonationTransactionEntity>

    @Query("SELECT * FROM donation_transactions ORDER BY createdAtMs DESC")
    fun observeAllTransactionsAdmin(): Flow<List<DonationTransactionEntity>>

    @Query("SELECT * FROM donation_transactions ORDER BY createdAtMs DESC")
    suspend fun getAllTransactionsAdmin(): List<DonationTransactionEntity>

    @Query("SELECT COALESCE(SUM(amountInr), 0.0) FROM donation_transactions WHERE status = 'PAID'")
    fun observeTotalVerifiedInr(): Flow<Double>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuditLog(entry: DonationAuditLogEntity)

    @Query("SELECT * FROM donation_audit_logs WHERE orderRef = :orderRef ORDER BY timestampMs DESC")
    suspend fun getAuditLogsForOrder(orderRef: String): List<DonationAuditLogEntity>

    @Query("SELECT * FROM donation_audit_logs ORDER BY timestampMs DESC LIMIT 200")
    fun observeAllAuditLogs(): Flow<List<DonationAuditLogEntity>>
}
