package com.example.model

/**
 * Individual chainage marker along the highway route.
 */
data class RouteMarker(
    val id: String,
    val label: String,
    val chainage: Chainage,
    val point: GeoPoint,
    val folderName: String = "",
    val description: String = ""
)

/**
 * Continuous polyline segment representing highway alignment / centerline.
 */
data class RouteAlignment(
    val id: String,
    val name: String,
    val points: List<GeoPoint>,
    val direction: String = "UNKNOWN", // "INCREASING", "DECREASING", "UNKNOWN"
    val carriagewayType: String = "MAIN" // "MAIN", "LHS", "RHS", "SERVICE", "BHS"
) {
    /**
     * Total length of this alignment polyline in metres.
     */
    val totalLengthMetres: Double by lazy {
        if (points.size < 2) return@lazy 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += com.example.geo.GeoUtils.distanceMetres(points[i], points[i + 1])
        }
        total
    }
}

/**
 * Diagnostic analysis report of KMZ alignment references.
 */
data class AlignmentDiagnostics(
    val isChainageIncreasing: Boolean = true,
    val totalMarkersCount: Int = 0,
    val duplicateMarkersCount: Int = 0,
    val inversionCount: Int = 0,
    val gapCount: Int = 0,
    val chainageEquations: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val qualityScorePercent: Int = 100,
    val summaryMessage: String = "Validated KMZ Alignment",
    val rawPlacemarkCount: Int = 0,
    val rawPointCount: Int = 0,
    val rawLineStringCount: Int = 0,
    val multiGeometryCount: Int = 0,
    val selectedKmlEntryName: String = "doc.kml"
)

/**
 * Complete parsed highway route containing markers, alignment geometry, and metadata.
 */
data class RouteData(
    val id: String,
    val name: String,
    val fileName: String,
    val markers: List<RouteMarker>,
    val alignments: List<RouteAlignment>,
    val bounds: GeoBounds,
    val startChainage: Chainage?,
    val endChainage: Chainage?,
    val importedAt: Long = System.currentTimeMillis(),
    val selectedAlignmentId: String? = null,
    val diagnostics: AlignmentDiagnostics? = null
) {
    val totalCoverageKm: Double
        get() {
            if (startChainage == null || endChainage == null) return 0.0
            return kotlin.math.abs(endChainage.metres - startChainage.metres) / 1000.0
        }

    val activeAlignment: RouteAlignment?
        get() {
            if (alignments.isEmpty()) return null
            if (selectedAlignmentId != null) {
                val match = alignments.find { it.id == selectedAlignmentId }
                if (match != null) return match
            }
            return alignments.first()
        }
}
