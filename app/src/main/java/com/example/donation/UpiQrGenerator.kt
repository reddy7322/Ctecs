package com.example.donation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin ISO/IEC 18004 Byte-mode ECC-M QR Code generator (Versions 1..10).
 * Generates genuinely scannable dynamic UPI QR matrices and bitmaps offline without
 * external library bloat, supporting up to 213 UTF-8 bytes per QR code.
 */
object UpiQrGenerator {

    data class QrMatrix(
        val size: Int,
        val modules: BooleanArray
    ) {
        fun get(x: Int, y: Int): Boolean = modules[y * size + x]
    }

    private data class VersionSpec(
        val version: Int,
        val totalCodewords: Int,
        val ecCodewordsPerBlock: Int,
        val numBlocksG1: Int,
        val dataCodewordsG1: Int,
        val numBlocksG2: Int = 0,
        val dataCodewordsG2: Int = 0,
        val alignmentCenters: IntArray = intArrayOf()
    ) {
        val totalDataCodewords: Int
            get() = numBlocksG1 * dataCodewordsG1 + numBlocksG2 * dataCodewordsG2
        val byteCapacity: Int
            get() = totalDataCodewords - if (version <= 9) 2 else 3
    }

    // Standard ISO/IEC 18004 ECC Level M parameters for Versions 1..10
    private val VERSION_SPECS = listOf(
        VersionSpec(1, 26, 10, 1, 16, alignmentCenters = intArrayOf()),
        VersionSpec(2, 44, 16, 1, 28, alignmentCenters = intArrayOf(6, 18)),
        VersionSpec(3, 70, 26, 1, 44, alignmentCenters = intArrayOf(6, 22)),
        VersionSpec(4, 100, 18, 2, 32, alignmentCenters = intArrayOf(6, 26)),
        VersionSpec(5, 134, 24, 2, 43, alignmentCenters = intArrayOf(6, 30)),
        VersionSpec(6, 172, 16, 4, 27, alignmentCenters = intArrayOf(6, 34)),
        VersionSpec(7, 196, 18, 4, 31, alignmentCenters = intArrayOf(6, 22, 38)),
        VersionSpec(8, 242, 22, 2, 38, 2, 39, alignmentCenters = intArrayOf(6, 24, 42)),
        VersionSpec(9, 292, 22, 3, 36, 2, 37, alignmentCenters = intArrayOf(6, 26, 46)),
        VersionSpec(10, 346, 26, 4, 43, 1, 44, alignmentCenters = intArrayOf(6, 28, 50))
    )

    // Precomputed Version Information (18-bit BCH) for Versions 7..10
    private val VERSION_INFO_BITS = mapOf(
        7 to 0x07C94,
        8 to 0x085BC,
        9 to 0x09A99,
        10 to 0x0A4D3
    )

    // Precomputed Format Information (15-bit BCH XOR 0x5412) for ECC Level M (00) & Masks 0..7
    private val FORMAT_INFO_M = intArrayOf(
        0x5412, 0x5125, 0x5E7C, 0x5B4B, 0x45F9, 0x40CE, 0x4F97, 0x4AA0
    )

    // GF(256) exp/log tables with primitive polynomial 0x11D
    private val gfExp = IntArray(512)
    private val gfLog = IntArray(256)

    init {
        var x = 1
        for (i in 0 until 255) {
            gfExp[i] = x
            gfLog[x] = i
            x = x shl 1
            if (x and 0x100 != 0) x = x xor 0x11D
        }
        for (i in 255 until 512) {
            gfExp[i] = gfExp[i - 255]
        }
    }

    private fun gfMul(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        return gfExp[gfLog[a] + gfLog[b]]
    }

    private fun rsGeneratorPoly(degree: Int): IntArray {
        var poly = intArrayOf(1)
        for (i in 0 until degree) {
            val next = IntArray(poly.size + 1)
            val root = gfExp[i]
            for (j in poly.indices) {
                next[j] = next[j] xor poly[j]
                next[j + 1] = next[j + 1] xor gfMul(poly[j], root)
            }
            poly = next
        }
        return poly
    }

    private fun rsRemainder(data: IntArray, ecLen: Int): IntArray {
        val gen = rsGeneratorPoly(ecLen)
        val rem = IntArray(ecLen)
        for (b in data) {
            val factor = b xor rem[0]
            System.arraycopy(rem, 1, rem, 0, ecLen - 1)
            rem[ecLen - 1] = 0
            for (j in 0 until ecLen) {
                rem[j] = rem[j] xor gfMul(gen[j + 1], factor)
            }
        }
        return rem
    }

    fun encode(payload: String): QrMatrix {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        val spec = VERSION_SPECS.firstOrNull { bytes.size <= it.byteCapacity }
            ?: throw IllegalArgumentException("Payload too long for QR Version 1..10 (${bytes.size} bytes)")

        // 1. Build bit buffer (Byte Mode 0100 + length + payload + terminator + pad)
        val bits = ArrayList<Int>(spec.totalDataCodewords * 8)
        fun appendBits(value: Int, length: Int) {
            for (i in (length - 1) downTo 0) {
                bits.add((value ushr i) and 1)
            }
        }
        appendBits(0b0100, 4) // Byte mode indicator
        appendBits(bytes.size, if (spec.version <= 9) 8 else 16)
        for (b in bytes) {
            appendBits(b.toInt() and 0xFF, 8)
        }

        val totalDataBits = spec.totalDataCodewords * 8
        val terminatorLen = minOf(4, totalDataBits - bits.size)
        appendBits(0, terminatorLen)
        while (bits.size % 8 != 0) bits.add(0)

        var padToggle = true
        while (bits.size < totalDataBits) {
            appendBits(if (padToggle) 0xEC else 0x11, 8)
            padToggle = !padToggle
        }

        val dataCodewords = IntArray(spec.totalDataCodewords) { idx ->
            var v = 0
            for (bit in 0 until 8) {
                v = (v shl 1) or bits[idx * 8 + bit]
            }
            v
        }

        // 2. Split into blocks & compute Reed-Solomon EC codewords
        val dataBlocks = mutableListOf<IntArray>()
        val ecBlocks = mutableListOf<IntArray>()
        var offset = 0
        repeat(spec.numBlocksG1) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.dataCodewordsG1)
            offset += spec.dataCodewordsG1
            dataBlocks.add(block)
            ecBlocks.add(rsRemainder(block, spec.ecCodewordsPerBlock))
        }
        repeat(spec.numBlocksG2) {
            val block = dataCodewords.copyOfRange(offset, offset + spec.dataCodewordsG2)
            offset += spec.dataCodewordsG2
            dataBlocks.add(block)
            ecBlocks.add(rsRemainder(block, spec.ecCodewordsPerBlock))
        }

        // 3. Interleave data & EC codewords
        val finalCodewords = ArrayList<Int>(spec.totalCodewords)
        val maxDataLen = maxOf(spec.dataCodewordsG1, spec.dataCodewordsG2)
        for (i in 0 until maxDataLen) {
            for (block in dataBlocks) {
                if (i < block.size) finalCodewords.add(block[i])
            }
        }
        for (i in 0 until spec.ecCodewordsPerBlock) {
            for (ec in ecBlocks) {
                finalCodewords.add(ec[i])
            }
        }

        // 4. Construct module matrix & function mask
        val size = 17 + 4 * spec.version
        val modules = BooleanArray(size * size)
        val isFunction = BooleanArray(size * size)

        fun setFn(x: Int, y: Int, dark: Boolean) {
            if (x in 0 until size && y in 0 until size) {
                modules[y * size + x] = dark
                isFunction[y * size + x] = true
            }
        }

        fun drawFinder(cx: Int, cy: Int) {
            for (dy in -4..4) {
                for (dx in -4..4) {
                    val xx = cx + dx
                    val yy = cy + dy
                    if (xx in 0 until size && yy in 0 until size) {
                        val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                        setFn(xx, yy, dist != 2 && dist != 4)
                    }
                }
            }
        }

        drawFinder(3, 3)
        drawFinder(size - 4, 3)
        drawFinder(3, size - 4)

        // Alignment patterns
        val centers = spec.alignmentCenters
        for (cy in centers) {
            for (cx in centers) {
                val overlapsFinder = (cx == 6 && cy == 6) ||
                    (cx == 6 && cy == centers.last()) ||
                    (cx == centers.last() && cy == 6)
                if (!overlapsFinder) {
                    for (dy in -2..2) {
                        for (dx in -2..2) {
                            val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                            setFn(cx + dx, cy + dy, dist != 1)
                        }
                    }
                }
            }
        }

        // Timing patterns
        for (i in 0 until size) {
            if (!isFunction[6 * size + i]) setFn(i, 6, i % 2 == 0)
            if (!isFunction[i * size + 6]) setFn(6, i, i % 2 == 0)
        }

        // Reserve format info areas & dark module
        for (i in 0..8) {
            if (i != 6) {
                setFn(8, i, false)
                setFn(i, 8, false)
            }
        }
        for (i in 0 until 8) {
            setFn(size - 1 - i, 8, false)
            setFn(8, size - 1 - i, false)
        }
        setFn(8, size - 8, true) // Mandatory dark module

        // Version information (Versions >= 7)
        VERSION_INFO_BITS[spec.version]?.let { vBits ->
            for (i in 0 until 18) {
                val dark = ((vBits ushr i) and 1) != 0
                val a = size - 11 + i % 3
                val b = i / 3
                setFn(a, b, dark)
                setFn(b, a, dark)
            }
        }

        // 5. Place data bits in standard zigzag pattern
        val bitStream = BooleanArray(finalCodewords.size * 8)
        for (i in finalCodewords.indices) {
            val cw = finalCodewords[i]
            for (b in 0 until 8) {
                bitStream[i * 8 + b] = ((cw ushr (7 - b)) and 1) != 0
            }
        }

        var bitIdx = 0
        var upward = true
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right-- // Skip vertical timing column
            for (vert in 0 until size) {
                val y = if (upward) size - 1 - vert else vert
                for (j in 0..1) {
                    val x = right - j
                    if (!isFunction[y * size + x]) {
                        val bit = if (bitIdx < bitStream.size) bitStream[bitIdx++] else false
                        modules[y * size + x] = bit
                    }
                }
            }
            upward = !upward
            right -= 2
        }

        // 6. Pick optimal mask (0..7) minimizing penalty score
        var bestMask = 0
        var bestPenalty = Int.MAX_VALUE
        var bestModules = modules.copyOf()

        for (mask in 0..7) {
            val candidate = modules.copyOf()
            applyMask(candidate, isFunction, size, mask)
            writeFormatInfo(candidate, size, FORMAT_INFO_M[mask])
            val penalty = computePenalty(candidate, size)
            if (penalty < bestPenalty) {
                bestPenalty = penalty
                bestMask = mask
                bestModules = candidate
            }
        }
        // Format info already written on bestModules for bestMask
        @Suppress("UNUSED_VARIABLE")
        val selectedMask = bestMask
        return QrMatrix(size, bestModules)
    }

    private fun applyMask(modules: BooleanArray, isFunction: BooleanArray, size: Int, mask: Int) {
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (isFunction[y * size + x]) continue
                val invert = when (mask) {
                    0 -> (x + y) % 2 == 0
                    1 -> y % 2 == 0
                    2 -> x % 3 == 0
                    3 -> (x + y) % 3 == 0
                    4 -> (x / 3 + y / 2) % 2 == 0
                    5 -> (x * y) % 2 + (x * y) % 3 == 0
                    6 -> ((x * y) % 2 + (x * y) % 3) % 2 == 0
                    7 -> ((x + y) % 2 + (x * y) % 3) % 2 == 0
                    else -> false
                }
                if (invert) {
                    modules[y * size + x] = !modules[y * size + x]
                }
            }
        }
    }

    private fun writeFormatInfo(modules: BooleanArray, size: Int, bits: Int) {
        for (i in 0..5) modules[i * size + 8] = ((bits ushr i) and 1) != 0
        modules[7 * size + 8] = ((bits ushr 6) and 1) != 0
        modules[8 * size + 8] = ((bits ushr 7) and 1) != 0
        modules[8 * size + 7] = ((bits ushr 8) and 1) != 0
        for (i in 9..14) modules[8 * size + (14 - i)] = ((bits ushr i) and 1) != 0

        for (i in 0..7) modules[8 * size + (size - 1 - i)] = ((bits ushr i) and 1) != 0
        for (i in 8..14) modules[(size - 15 + i) * size + 8] = ((bits ushr i) and 1) != 0
    }

    private fun computePenalty(modules: BooleanArray, size: Int): Int {
        var penalty = 0
        // Adjacent modules in row/col
        for (y in 0 until size) {
            var runColor = modules[y * size]
            var runLen = 1
            for (x in 1 until size) {
                val c = modules[y * size + x]
                if (c == runColor) {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty += 1
                } else {
                    runColor = c
                    runLen = 1
                }
            }
        }
        for (x in 0 until size) {
            var runColor = modules[x]
            var runLen = 1
            for (y in 1 until size) {
                val c = modules[y * size + x]
                if (c == runColor) {
                    runLen++
                    if (runLen == 5) penalty += 3 else if (runLen > 5) penalty += 1
                } else {
                    runColor = c
                    runLen = 1
                }
            }
        }
        // 2x2 blocks
        for (y in 0 until size - 1) {
            for (x in 0 until size - 1) {
                val c = modules[y * size + x]
                if (c == modules[y * size + x + 1] &&
                    c == modules[(y + 1) * size + x] &&
                    c == modules[(y + 1) * size + x + 1]
                ) {
                    penalty += 3
                }
            }
        }
        return penalty
    }

    /**
     * Renders a crisp, scannable QR code [Bitmap] with a 4-module quiet zone.
     */
    fun generateBitmap(
        upiUri: String,
        targetSizePx: Int = 512,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE
    ): Bitmap {
        val matrix = encode(upiUri)
        val quietZone = 4
        val totalModules = matrix.size + quietZone * 2
        val scale = maxOf(1, targetSizePx / totalModules)
        val imgSize = totalModules * scale
        val pixels = IntArray(imgSize * imgSize) { lightColor }

        for (my in 0 until matrix.size) {
            for (mx in 0 until matrix.size) {
                if (matrix.get(mx, my)) {
                    val startX = (mx + quietZone) * scale
                    val startY = (my + quietZone) * scale
                    for (dy in 0 until scale) {
                        val rowOffset = (startY + dy) * imgSize + startX
                        for (dx in 0 until scale) {
                            pixels[rowOffset + dx] = darkColor
                        }
                    }
                }
            }
        }
        return Bitmap.createBitmap(pixels, imgSize, imgSize, Bitmap.Config.ARGB_8888)
    }

    /**
     * Validates a UPI payment URI strictly via [DonationConfig.validateUpiPaymentUri]
     * before generating a standards-compliant QR bitmap so malformed, double-encoded,
     * or mismatched-amount QR payloads are never rendered.
     */
    fun generateValidatedUpiBitmap(
        upiUri: String,
        expectedAmountInr: Double,
        targetSizePx: Int = 512,
        darkColor: Int = Color.BLACK,
        lightColor: Int = Color.WHITE
    ): Bitmap? {
        val checked = DonationConfig.validateUpiPaymentUri(upiUri, expectedAmountInr)
        if (checked !is UpiUriValidationResult.Valid) return null
        return runCatching {
            generateBitmap(
                upiUri = checked.uri.toString(),
                targetSizePx = targetSizePx,
                darkColor = darkColor,
                lightColor = lightColor
            )
        }.getOrNull()
    }

    /**
     * Saves the generated QR bitmap into app cache and returns a FileProvider content URI
     * suitable for Intent.ACTION_SEND sharing.
     */
    fun saveShareableQrPng(context: Context, upiUri: String, orderRef: String): Uri? {
        return try {
            val bitmap = generateBitmap(upiUri = upiUri, targetSizePx = 720)
            val qrDir = File(context.cacheDir, "donation_qr").apply { mkdirs() }
            val safeRef = orderRef.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val outFile = File(qrDir, "ctecs_coffee_$safeRef.png")
            outFile.outputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        } catch (_: Exception) {
            null
        }
    }
}
