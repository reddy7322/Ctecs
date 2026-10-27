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
     * Builds the structured 3rd line:
     * e.g. "MH || MM || 196+040 RHS I/L DBM"
     */
    fun buildHighwayText(chainageFormatted: String): String {
        val parts = mutableListOf<String>()
        if (highwayId.isNotBlank()) parts.add(highwayId.trim())
        if (packageSection.isNotBlank()) parts.add(packageSection.trim())

        // Chainage with road side, lane, and activity
        val workParts = mutableListOf<String>()
        if (chainageFormatted.isNotBlank()) workParts.add(chainageFormatted)
        workParts.add(roadSide.label)
        if (lane.isNotBlank()) workParts.add(lane.trim())
        if (activity.isNotBlank()) workParts.add(activity.trim())
        if (customDescription.isNotBlank()) workParts.add(customDescription.trim())

        val workStr = workParts.joinToString(" ")
        if (workStr.isNotBlank()) parts.add(workStr)

        val sep = " $separator "
        return parts.joinToString(sep)
    }

    companion object {
        val LANE_PRESETS = listOf("I/L", "M/L", "O/L", "Shoulder", "Service Rd", "Main CW")
        val ACTIVITY_PRESETS = listOf(
            "DBM", "BC", "GSB", "WMM", "Milling", "Embankment",
            "Subgrade", "DLC", "PQC", "Drainage", "Kerb",
            "Road Marking", "Safety Barrier", "Site Inspection", "General Progress"
        )
    }
}
