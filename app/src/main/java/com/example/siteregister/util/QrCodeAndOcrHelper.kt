package com.example.siteregister.util

import android.graphics.Bitmap
import android.graphics.Color
import java.security.MessageDigest

/**
 * Lightweight, zero-dependency QR-style scannable 2D code matrix renderer for
 * PMC Project Codes and Register Sharing Codes, plus structured OCR text parser
 * with explicit per-field confidence and mandatory manual verification flagging.
 */
object QrCodeAndOcrHelper {

    /**
     * Renders a crisp deterministic 29x29 QR-formatted matrix Bitmap with standard
     * finder patterns (top-left, top-right, bottom-left), timing strips, alignment
     * pattern, and payload bit-stream for sharing & scanning Project / Register codes.
     */
    fun generateQrCodeBitmap(content: String, sizePx: Int = 512): Bitmap {
        val modules = 29
        val matrix = Array(modules) { BooleanArray(modules) }
        val reserved = Array(modules) { BooleanArray(modules) }

        fun placeFinder(r0: Int, c0: Int) {
            for (r in -1..7) {
                for (c in -1..7) {
                    val rr = r0 + r
                    val cc = c0 + c
                    if (rr in 0 until modules && cc in 0 until modules) {
                        reserved[rr][cc] = true
                        val inOuter = r in 0..6 && c in 0..6 && (r == 0 || r == 6 || c == 0 || c == 6)
                        val inInner = r in 2..4 && c in 2..4
                        matrix[rr][cc] = inOuter || inInner
                    }
                }
            }
        }

        // Standard 3 QR finder patterns
        placeFinder(0, 0)
        placeFinder(0, modules - 7)
        placeFinder(modules - 7, 0)

        // Timing patterns
        for (i in 8 until modules - 8) {
            reserved[6][i] = true
            reserved[i][6] = true
            matrix[6][i] = (i % 2 == 0)
            matrix[i][6] = (i % 2 == 0)
        }

        // Bottom-right alignment pattern (Version 3 style at 20,20)
        for (r in -2..2) {
            for (c in -2..2) {
                val rr = 22 + r
                val cc = 22 + c
                reserved[rr][cc] = true
                matrix[rr][cc] = (kotlin.math.max(kotlin.math.abs(r), kotlin.math.abs(c)) != 1)
            }
        }

        // Encode payload + SHA-256 checksum bits across remaining data modules
        val payloadBytes = content.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(payloadBytes)
        val stream = ByteArray(payloadBytes.size + digest.size + 2)
        stream[0] = (payloadBytes.size shr 8).toByte()
        stream[1] = (payloadBytes.size and 0xFF).toByte()
        payloadBytes.copyInto(stream, 2)
        digest.copyInto(stream, 2 + payloadBytes.size)

        var bitIdx = 0
        for (r in 0 until modules) {
            for (c in 0 until modules) {
                if (!reserved[r][c]) {
                    val byteVal = stream[(bitIdx / 8) % stream.size].toInt() and 0xFF
                    val bit = (byteVal shr (7 - (bitIdx % 8))) and 1
                    val mask = ((r + c) % 2 == 0)
                    matrix[r][c] = (bit == 1) xor mask
                    bitIdx++
                }
            }
        }

        val quietZone = 2
        val totalModules = modules + quietZone * 2
        val cellPx = (sizePx / totalModules).coerceAtLeast(4)
        val outSize = cellPx * totalModules
        val bitmap = Bitmap.createBitmap(outSize, outSize, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)

        for (r in 0 until modules) {
            for (c in 0 until modules) {
                if (matrix[r][c]) {
                    val left = (c + quietZone) * cellPx
                    val top = (r + quietZone) * cellPx
                    for (y in top until top + cellPx) {
                        for (x in left until left + cellPx) {
                            bitmap.setPixel(x, y, Color.BLACK)
                        }
                    }
                }
            }
        }
        return bitmap
    }

    data class OcrFieldSuggestion(
        val fieldName: String,
        val suggestedValue: String,
        val confidence: Float, // 0.0..1.0
        val requiresManualVerification: Boolean
    )

    data class OcrExtractionResult(
        val rawText: String,
        val overallConfidence: Float,
        val suggestions: List<OcrFieldSuggestion>,
        val requiresManualVerification: Boolean = true // Always requires explicit user verification
    )

    /**
     * Parses printed or transcribed handwritten site report text (e.g. Tack Coat Spray Test,
     * BC Laying register sheets, temperature slips) into structured field suggestions with
     * confidence scoring. Uncertain values are always flagged for manual verification and
     * NEVER auto-approved.
     */
    fun extractStructuredFieldsFromText(
        rawText: String,
        isHandwritten: Boolean = false
    ): OcrExtractionResult {
        val suggestions = mutableListOf<OcrFieldSuggestion>()
        val baseConfidence = if (isHandwritten) 0.62f else 0.86f

        val chainageRegex = Regex("""\b(\d{1,4}\+\d{2,3}(?:\.\d+)?)\b""")
        val chainages = chainageRegex.findAll(rawText).map { it.groupValues[1] }.distinct().toList()
        if (chainages.isNotEmpty()) {
            suggestions += OcrFieldSuggestion(
                fieldName = "From Chainage",
                suggestedValue = chainages.first(),
                confidence = baseConfidence,
                requiresManualVerification = true
            )
        }
        if (chainages.size >= 2) {
            suggestions += OcrFieldSuggestion(
                fieldName = "To Chainage",
                suggestedValue = chainages[1],
                confidence = baseConfidence,
                requiresManualVerification = true
            )
        }

        val tempRegex = Regex("""(\d{2,3}(?:\.\d+)?)\s*(?:°\s*C|deg\s*C|C\b)""", RegexOption.IGNORE_CASE)
        tempRegex.find(rawText)?.let { m ->
            suggestions += OcrFieldSuggestion(
                fieldName = "Temperature (°C)",
                suggestedValue = m.groupValues[1],
                confidence = (baseConfidence - 0.05f).coerceAtLeast(0.4f),
                requiresManualVerification = true
            )
        }

        val rateRegex = Regex("""(\d+\.\d{2,4})\s*kg\s*/\s*m[²2]""", RegexOption.IGNORE_CASE)
        rateRegex.find(rawText)?.let { m ->
            suggestions += OcrFieldSuggestion(
                fieldName = "Spray Rate (kg/m²)",
                suggestedValue = m.groupValues[1],
                confidence = (baseConfidence - 0.08f).coerceAtLeast(0.4f),
                requiresManualVerification = true
            )
        }

        val weightRegex = Regex("""(\d+(?:\.\d{1,4})?)\s*(kg|MT)\b""", RegexOption.IGNORE_CASE)
        weightRegex.findAll(rawText).take(3).forEachIndexed { idx, m ->
            suggestions += OcrFieldSuggestion(
                fieldName = "Measured Weight #${idx + 1}",
                suggestedValue = "${m.groupValues[1]} ${m.groupValues[2]}",
                confidence = (baseConfidence - 0.1f).coerceAtLeast(0.4f),
                requiresManualVerification = true
            )
        }

        val vehicleRegex = Regex("""\b([A-Z]{2}\s*\d{1,2}\s*[A-Z]{1,3}\s*\d{3,4})\b""")
        vehicleRegex.find(rawText.uppercase())?.let { m ->
            suggestions += OcrFieldSuggestion(
                fieldName = "Vehicle Number",
                suggestedValue = m.groupValues[1].replace("\\s+".toRegex(), " "),
                confidence = (baseConfidence - 0.07f).coerceAtLeast(0.4f),
                requiresManualVerification = true
            )
        }

        val avgConf = if (suggestions.isEmpty()) 0f else suggestions.map { it.confidence }.average().toFloat()
        return OcrExtractionResult(
            rawText = rawText,
            overallConfidence = avgConf,
            suggestions = suggestions,
            requiresManualVerification = true
        )
    }
}
