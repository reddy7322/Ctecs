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
    val points: List<GeoPoint>
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
    val importedAt: Long = System.currentTimeMillis()
) {
    val totalCoverageKm: Double
        get() {
            if (startChainage == null || endChainage == null) return 0.0
            return kotlin.math.abs(endChainage.metres - startChainage.metres) / 1000.0
        }
}
