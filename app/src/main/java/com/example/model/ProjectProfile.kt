package com.example.model

enum class RoadSide(val label: String) {
    RHS("RHS"),
    LHS("LHS"),
    MEDIAN("MEDIAN"),
    BOTH("BOTH");

    companion object {
        fun fromString(s: String?): RoadSide =
            values().firstOrNull { it.label.equals(s, ignoreCase = true) } ?: RHS
    }
}

/**
 * Road construction project parameters and field tagging.
 */
data class ProjectProfile(
    val highwayId: String = "MH",              // e.g. "MH" or "NH-58"
    val packageSection: String = "MM",         // e.g. "MM" or "Pkg-02"
    val roadSide: RoadSide = RoadSide.RHS,      // RHS / LHS / MEDIAN
    val lane: String = "I/L",                  // I/L (Inner), M/L (Middle), O/L (Outer), Shoulder
    val activity: String = "DBM",              // DBM, BC, GSB, WMM, Milling, Inspection, etc.
    val customDescription: String = "",        // Optional notes
    val inspectorName: String = "",            // Optional site supervisor / engineer
    val separator: String = "||"               // Default "||" separator
) {
    /**
     * Builds the structured highway text line:
     * e.g. "MH | 168+400 RHS Milling" or "MH | 168+400 RHS" or "MH | MM | 196+040 RHS I/L DBM"
     */
    fun buildHighwayText(chainageFormatted: String): String {
        fun isValid(s: String?): Boolean {
            if (s == null) return false
            val t = s.trim()
            if (t.isEmpty()) return false
            if (t.equals("None", ignoreCase = true)) return false
            if (t.equals("Null", ignoreCase = true)) return false
            if (t.equals("N/A", ignoreCase = true)) return false
            return true
        }

        val parts = mutableListOf<String>()

        if (isValid(highwayId)) parts.add(highwayId.trim())
        if (isValid(packageSection)) parts.add(packageSection.trim())

        // Chainage with road side, lane, activity, and custom description
        val workParts = mutableListOf<String>()
        if (isValid(chainageFormatted)) workParts.add(chainageFormatted.trim())
        if (isValid(roadSide.label)) workParts.add(roadSide.label.trim())
        if (isValid(lane)) workParts.add(lane.trim())
        if (isValid(activity)) workParts.add(activity.trim())
        if (isValid(customDescription)) workParts.add(customDescription.trim())

        val workStr = workParts.joinToString(" ")
        if (workStr.isNotBlank()) {
            parts.add(workStr)
        }

        val effectiveSep = if (separator.trim() == "||") "|" else separator.trim()
        val sep = " $effectiveSep "
        return parts.joinToString(sep)
    }

    companion object {
        val LANE_PRESETS = listOf("None", "I/L", "M/L", "O/L", "Shoulder", "Service Rd", "Main CW")
        val ACTIVITY_PRESETS = listOf(
            "None",
            "DBM",
            "BC",
            "GSB",
            "WMM",
            "Milling",
            "Embankment",
            "Subgrade",
            "DLC",
            "PQC",
            "Drainage",
            "Kerb",
            "Road Marking",
            "Safety Barrier",
            "Site Inspection",
            "General Progress",
            "Main CW to MCW"
        )
    }
}
