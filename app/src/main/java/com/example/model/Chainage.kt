package com.example.model

import java.util.Locale

/**
 * Represents a highway chainage (distance along road alignment).
 * Example: "196+040" = 196 km + 40 metres = 196,040 metres.
 */
data class Chainage(val metres: Long) : Comparable<Chainage> {

    val kilometres: Long get() = metres / 1000
    val remainderMetres: Long get() = Math.abs(metres % 1000)

    /**
     * Formats the chainage to the standard format "196+040".
     */
    fun format(): String {
        return if (metres < 0) {
            String.format(Locale.US, "-%d+%03d", Math.abs(kilometres), remainderMetres)
        } else {
            String.format(Locale.US, "%d+%03d", kilometres, remainderMetres)
        }
    }

    override fun toString(): String = format()

    override fun compareTo(other: Chainage): Int = metres.compareTo(other.metres)

    companion object {
        private val CHAINAGE_REGEX = Regex(
            """(?i)(?:CH|CHAINAGE|KM)?[:\s\.]*(-?\d+)\s*[\+]+\s*(\d{1,4})(?:\s*m)?(?:\b|$)"""
        )

        /**
         * Parses a raw string into a [Chainage] object.
         * Accepts formats like:
         * - "198+880", "200+000", "CH: 196+040", "CH. 196+040", "196+40", "-5+200"
         * - "198880", "200000" (raw metre integer)
         * - "198.880", "200.0" (kilometres)
         */
        fun parse(raw: String?): Chainage? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.trim()

            // 1. Standard "km+m" pattern e.g. "198+880" or "CH: 198+880"
            val match = CHAINAGE_REGEX.find(cleaned)
            if (match != null) {
                val (kmStr, mStr) = match.destructured
                val km = kmStr.toLongOrNull() ?: return null
                val m = mStr.toLongOrNull() ?: return null

                val totalMetres = if (km < 0) {
                    km * 1000 - m
                } else {
                    km * 1000 + m
                }
                return Chainage(totalMetres)
            }

            // 2. Pure digits or signed integer e.g. "198880", "200000", "198"
            val prefixStripped = cleaned
                .replace(Regex("""^(?i)(?:CH|CHAINAGE|KM)[\s:\.]*"""), "")
                .trim()

            val asLong = prefixStripped.toLongOrNull()
            if (asLong != null) {
                val totalMetres = if (Math.abs(asLong) < 1000 && !prefixStripped.startsWith("0")) {
                    asLong * 1000
                } else {
                    asLong
                }
                return Chainage(totalMetres)
            }

            // 3. Decimal kilometre representation e.g. "198.880", "200.5"
            val asDouble = prefixStripped.toDoubleOrNull()
            if (asDouble != null && !asDouble.isNaN() && !asDouble.isInfinite()) {
                val totalMetres = Math.round(asDouble * 1000.0)
                return Chainage(totalMetres)
            }

            return null
        }

        fun fromMetres(metres: Long): Chainage = Chainage(metres)
    }
}
