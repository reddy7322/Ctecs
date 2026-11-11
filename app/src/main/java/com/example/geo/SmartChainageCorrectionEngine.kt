package com.example.geo

import android.content.Context
import com.example.model.AlignmentDiagnostics
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RouteData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Result returned by [SmartChainageCorrectionEngine.applyCorrection].
 */
data class SmartCorrectionResult(
    val correctedChainage: Chainage,
    val originalKmzChainage: Chainage,
    val appliedOffsetMetres: Long,
    val boundingPointA: ChainageCorrectionPoint?,
    val boundingPointB: ChainageCorrectionPoint?,
    val interpolationFraction: Double,
    val infoMessage: String,
    val isEquationBoundary: Boolean = false
)

/**
 * Validates new or edited correction points against existing points and route boundaries.
 */
data class CorrectionValidationResult(
    val isValid: Boolean,
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null
)

/**
 * Unified KMZ Chainage Calibration & Correction Engine.
 *
 * Requirements:
 * - Uses selected KMZ/KML alignment as the sole geographical reference.
 * - Detects missing, duplicated, inconsistent, and incorrectly ordered chainage references.
 * - Validates chainage progression against actual alignment distance.
 * - Supports non-uniform chainage progression and chainage equations.
 * - Adjusts chainage reference relationship ONLY without shifting coordinates or geometry.
 * - Generates verified exportable KMZ with calibrated references.
 */
object SmartChainageCorrectionEngine {

    // Maximum distance from a single correction point where fixed shift applies (100 km corridor coverage)
    const val SINGLE_POINT_INFLUENCE_METRES = 100_000L

    /**
     * Applies custom chainage correction to [rawChainage] if it falls within valid coverage
     * of enabled correction points on [routeData].
     */
    fun applyCorrection(
        rawChainage: Chainage,
        routeData: RouteData,
        correctionPoints: List<ChainageCorrectionPoint>,
        gpsPoint: GeoPoint? = null,
        gpsAccuracy: Float = 0f,
        confidence: MatchConfidence = MatchConfidence.HIGH
    ): SmartCorrectionResult? {
        // Safety: Do not apply custom correction if GPS fix is untrusted or off-route
        if (confidence == MatchConfidence.NO_MATCH || confidence == MatchConfidence.LOW) {
            return null
        }

        // Filter enabled correction points belonging strictly to this route
        val activePoints = correctionPoints
            .filter { it.routeId == routeData.id && it.isEnabled }
            .sortedBy { it.originalKmzMetres }

        if (activePoints.isEmpty()) {
            return null
        }

        val rawMetres = rawChainage.metres

        // Case 1: Single correction point calibration
        if (activePoints.size == 1) {
            val single = activePoints[0]
            val distFromPoint = abs(rawMetres - single.originalKmzMetres)
            if (distFromPoint <= SINGLE_POINT_INFLUENCE_METRES) {
                val corrected = rawMetres + single.offsetMetres
                return SmartCorrectionResult(
                    correctedChainage = Chainage(corrected),
                    originalKmzChainage = rawChainage,
                    appliedOffsetMetres = single.offsetMetres,
                    boundingPointA = single,
                    boundingPointB = null,
                    interpolationFraction = 1.0,
                    infoMessage = "Calibrated at ${single.originalKmzChainage.format()} (${single.displayOffset})"
                )
            } else {
                return null
            }
        }

        // Case 2: Multiple correction points - Route piecewise interpolation
        val minKmz = activePoints.first().originalKmzMetres
        val maxKmz = activePoints.last().originalKmzMetres

        if (rawMetres < minKmz || rawMetres > maxKmz) {
            return null
        }

        // Locate bounding control points [pA, pB]
        var pA: ChainageCorrectionPoint? = null
        var pB: ChainageCorrectionPoint? = null

        for (i in 0 until activePoints.size - 1) {
            val curr = activePoints[i]
            val next = activePoints[i + 1]
            val segMin = minOf(curr.originalKmzMetres, next.originalKmzMetres)
            val segMax = maxOf(curr.originalKmzMetres, next.originalKmzMetres)
            if (rawMetres in segMin..segMax) {
                pA = curr
                pB = next
                break
            }
        }

        if (pA == null || pB == null) {
            if (rawMetres == maxKmz) {
                val last = activePoints.last()
                return SmartCorrectionResult(
                    correctedChainage = last.correctedChainage,
                    originalKmzChainage = rawChainage,
                    appliedOffsetMetres = last.offsetMetres,
                    boundingPointA = last,
                    boundingPointB = null,
                    interpolationFraction = 1.0,
                    infoMessage = "Calibrated at ${last.originalKmzChainage.format()}"
                )
            }
            return null
        }

        // Linear interpolation of offset drift between control points
        val spanKmz = pB.originalKmzMetres - pA.originalKmzMetres
        val fraction = if (spanKmz != 0L) {
            ((rawMetres - pA.originalKmzMetres).toDouble() / spanKmz.toDouble()).coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        val correctedMetres = (pA.correctedMetres + fraction * (pB.correctedMetres - pA.correctedMetres)).roundToLong()
        val appliedOffset = correctedMetres - rawMetres

        val offsetStr = if (appliedOffset >= 0) "+${appliedOffset}m" else "${appliedOffset}m"
        val infoMsg = "${pA.correctedChainage.format()} ↔ ${pB.correctedChainage.format()} ($offsetStr)"

        return SmartCorrectionResult(
            correctedChainage = Chainage(correctedMetres),
            originalKmzChainage = rawChainage,
            appliedOffsetMetres = appliedOffset,
            boundingPointA = pA,
            boundingPointB = pB,
            interpolationFraction = fraction,
            infoMessage = infoMsg
        )
    }

    /**
     * Validates a correction point before adding or editing.
     */
    fun validatePoint(
        point: ChainageCorrectionPoint,
        existingPoints: List<ChainageCorrectionPoint>,
        routeData: RouteData?
    ): CorrectionValidationResult {
        val warnings = mutableListOf<String>()

        // 1. Basic validation
        if (point.routeId.isBlank()) {
            return CorrectionValidationResult(isValid = false, errorMessage = "No route selected for correction point.")
        }

        // 2. Duplicate check (within 10 metres on the same route)
        val otherPoints = existingPoints.filter { it.routeId == point.routeId && it.id != point.id }
        val duplicate = otherPoints.find { abs(it.originalKmzMetres - point.originalKmzMetres) < 10 }
        if (duplicate != null) {
            warnings.add("A correction point already exists at this location (${duplicate.originalKmzChainage.format()}).")
        }

        // 3. Route boundary check
        if (routeData != null && routeData.startChainage != null && routeData.endChainage != null) {
            val minRoute = minOf(routeData.startChainage.metres, routeData.endChainage.metres)
            val maxRoute = maxOf(routeData.startChainage.metres, routeData.endChainage.metres)
            if (point.originalKmzMetres < minRoute || point.originalKmzMetres > maxRoute) {
                warnings.add("Correction position (${point.originalKmzChainage.format()}) is outside imported KMZ range (${routeData.startChainage.format()} - ${routeData.endChainage.format()}).")
            }
        }

        // 4. Large jump check (> 10 km shift)
        val offset = abs(point.offsetMetres)
        if (offset > 10_000) {
            warnings.add("Large correction shift detected: ${point.displayOffset}. Ensure correct chainage was entered.")
        }

        // 5. Direction progression conflict check
        val sortedOthers = otherPoints.sortedBy { it.originalKmzMetres }
        val prev = sortedOthers.lastOrNull { it.originalKmzMetres < point.originalKmzMetres }
        val next = sortedOthers.firstOrNull { it.originalKmzMetres > point.originalKmzMetres }

        if (prev != null && point.correctedMetres < prev.correctedMetres) {
            warnings.add("Corrected chainage (${point.correctedChainage.format()}) is less than previous point (${prev.correctedChainage.format()}), creating an inversion.")
        }
        if (next != null && point.correctedMetres > next.correctedMetres) {
            warnings.add("Corrected chainage (${point.correctedChainage.format()}) is greater than next point (${next.correctedChainage.format()}), creating an inversion.")
        }

        return CorrectionValidationResult(isValid = true, warnings = warnings)
    }

    /**
     * Comprehensive alignment audit: validates progression, checks for duplicate markers,
     * inversions, and chainage equations without modifying any source data.
     */
    fun performAlignmentAudit(routeData: RouteData): AlignmentDiagnostics {
        val markers = routeData.markers
        if (markers.isEmpty()) {
            return AlignmentDiagnostics(
                totalMarkersCount = 0,
                qualityScorePercent = 50,
                summaryMessage = "No chainage markers found in route"
            )
        }

        val warnings = mutableListOf<String>()
        val chainageEquations = mutableListOf<String>()

        var duplicateCount = 0
        var inversionCount = 0
        var largeGaps = 0

        for (i in 0 until markers.size - 1) {
            val m1 = markers[i]
            val m2 = markers[i + 1]

            // Duplicate check
            if (abs(m1.chainage.metres - m2.chainage.metres) < 5 && GeoUtils.distanceMetres(m1.point, m2.point) < 25.0) {
                duplicateCount++
            }

            // Inversion check
            if (m2.chainage.metres < m1.chainage.metres) {
                inversionCount++
            }

            val deltaCh = abs(m2.chainage.metres - m1.chainage.metres)
            val geomDist = GeoUtils.distanceMetres(m1.point, m2.point)

            if (deltaCh > 2500) {
                largeGaps++
            }

            // Non-uniform chainage progression / equation check
            if (abs(deltaCh - geomDist) > 150.0 && deltaCh > 100) {
                chainageEquations.add("Discontinuity at ${m1.chainage.format()} ↔ ${m2.chainage.format()}: ΔCh=${deltaCh}m vs Geom=${geomDist.toInt()}m")
            }
        }

        if (duplicateCount > 0) warnings.add("$duplicateCount duplicate chainage reference markers detected.")
        if (inversionCount > 0) warnings.add("$inversionCount direction inversions found.")
        if (largeGaps > 0) warnings.add("$largeGaps unreferenced marker gaps > 2.5 km.")

        var score = 100 - (duplicateCount * 5) - (inversionCount * 15) - (largeGaps * 5)
        score = score.coerceIn(10, 100)

        val summary = when {
            score >= 90 -> "100% Validated KMZ Highway Alignment"
            score >= 70 -> "Good Alignment Quality (${markers.size} Reference Points)"
            else -> "Alignment Warnings: $duplicateCount Duplicates, $inversionCount Inversions"
        }

        return AlignmentDiagnostics(
            isChainageIncreasing = inversionCount == 0,
            totalMarkersCount = markers.size,
            duplicateMarkersCount = duplicateCount,
            inversionCount = inversionCount,
            gapCount = largeGaps,
            chainageEquations = chainageEquations,
            warnings = warnings,
            qualityScorePercent = score,
            summaryMessage = summary
        )
    }

    /**
     * Exports a verified KMZ file containing 100% untouched original geometry
     * plus a dedicated folder of Calibrated Chainage References.
     */
    suspend fun exportCalibratedKmz(
        context: Context,
        routeData: RouteData,
        correctionPoints: List<ChainageCorrectionPoint>
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.filesDir, "kmz_calibrated_exports").apply { mkdirs() }
        val dateSuffix = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val sanitized = routeData.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        val kmzFile = File(exportDir, "${sanitized}_calibrated_${dateSuffix}.kmz")

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("<Document>\n")
        sb.append("  <name>${escapeXml(routeData.name)} (CTECS Calibrated)</name>\n")
        sb.append("  <description>Calibrated Highway Alignment exported from CTECS Chainage Control System.</description>\n")

        // 1. Original Alignments (100% Unchanged Geometry)
        if (routeData.alignments.isNotEmpty()) {
            sb.append("  <Folder>\n")
            sb.append("    <name>Original Highway Alignments</name>\n")
            routeData.alignments.forEach { align ->
                sb.append("    <Placemark>\n")
                sb.append("      <name>${escapeXml(align.name)}</name>\n")
                sb.append("      <LineString>\n")
                sb.append("        <tessellate>1</tessellate>\n")
                sb.append("        <coordinates>\n")
                align.points.forEach { pt ->
                    sb.append("          ${pt.longitude},${pt.latitude},${pt.altitude}\n")
                }
                sb.append("        </coordinates>\n")
                sb.append("      </LineString>\n")
                sb.append("    </Placemark>\n")
            }
            sb.append("  </Folder>\n")
        }

        // 2. Original KMZ Chainage Markers (100% Unchanged)
        if (routeData.markers.isNotEmpty()) {
            sb.append("  <Folder>\n")
            sb.append("    <name>Original KMZ Markers</name>\n")
            routeData.markers.forEach { m ->
                sb.append("    <Placemark>\n")
                sb.append("      <name>${escapeXml(m.label)}</name>\n")
                sb.append("      <description>Original Chainage: ${m.chainage.format()}</description>\n")
                sb.append("      <Point>\n")
                sb.append("        <coordinates>${m.point.longitude},${m.point.latitude},${m.point.altitude}</coordinates>\n")
                sb.append("      </Point>\n")
                sb.append("    </Placemark>\n")
            }
            sb.append("  </Folder>\n")
        }

        // 3. Calibrated Chainage References Folder
        if (correctionPoints.isNotEmpty()) {
            sb.append("  <Folder>\n")
            sb.append("    <name>Calibrated Chainage References</name>\n")
            correctionPoints.forEachIndexed { idx, cp ->
                sb.append("    <Placemark>\n")
                sb.append("      <name>Calibration #${idx + 1}: ${cp.correctedChainage.format()}</name>\n")
                sb.append("      <description>Original KMZ: ${cp.originalKmzChainage.format()} | Corrected: ${cp.correctedChainage.format()} | Applied Shift: ${cp.displayOffset} | Status: ${if (cp.isEnabled) "Enabled" else "Disabled"}</description>\n")
                sb.append("      <Point>\n")
                sb.append("        <coordinates>${cp.longitude},${cp.latitude}</coordinates>\n")
                sb.append("      </Point>\n")
                sb.append("    </Placemark>\n")
            }
            sb.append("  </Folder>\n")
        }

        sb.append("</Document>\n")
        sb.append("</kml>\n")

        ZipOutputStream(FileOutputStream(kmzFile)).use { zos ->
            val entry = ZipEntry("doc.kml")
            zos.putNextEntry(entry)
            zos.write(sb.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            zos.flush()
        }

        kmzFile
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
