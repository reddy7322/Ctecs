package com.example.donation

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Cryptographic webhook signature and event verifier matching mynkdev-21/upi-gateway's
 * timestamped HMAC-SHA256 webhook contract (`t=<unix_seconds>,v1=<hex_hmac>` over
 * `<timestamp>.<raw_body>`).
 *
 * NOTE: Webhook secrets MUST reside exclusively on the trusted CTECS backend server
 * (never embedded in the Android APK). This pure Kotlin verifier mirrors the backend
 * verification contract for shared protocol validation and unit testing.
 */
object DonationWebhookVerifier {

    const val DEFAULT_TOLERANCE_SECONDS: Long = 300L // 5 minutes replay protection window

    sealed class VerificationResult {
        data class Accepted(
            val eventId: String,
            val eventType: String,
            val orderRef: String,
            val amountInr: Double,
            val currency: String,
            val targetStatus: DonationStatus,
            val confirmationRef: String
        ) : VerificationResult()

        data class Rejected(val reason: String) : VerificationResult()
    }

    /**
     * Computes the canonical timestamped HMAC-SHA256 header (`t=<ts>,v1=<hex>`) over
     * `<timestampSeconds>.<rawBody>`.
     */
    fun signPayload(rawBody: String, secret: String, timestampSeconds: Long): String {
        val hex = computeHmacSha256Hex("$timestampSeconds.$rawBody", secret)
        return "t=$timestampSeconds,v1=$hex"
    }

    fun computeHmacSha256Hex(message: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(message.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verifies raw body HMAC signature, timestamp tolerance, event deduplication,
     * order ID match, amount & INR currency match, and safe state transition ordering.
     */
    fun verifyAndParseWebhook(
        rawBody: String,
        signatureHeader: String?,
        webhookSecret: String,
        expectedOrderRef: String,
        expectedAmountInr: Double,
        currentStatus: DonationStatus,
        processedEventIds: Set<String>,
        nowSeconds: Long = System.currentTimeMillis() / 1000L,
        toleranceSeconds: Long = DEFAULT_TOLERANCE_SECONDS
    ): VerificationResult {
        if (webhookSecret.isBlank()) {
            return VerificationResult.Rejected("Webhook signing secret is not configured.")
        }
        if (signatureHeader.isNullOrBlank()) {
            return VerificationResult.Rejected("Missing webhook signature header.")
        }

        val parsedSig = parseSignatureHeader(signatureHeader)
            ?: return VerificationResult.Rejected("Malformed webhook signature header.")

        // 1. Timestamp replay protection
        if (abs(nowSeconds - parsedSig.timestampSeconds) > toleranceSeconds) {
            return VerificationResult.Rejected("Webhook timestamp outside replay tolerance window.")
        }

        // 2. Constant-time HMAC-SHA256 verification over raw request body
        val expectedHex = computeHmacSha256Hex("${parsedSig.timestampSeconds}.$rawBody", webhookSecret)
        val expectedBytes = expectedHex.lowercase().toByteArray(StandardCharsets.UTF_8)
        val receivedBytes = parsedSig.v1Hex.lowercase().toByteArray(StandardCharsets.UTF_8)
        if (!MessageDigest.isEqual(expectedBytes, receivedBytes)) {
            return VerificationResult.Rejected("Invalid HMAC-SHA256 webhook signature.")
        }

        // 3. Parse JSON payload safely
        val json = try {
            JSONObject(rawBody)
        } catch (_: Exception) {
            return VerificationResult.Rejected("Invalid webhook JSON payload.")
        }

        val eventId = json.optString("event_id", "").trim()
        if (eventId.isEmpty()) {
            return VerificationResult.Rejected("Missing event_id in webhook payload.")
        }
        if (eventId in processedEventIds) {
            return VerificationResult.Rejected("Duplicate webhook event_id ignored.")
        }

        val eventType = json.optString("event", "").trim().lowercase()
        val data = json.optJSONObject("data") ?: json
        val orderRef = data.optString("order_id", data.optString("order_ref", "")).trim()
        if (orderRef != expectedOrderRef) {
            return VerificationResult.Rejected("Webhook order_id mismatch.")
        }

        val currency = data.optString("currency", "").trim().uppercase()
        if (currency != DonationConfig.CURRENCY) {
            return VerificationResult.Rejected("Unsupported currency: $currency (expected INR).")
        }

        val amount = data.optDouble("amount", Double.NaN)
        if (!amount.isFinite() || abs(amount - expectedAmountInr) > 0.009) {
            return VerificationResult.Rejected("Webhook amount mismatch.")
        }

        val targetStatus = when (eventType) {
            "order.paid" -> DonationStatus.PAID
            "order.expired" -> DonationStatus.EXPIRED
            "order.failed" -> DonationStatus.FAILED
            "order.cancelled" -> DonationStatus.CANCELLED
            "order.under_review" -> DonationStatus.UNDER_REVIEW
            else -> return VerificationResult.Rejected("Unhandled webhook event type: $eventType")
        }

        // 4. Safe event ordering: never downgrade an already-verified PAID transaction
        if (currentStatus == DonationStatus.PAID && targetStatus != DonationStatus.PAID) {
            return VerificationResult.Rejected("Out-of-order event ignored for already PAID transaction.")
        }

        val confirmationRef = data.optString("utr", data.optString("confirmation_ref", "")).trim()

        return VerificationResult.Accepted(
            eventId = eventId,
            eventType = eventType,
            orderRef = orderRef,
            amountInr = amount,
            currency = currency,
            targetStatus = targetStatus,
            confirmationRef = confirmationRef
        )
    }

    private data class ParsedSignature(val timestampSeconds: Long, val v1Hex: String)

    private fun parseSignatureHeader(header: String): ParsedSignature? {
        var ts: Long? = null
        var v1: String? = null
        header.split(",").forEach { part ->
            val kv = part.trim().split("=", limit = 2)
            if (kv.size == 2) {
                when (kv[0].trim()) {
                    "t" -> ts = kv[1].trim().toLongOrNull()
                    "v1" -> v1 = kv[1].trim()
                }
            }
        }
        val tVal = ts ?: return null
        val v1Val = v1?.takeIf { it.length == 64 && it.all { ch -> ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F' } }
            ?: return null
        return ParsedSignature(tVal, v1Val)
    }
}
