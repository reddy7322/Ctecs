package com.example.model

enum class RefreshMode(val label: String) {
    SMART("Smart Refresh"),
    FORCE_ALL("Force Refresh All")
}

enum class ChainageAccuracyStatus(val label: String) {
    VERIFIED("Accurate / Verified"),
    UPDATED("Updated"),
    NEEDS_GPS("Needs GPS"),
    ALIGNMENT_MISMATCH("Alignment Mismatch"),
    CALIBRATION_NEEDS_REVIEW("Calibration Needs Review")
}

data class ChainageAccuracyReport(
    val status: ChainageAccuracyStatus = ChainageAccuracyStatus.NEEDS_GPS,
    val mode: RefreshMode = RefreshMode.SMART,
    val isRefreshing: Boolean = false,
    val isStaleFallback: Boolean = false,
    val summaryMessage: String = "",
    val lastRefreshedEpochMs: Long = 0L,
    val isGpsValid: Boolean = false,
    val isGpsFresh: Boolean = false,
    val gpsFixAgeMs: Long = -1L,
    val matchConfidence: MatchConfidence = MatchConfidence.NO_MATCH,
    val offsetMetres: Double = 0.0,
    val isCalibrationProfileValid: Boolean = true,
    val isControlPointConsistent: Boolean = true,
    val isChainageProgressionValid: Boolean = false,
    val isSideConsistent: Boolean = false,
    val isCustomCorrectionValid: Boolean = true,
    val isManualOverrideActive: Boolean = false,
    val stepNotes: List<String> = emptyList()
)
