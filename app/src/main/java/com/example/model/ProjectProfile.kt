package com.example.model

enum class RoadSide(val label: String) {
    RHS("RHS"),
    LHS("LHS"),
    MEDIAN("MEDIAN"),
    BOTH("BOTH"),
    NONE("None");

    companion object {
        fun fromString(s: String?): RoadSide {
            if (s == null) return RHS
            val trimmed = s.trim()
            if (trimmed.isEmpty()) return RHS
            if (trimmed.equals("BHS", ignoreCase = true)) return BOTH
            if (trimmed.equals("None", ignoreCase = true)) return NONE
            return values().firstOrNull {
                it.label.equals(trimmed, ignoreCase = true) || it.name.equals(trimmed, ignoreCase = true)
            } ?: RHS
        }
    }
}

/**
 * Road construction project parameters and field tagging.
 */
data class ProjectProfile(
    val highwayId: String = "",              // e.g. "NH-44" (Project Name)
    val packageSection: String = "",         // e.g. "Pkg-02" (Stretch)
    val startChainage: String = "0+000",      // Project Start Chainage e.g. "0+000"
    val endChainage: String = "50+000",        // Project End Chainage e.g. "50+000"
    val workType: String = "",               // Work Type: MM, RM, LIE, or "None"/""
    val roadSide: RoadSide = RoadSide.RHS,      // RHS / LHS / MEDIAN / BOTH / NONE
    val lane: String = "I/L",                  // I/L (Inner), M/L (Middle), O/L (Outer), Shoulder, None
    val activity: String = "DBM",              // Activity / Layer: DBM, BC, GSB, None, or custom
    val subActivity: String = "",              // Sub-Activity / Layer detail: Layer 1, Tack Coat, None, or custom
    val customDescription: String = "",        // Custom Comment (shown on next line)
    val inspectorName: String = "",            // Optional site supervisor / engineer
    val separator: String = "||"               // Default "||" separator
) {
    /**
     * Builds the structured highway text line:
     * Line 1: Project Name | Stretch | Work Type | {Chainage} { RHS } { I/L} {Activity} {SubActivity}
     * Line 2: { Custom Comment } (handled via getCustomCommentLine())
     */
    fun buildHighwayText(chainageFormatted: String, includeProjectName: Boolean = true): String {
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

        if (includeProjectName && isValid(highwayId)) parts.add(highwayId.trim())
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

        // Road segment telemetry: {Chainage} { RHS } { I/L} {Activity} {SubActivity}
        val roadDetails = mutableListOf<String>()
        if (isValid(chainageFormatted)) roadDetails.add(chainageFormatted.trim())
        if (isValid(roadSide.label)) roadDetails.add(roadSide.label.trim())
        if (isValid(lane)) roadDetails.add(lane.trim())

        // Activity / Layer: e.g. DBM (ignore if it matches workType or is None)
        val effectiveActivity = if (!activity.equals("MM", true) && !activity.equals("RM", true) && !activity.equals("LIE", true)) activity else ""
        if (isValid(effectiveActivity)) {
            if (isValid(subActivity)) {
                roadDetails.add("${effectiveActivity.trim()} (${subActivity.trim()})")
            } else {
                roadDetails.add(effectiveActivity.trim())
            }
        } else if (isValid(subActivity)) {
            roadDetails.add(subActivity.trim())
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

    fun hasActiveActivity(): Boolean {
        val t = activity.trim()
        return t.isNotEmpty() && !t.equals("None", ignoreCase = true) && !t.equals("null", ignoreCase = true)
    }

    fun hasActiveSubActivity(): Boolean {
        val t = subActivity.trim()
        return t.isNotEmpty() && !t.equals("None", ignoreCase = true) && !t.equals("null", ignoreCase = true)
    }

    fun hasActiveLane(): Boolean {
        val t = lane.trim()
        return t.isNotEmpty() && !t.equals("None", ignoreCase = true) && !t.equals("null", ignoreCase = true)
    }

    companion object {
        data class WorkType(val code: String, val title: String)
        val WORK_TYPE_PRESETS = listOf(
            WorkType("None", "None"),
            WorkType("MM", "Major Maintenance"),
            WorkType("RM", "Routine Maintenance"),
            WorkType("LIE", "Lenders Investment Engineer")
        )

        val LANE_PRESETS = listOf("None", "I/L", "M/L", "O/L", "Shoulder", "Service Rd", "MCW")
        val ACTIVITY_PRESETS = listOf(
            "None",
            "EMB",
            "SG",
            "GSB",
            "WMM",
            "DBM",
            "BC",
            "Milling",
            "DLC",
            "PQC",
            "RE Wall",
            "Drain",
            "Kerb",
            "Studs",
            "Lighting",
            "Junction",
            "Minor Junction",
            "Major Junction",
            "Road Marking",
            "Sign Boards",
            "MBCB",
            "U-Duct",
            "Busbay",
            "Bus Shelter",
            "Safety",
            "Site Inspection",
            "General Progress"
        )
        val SUB_ACTIVITY_PRESETS = listOf(
            "None",
            "Layer 1",
            "Layer 2",
            "Prime Coat",
            "Tack Coat",
            "Trial Stretch",
            "Core Cutting",
            "FDD / Compaction",
            "Level Checking",
            "Rolling",
            "Laying",
            "Excavation",
            "Reinforcement",
            "Shuttering",
            "Concreting",
            "Curing",
            "Joint Cutting",
            "Rectification"
        )
    }
}

/**
 * Defined project item for user selection and persistence in Settings.
 */
data class DefinedProject(
    val name: String,
    val stretch: String = "",
    val workType: String = "MM",
    val startChainage: String = "0+000",
    val endChainage: String = "50+000"
) {
    companion object {
        val DEFAULT_PROJECTS: List<DefinedProject> = emptyList()
    }
}

