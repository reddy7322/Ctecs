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
    val highwayId: String = "",              // e.g. "NH-44" (Project Name)
    val packageSection: String = "",         // e.g. "Pkg-02" (Stretch)
    val workType: String = "",               // Work Type: MM, RM, LIE, or "None"/""
    val roadSide: RoadSide = RoadSide.RHS,      // RHS / LHS / MEDIAN
    val lane: String = "I/L",                  // I/L (Inner), M/L (Middle), O/L (Outer), Shoulder
    val activity: String = "DBM",              // Activity / Layer: DBM, BC, GSB, etc.
    val customDescription: String = "",        // Custom Comment (shown on next line)
    val inspectorName: String = "",            // Optional site supervisor / engineer
    val separator: String = "||"               // Default "||" separator
) {
    /**
     * Builds the structured highway text line:
     * Line 1: Project Name | Stretch | Work Type | {Chainage} { RHS } { I/L} {DBM}
     * Line 2: { Custom Comment } (handled via getCustomCommentLine())
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

        // Work Type: MM, RM, LIE. "None" or blank means empty Work type
        val effectiveWorkType = when {
            isValid(workType) -> workType.trim()
            isValid(activity) && (activity.equals("MM", true) || activity.equals("RM", true) || activity.equals("LIE", true)) -> activity.trim()
            else -> ""
        }
        if (isValid(effectiveWorkType)) {
            parts.add(effectiveWorkType)
        }

        // Road segment telemetry: {Chainage} { RHS } { I/L} {DBM}
        val roadDetails = mutableListOf<String>()
        if (isValid(chainageFormatted)) roadDetails.add(chainageFormatted.trim())
        if (isValid(roadSide.label)) roadDetails.add(roadSide.label.trim())
        if (isValid(lane)) roadDetails.add(lane.trim())

        // Activity / Layer: e.g. DBM (ignore if it matches workType or is None)
        val effectiveActivity = if (!activity.equals("MM", true) && !activity.equals("RM", true) && !activity.equals("LIE", true)) activity else ""
        if (isValid(effectiveActivity)) {
            roadDetails.add(effectiveActivity.trim())
        }

        val roadDetailsStr = roadDetails.joinToString(" ")
        if (roadDetailsStr.isNotBlank()) {
            parts.add(roadDetailsStr)
        }

        val effectiveSep = if (separator.trim() == "||") "|" else separator.trim()
        val sep = " $effectiveSep "
        return parts.joinToString(sep)
    }

    fun getCustomCommentLine(): String? {
        val comment = customDescription.trim()
        if (comment.isBlank()) return null
        if (comment.equals("None", ignoreCase = true)) return null
        if (comment.equals("Null", ignoreCase = true)) return null
        return comment
    }

    companion object {
        data class WorkType(val code: String, val title: String)
        val WORK_TYPE_PRESETS = listOf(
            WorkType("None", "None"),
            WorkType("MM", "Major Maintenance"),
            WorkType("RM", "Routine Maintenance"),
            WorkType("LIE", "Lenders Investment Engineer")
        )

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

/**
 * Defined project item for user selection and persistence in Settings.
 */
data class DefinedProject(
    val name: String,
    val stretch: String = "",
    val workType: String = "MM"
) {
    companion object {
        val DEFAULT_PROJECTS: List<DefinedProject> = emptyList()
    }
}

