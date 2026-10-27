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
         * Accepts formats like: "196+040", "130+000", "209+883", "CH: 196+040", "CH. 196+040", "196+40".
         */
        fun parse(raw: String?): Chainage? {
            if (raw.isNullOrBlank()) return null
            val match = CHAINAGE_REGEX.find(raw.trim()) ?: return null
            val (kmStr, mStr) = match.destructured

            val km = kmStr.toLongOrNull() ?: return null
            val m = mStr.toLongOrNull() ?: return null

            if (m < 0 || m >= 1000 && mStr.length != 4) {
                // Reject invalid metre components unless valid 3-digit or standard interval
                if (m >= 10000) return null
            }

            val totalMetres = if (km < 0) {
                km * 1000 - m
            } else {
                km * 1000 + m
            }
            return Chainage(totalMetres)
        }

        fun fromMetres(metres: Long): Chainage = Chainage(metres)
    }
}
