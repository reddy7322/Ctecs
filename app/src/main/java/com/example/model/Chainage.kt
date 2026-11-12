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
        private val CHAINAGE_PLUS_REGEX = Regex(
            """(?i)(?:CH|CHAINAGE|KM|STA|STATION)?[:\s\.-]*(-?\d+)\s*[\+]+\s*(\d{1,4}(?:\.\d+)?)(?:\s*m)?(?:\b|$)"""
        )

        private val CHAINAGE_SLASH_REGEX = Regex(
            """(?i)(?:CH|CHAINAGE|KM)?[:\s\.-]*(-?\d+)\s*[/]+\s*(\d{1,4})(?:\s*m)?(?:\b|$)"""
        )

        private val UNIT_METRES_REGEX = Regex(
            """(?i)^\s*(-?\d+(?:\.\d+)?)\s*(?:m|metres|meters)\s*$"""
        )

        private val UNIT_KM_REGEX = Regex(
            """(?i)^\s*(-?\d+(?:\.\d+)?)\s*(?:km|kms|kilometres|kilometers)\s*$"""
        )

        /**
         * Parses a raw string into a [Chainage] object.
         * Accepts formats like:
         * - "198+880", "200+000", "CH: 196+040", "CH. 196+040", "196+40", "-5+200", "196+040.50"
         * - "196/0", "196/200", "196/4", "KM 196/400" (highway stone markers)
         * - "STA 196+00", "Station 196+00"
         * - "198880", "200000" (raw metre integer)
         * - "198.880", "200.0", "196.4 km", "196400 m"
         */
        fun parse(raw: String?): Chainage? {
            if (raw.isNullOrBlank()) return null
            // Strip any HTML markup from description tags (e.g. <b>196+040</b> or <td>196+040</td>)
            val cleaned = raw.replace(Regex("<[^>]*>"), " ").trim()
            if (cleaned.isBlank()) return null

            // 1. Standard "km+m" pattern e.g. "198+880", "CH: 198+880", "STA 198+00"
            val matchPlus = CHAINAGE_PLUS_REGEX.find(cleaned)
            if (matchPlus != null) {
                val (kmStr, mStr) = matchPlus.destructured
                val km = kmStr.toLongOrNull() ?: return null
                val m = mStr.toDoubleOrNull() ?: return null

                val totalMetres = if (km < 0) {
                    Math.round(km * 1000.0 - m)
                } else {
                    Math.round(km * 1000.0 + m)
                }
                return Chainage(totalMetres)
            }

            // 2. Highway stone slash pattern e.g. "198/0", "198/200", "198/4", "KM 198/400"
            val matchSlash = CHAINAGE_SLASH_REGEX.find(cleaned)
            if (matchSlash != null) {
                val (kmStr, subStr) = matchSlash.destructured
                val km = kmStr.toLongOrNull() ?: return null
                val sub = subStr.toLongOrNull() ?: return null

                // In standard Indian highway 200m stone conventions:
                // 198/0 = 198 km 000m
                // 198/2 = 198 km 200m, 198/4 = 198 km 400m, 198/6 = 198 km 600m, 198/8 = 198 km 800m
                val m = when {
                    subStr.length == 1 && sub in 1..9 -> sub * 100 // or 200m stone convention: 1 -> 100m, 2 -> 200m
                    sub < 1000 -> sub
                    else -> sub % 1000
                }

                val totalMetres = if (km < 0) {
                    km * 1000L - m
                } else {
                    km * 1000L + m
                }
                return Chainage(totalMetres)
            }

            // 3. Explicit unit in metres e.g. "198880 m" or "198880m"
            val matchMetres = UNIT_METRES_REGEX.find(cleaned)
            if (matchMetres != null) {
                val d = matchMetres.groupValues[1].toDoubleOrNull()
                if (d != null) return Chainage(Math.round(d))
            }

            // 4. Explicit unit in kilometres e.g. "198.88 km" or "198km"
            val matchKm = UNIT_KM_REGEX.find(cleaned)
            if (matchKm != null) {
                val d = matchKm.groupValues[1].toDoubleOrNull()
                if (d != null) return Chainage(Math.round(d * 1000.0))
            }

            // 5. Prefix-stripped numbers
            val prefixStripped = cleaned
                .replace(Regex("""^(?i)(?:CH|CHAINAGE|KM|STA|STATION)[\s:\.-]*"""), "")
                .trim()

            // Pure digits or signed integer e.g. "198880", "200000", "198"
            val asLong = prefixStripped.toLongOrNull()
            if (asLong != null) {
                val totalMetres = if (Math.abs(asLong) in 1..999 && !prefixStripped.startsWith("0")) {
                    asLong * 1000L
                } else {
                    asLong
                }
                return Chainage(totalMetres)
            }

            // 6. Decimal kilometre representation e.g. "198.880", "200.5"
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
