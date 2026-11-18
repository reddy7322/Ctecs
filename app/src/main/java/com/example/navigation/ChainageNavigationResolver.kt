package com.example.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.example.geo.ChainageMatcher
import com.example.geo.GeoUtils
import com.example.geo.SmartChainageCorrectionEngine
import com.example.location.LocationTracker
import com.example.model.CalibrationValidationStatus
import com.example.model.Chainage
import com.example.model.ChainageCorrectionPoint
import com.example.model.ChainageMatchResult
import com.example.model.GeoPoint
import com.example.model.MatchConfidence
import com.example.model.RoadSide
import com.example.model.RouteAlignment
import com.example.model.RouteData
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Travel direction from current chainage to destination chainage.
 */
enum class ChainageTravelDirection(val label: String, val shortArrow: String) {
    INCREASING("Increasing Chainage", "↑"),
    DECREASING("Decreasing Chainage", "↓"),
    AT_DESTINATION("At Destination Chainage", "•")
}

/**
 * Status of resolving a destination chainage on the active KMZ/KML alignment.
 */
enum class DestinationResolutionStatus {
    RESOLVED_EXACT_MARKER,
    RESOLVED_INTERPOLATED,
    UNAVAILABLE_NO_ROUTE,
    UNAVAILABLE_OUT_OF_RANGE,
    UNAVAILABLE_AMBIGUOUS,
    UNAVAILABLE_NO_GEOMETRY,
    INVALID_INPUT
}

/**
 * Supported external navigation apps for CTECS Navigate to Chainage.
 */
enum class ExternalNavApp(
    val id: String,
    val displayName: String,
    val packageName: String
) {
    GOOGLE_MAPS(
        id = "google_maps",
        displayName = "Google Maps",
        packageName = "com.google.android.apps.maps"
    ),
    MAPPLS(
        id = "mappls",
        displayName = "Mappls",
        packageName = "com.mmi.maps"
    );

    companion object {
        fun fromId(id: String?): ExternalNavApp? =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) || it.name.equals(id, ignoreCase = true) }
    }
}

/**
 * Complete result of validating a destination chainage, calculating distance from current
 * live calibrated chainage, and resolving its exact geographic coordinates on the active alignment.
 */
data class ChainageDestinationCalculation(
    val rawInput: String,
    val isValidChainageInput: Boolean,
    val inputValidationError: String? = null,
    val destinationChainage: Chainage? = null,
    val formattedDestinationChainage: String = "--+---",
    val currentChainage: Chainage? = null,
    val formattedCurrentChainage: String = "--+---",
    val isCurrentChainageLive: Boolean = false,
    val currentSideLabel: String = "RHS",
    // Chainage difference (nominal |dest - current|)
    val chainageDifferenceMetres: Long? = null,
    val formattedChainageDifference: String = "--",
    val formattedDifferenceMetresDetail: String = "",
    // Alignment distance along active KMZ/KML when both current and destination lie on the alignment
    val alignmentTravelDistanceMetres: Long? = null,
    val hasChainageEquationOnSpan: Boolean = false,
    val chainageEquationNote: String? = null,
    val travelDirection: ChainageTravelDirection? = null,
    val carriagewayCompatibilityNote: String? = null,
    // Destination location resolution on active KMZ/KML alignment
    val resolutionStatus: DestinationResolutionStatus = DestinationResolutionStatus.INVALID_INPUT,
    val isLocationResolved: Boolean = false,
    val destinationPoint: GeoPoint? = null,
    val destinationAlignmentDistanceMetres: Double? = null,
    val currentAlignmentDistanceMetres: Double? = null,
    val alignmentName: String = "",
    val locationDetailsText: String = "",
    val nearestMarkerInfo: String = "",
    val unavailableReason: String? = null
) {
    val canStartNavigation: Boolean
        get() = isValidChainageInput &&
            destinationChainage != null &&
            isLocationResolved &&
            destinationPoint != null &&
            LocationTracker.isValidCoordinate(destinationPoint.latitude, destinationPoint.longitude)
}

/**
 * Active in-app chainage navigation target retained when the user taps "Start" inside CTECS.
 */
data class ActiveChainageNavigationTarget(
    val destinationChainage: Chainage,
    val destinationPoint: GeoPoint,
    val alignmentName: String,
    val routeId: String,
    val travelDirection: ChainageTravelDirection,
    val initialDistanceMetres: Long?,
    val startedAtMs: Long = System.currentTimeMillis()
)

/**
 * Result of launching external navigation or directions in Google Maps / Mappls.
 */
data class ExternalNavLaunchResult(
    val launchedExternalApp: Boolean,
    val appUsed: ExternalNavApp?,
    val usedSystemChooser: Boolean = false,
    val requiresAppSelectionChoice: Boolean = false,
    val message: String
)

/**
 * Pure, deterministic resolver for CTECS Navigate to Chainage.
 * Reuses existing [Chainage], [ChainageMatcher], and [SmartChainageCorrectionEngine] without
 * creating duplicate GPS listeners or chainage engines.
 */
object ChainageNavigationResolver {

    // Strict input pattern: supports "196+000", "196+494", "201+500", "196/400", or plain non-negative metres/km
    private val STRICT_PLUS_INPUT_REGEX = Regex(
        """^\s*(?:CH|CHAINAGE|KM|STA|STATION)?[:\s\.-]*(\d{1,5})\s*\+\s*(\d{1,3}(?:\.\d{1,2})?)\s*(?:m)?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val STRICT_SLASH_INPUT_REGEX = Regex(
        """^\s*(?:CH|CHAINAGE|KM)?[:\s\.-]*(\d{1,5})\s*/\s*(\d{1,3})\s*(?:m)?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val STRICT_DIGITS_ONLY_REGEX = Regex("""^\s*(\d{1,8})\s*$""")
    private val STRICT_DECIMAL_KM_REGEX = Regex("""^\s*(\d{1,5})\.(\d{1,3})\s*(?:km)?\s*$""", RegexOption.IGNORE_CASE)

    /**
     * Formats user input into standard `KM+MMM` notation when committed/calculated,
     * or returns the original string if invalid.
     */
    fun autoFormatChainageInput(rawInput: String): String {
        val parsed = parseAndValidateDestinationChainage(rawInput).first ?: return rawInput.trim()
        return parsed.format()
    }

    /**
     * Strictly validates user-entered destination chainage.
     * Accepts formats like:
     * - `"196+000"`, `"196+494"`, `"201+500"`, `"0+050"`
     * - `"196/400"`
     * - `"196494"` (6+ digits interpreted as metres `196+494`, or 1-3 digits `196` interpreted as `196+000`)
     * - `"196.494"`
     * Rejects negative values, malformed symbols, or sub-metre parts >= 1000 in `KM+MMM`.
     */
    fun parseAndValidateDestinationChainage(rawInput: String?): Pair<Chainage?, String?> {
        val trimmed = rawInput?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null to "Enter a destination chainage (for example: 196+000 or 201+500)."
        }

        if (trimmed.startsWith("-")) {
            return null to "Negative chainage values are not supported."
        }

        // Check explicit "KM+M" format first and enforce 0..999 metres after '+'
        if (trimmed.contains("+")) {
            val match = STRICT_PLUS_INPUT_REGEX.matchEntire(trimmed)
                ?: return null to "Invalid chainage format. Use KM+MMM (for example: 196+000, 196+494, or 201+500)."
            val km = match.groupValues[1].toLongOrNull()
                ?: return null to "Invalid kilometre value in chainage."
            val mRawStr = match.groupValues[2]
            val mDouble = mRawStr.toDoubleOrNull()
                ?: return null to "Invalid metre offset in chainage."
            if (mDouble < 0.0 || mDouble >= 1000.0) {
                return null to "Metres after '+' must be between 000 and 999 (for example: $km+500)."
            }
            val integerPart = mRawStr.substringBefore('.')
            // If user typed 1 or 2 digits after '+' like "196+5" or "196+50", require 3 digits or unambiguous metres
            val metresPart = when (integerPart.length) {
                3 -> Math.round(mDouble)
                1, 2 -> Math.round(mDouble)
                else -> return null to "Use 3 digits after '+' (for example: ${km}+000)."
            }
            val totalMetres = km * 1000L + metresPart
            return Chainage(totalMetres) to null
        }

        // Check slash format "196/400"
        if (trimmed.contains("/")) {
            val match = STRICT_SLASH_INPUT_REGEX.matchEntire(trimmed)
                ?: return null to "Invalid highway marker format. Use KM+MMM (for example: 196+400)."
            val parsed = Chainage.parse(trimmed)
            return if (parsed != null && parsed.metres >= 0L) {
                parsed to null
            } else {
                null to "Invalid chainage value."
            }
        }

        // Check decimal km format "196.494"
        val decMatch = STRICT_DECIMAL_KM_REGEX.matchEntire(trimmed)
        if (decMatch != null) {
            val km = decMatch.groupValues[1].toLongOrNull() ?: return null to "Invalid chainage value."
            val fracStr = decMatch.groupValues[2].padEnd(3, '0').take(3)
            val m = fracStr.toLongOrNull() ?: return null to "Invalid chainage value."
            return Chainage(km * 1000L + m) to null
        }

        // Check pure digits
        val digitsMatch = STRICT_DIGITS_ONLY_REGEX.matchEntire(trimmed)
        if (digitsMatch != null) {
            val digits = digitsMatch.groupValues[1]
            val value = digits.toLongOrNull() ?: return null to "Invalid numeric chainage."
            val totalMetres = if (digits.length <= 3 && !digits.startsWith("0")) {
                // e.g. "196" -> 196+000
                value * 1000L
            } else {
                // e.g. "196494" -> 196+494
                value
            }
            if (totalMetres > 9_999_999L) {
                return null to "Chainage value is out of supported highway range."
            }
            return Chainage(totalMetres) to null
        }

        return null to "Invalid chainage format. Enter a valid chainage such as 196+000, 196+494, or 201+500."
    }

    /**
     * Formats a chainage difference in metres or kilometres according to CTECS requirements:
     * - `< 1000 m`: `"494 m"`
     * - `>= 1000 m`: `"4.506 km"` (always preserves metre precision to 3 decimals when non-zero, or `"4.000 km"` / `"4.500 km"`).
     */
    fun formatChainageDifference(diffMetres: Long): String {
        val absM = abs(diffMetres)
        return if (absM < 1000L) {
            "$absM m"
        } else {
            String.format(Locale.US, "%.3f km", absM / 1000.0)
        }
    }

    /**
     * Resolves a destination chainage against the current live calibrated chainage and active KMZ/KML alignment.
     *
     * Strictly guarantees:
     * - Uses actual calibrated chainage (`matchResult.chainage`) and active correction points.
     * - Never calculates straight-line GPS distance and presents it as chainage distance.
     * - Detects chainage equations/discontinuities, out-of-range chainages, and ambiguous duplicate segments.
     * - Never invents destination coordinates when the destination cannot be reliably located on the alignment.
     */
    fun calculateAndResolveDestination(
        rawDestinationInput: String,
        matchResult: ChainageMatchResult?,
        activeRoute: RouteData?,
        correctionPoints: List<ChainageCorrectionPoint> = emptyList(),
        isCorrectionsEnabled: Boolean = true,
        projectRoadSide: RoadSide = RoadSide.RHS,
        detectedSideOverride: String? = null
    ): ChainageDestinationCalculation {
        val currentSide = resolveEffectiveSideLabel(matchResult, projectRoadSide, detectedSideOverride)
        val hasLiveCurrentChainage = matchResult?.chainage != null &&
            matchResult.confidence != MatchConfidence.NO_MATCH
        val currentCh = if (hasLiveCurrentChainage) matchResult?.chainage else null
        val currentFormatted = currentCh?.format() ?: "--+---"

        val (destCh, validationError) = parseAndValidateDestinationChainage(rawDestinationInput)
        if (destCh == null) {
            return ChainageDestinationCalculation(
                rawInput = rawDestinationInput,
                isValidChainageInput = false,
                inputValidationError = validationError,
                currentChainage = currentCh,
                formattedCurrentChainage = currentFormatted,
                isCurrentChainageLive = hasLiveCurrentChainage,
                currentSideLabel = currentSide,
                resolutionStatus = DestinationResolutionStatus.INVALID_INPUT
            )
        }

        val destMetres = destCh.metres
        val destFormatted = destCh.format()

        // 1. Calculate Chainage Difference & Travel Direction from current calibrated chainage
        val diffMetres: Long? = currentCh?.let { abs(destMetres - it.metres) }
        val formattedDiff = diffMetres?.let { formatChainageDifference(it) } ?: "Waiting for live chainage"
        val formattedDiffDetail = diffMetres?.let {
            if (it >= 1000L) "${String.format(Locale.US, "%,d", it)} m" else "${String.format(Locale.US, "%.3f", it / 1000.0)} km"
        }.orEmpty()

        val travelDirection: ChainageTravelDirection? = currentCh?.let { cur ->
            when {
                destMetres > cur.metres -> ChainageTravelDirection.INCREASING
                destMetres < cur.metres -> ChainageTravelDirection.DECREASING
                else -> ChainageTravelDirection.AT_DESTINATION
            }
        }

        val carriagewayNote: String? = if (travelDirection != null) {
            when {
                travelDirection == ChainageTravelDirection.AT_DESTINATION ->
                    "You are currently at destination chainage $destFormatted ($currentSide)."
                currentSide == "LHS" && travelDirection == ChainageTravelDirection.INCREASING ->
                    "Forward along LHS (Increasing Chainage)"
                currentSide == "RHS" && travelDirection == ChainageTravelDirection.DECREASING ->
                    "Forward along RHS (Decreasing Chainage)"
                currentSide == "LHS" && travelDirection == ChainageTravelDirection.DECREASING ->
                    "Decreasing chainage (opposite to standard LHS increasing direction)"
                currentSide == "RHS" && travelDirection == ChainageTravelDirection.INCREASING ->
                    "Increasing chainage (opposite to standard RHS decreasing direction)"
                else -> "${travelDirection.label} ($currentSide)"
            }
        } else {
            "Current GPS chainage not locked yet — showing alignment location for $destFormatted"
        }

        // 2. Check active KMZ/KML alignment availability
        if (activeRoute == null) {
            return ChainageDestinationCalculation(
                rawInput = rawDestinationInput,
                isValidChainageInput = true,
                destinationChainage = destCh,
                formattedDestinationChainage = destFormatted,
                currentChainage = currentCh,
                formattedCurrentChainage = currentFormatted,
                isCurrentChainageLive = hasLiveCurrentChainage,
                currentSideLabel = currentSide,
                chainageDifferenceMetres = diffMetres,
                formattedChainageDifference = formattedDiff,
                formattedDifferenceMetresDetail = formattedDiffDetail,
                travelDirection = travelDirection,
                carriagewayCompatibilityNote = carriagewayNote,
                resolutionStatus = DestinationResolutionStatus.UNAVAILABLE_NO_ROUTE,
                isLocationResolved = false,
                unavailableReason = "No KMZ/KML alignment is currently active. Import or select a route to locate $destFormatted on the map."
            )
        }

        if (activeRoute.markers.isEmpty() && activeRoute.alignments.isEmpty()) {
            return ChainageDestinationCalculation(
                rawInput = rawDestinationInput,
                isValidChainageInput = true,
                destinationChainage = destCh,
                formattedDestinationChainage = destFormatted,
                currentChainage = currentCh,
                formattedCurrentChainage = currentFormatted,
                isCurrentChainageLive = hasLiveCurrentChainage,
                currentSideLabel = currentSide,
                chainageDifferenceMetres = diffMetres,
                formattedChainageDifference = formattedDiff,
                formattedDifferenceMetresDetail = formattedDiffDetail,
                travelDirection = travelDirection,
                carriagewayCompatibilityNote = carriagewayNote,
                resolutionStatus = DestinationResolutionStatus.UNAVAILABLE_NO_GEOMETRY,
                isLocationResolved = false,
                alignmentName = activeRoute.name,
                unavailableReason = "The active route (${activeRoute.name}) has no valid alignment geometry or chainage markers."
            )
        }

        val activeCorrections = if (isCorrectionsEnabled) {
            correctionPoints.filter {
                it.isEnabled &&
                    it.verificationStatus != CalibrationValidationStatus.INVALID &&
                    (it.routeId.isBlank() || activeRoute.id.isBlank() || it.routeId == activeRoute.id)
            }
        } else {
            emptyList()
        }

        // Select best alignment matching user's carriageway side (LHS / RHS) or active alignment
        val targetAlignment = selectAlignmentForCarriageway(
            routeData = activeRoute,
            matchedAlignmentName = matchResult?.matchedAlignmentName,
            sideLabel = currentSide
        )
        val cachedGeometry = if (targetAlignment != null && targetAlignment.points.size >= 2) {
            ChainageMatcher.getOrCreateCachedGeometry(targetAlignment, activeRoute.markers)
        } else null

        // 3. Determine calibrated chainage bounds of the active route
        val (boundA, boundB) = SmartChainageCorrectionEngine.computeCalibratedRouteRange(
            routeData = activeRoute,
            correctionPoints = activeCorrections
        )
        val markerMin = activeRoute.markers.minOfOrNull { it.chainage.metres }
        val markerMax = activeRoute.markers.maxOfOrNull { it.chainage.metres }
        val singleOffset = if (activeCorrections.size == 1) activeCorrections.first().offsetMetres else 0L
        val effectiveMinCh = minOf(
            boundA,
            boundB,
            (markerMin ?: boundA) + singleOffset
        )
        val effectiveMaxCh = maxOf(
            boundA,
            boundB,
            (markerMax ?: boundB) + singleOffset
        )

        // Allow up to 50m boundary tolerance at start/end of alignment; reject out-of-coverage chainages
        val boundaryToleranceM = 50L
        if (effectiveMaxCh > effectiveMinCh &&
            (destMetres < effectiveMinCh - boundaryToleranceM || destMetres > effectiveMaxCh + boundaryToleranceM)
        ) {
            val minFormatted = Chainage(effectiveMinCh.coerceAtLeast(0L)).format()
            val maxFormatted = Chainage(effectiveMaxCh.coerceAtLeast(0L)).format()
            return ChainageDestinationCalculation(
                rawInput = rawDestinationInput,
                isValidChainageInput = true,
                destinationChainage = destCh,
                formattedDestinationChainage = destFormatted,
                currentChainage = currentCh,
                formattedCurrentChainage = currentFormatted,
                isCurrentChainageLive = hasLiveCurrentChainage,
                currentSideLabel = currentSide,
                chainageDifferenceMetres = diffMetres,
                formattedChainageDifference = formattedDiff,
                formattedDifferenceMetresDetail = formattedDiffDetail,
                travelDirection = travelDirection,
                carriagewayCompatibilityNote = carriagewayNote,
                resolutionStatus = DestinationResolutionStatus.UNAVAILABLE_OUT_OF_RANGE,
                isLocationResolved = false,
                alignmentName = targetAlignment?.name ?: activeRoute.name,
                unavailableReason = "Chainage $destFormatted is outside the active alignment coverage ($minFormatted to $maxFormatted)."
            )
        }

        // 4. Check for ambiguous duplicate chainages on the active alignment (e.g. overlapping chainage equations)
        val ambiguityExplanation = detectAmbiguousChainageAtTarget(
            targetCalibratedMetres = destMetres,
            routeData = activeRoute,
            cached = cachedGeometry,
            activeCorrections = activeCorrections
        )
        if (ambiguityExplanation != null) {
            return ChainageDestinationCalculation(
                rawInput = rawDestinationInput,
                isValidChainageInput = true,
                destinationChainage = destCh,
                formattedDestinationChainage = destFormatted,
                currentChainage = currentCh,
                formattedCurrentChainage = currentFormatted,
                isCurrentChainageLive = hasLiveCurrentChainage,
                currentSideLabel = currentSide,
                chainageDifferenceMetres = diffMetres,
                formattedChainageDifference = formattedDiff,
                formattedDifferenceMetresDetail = formattedDiffDetail,
                travelDirection = travelDirection,
                carriagewayCompatibilityNote = carriagewayNote,
                resolutionStatus = DestinationResolutionStatus.UNAVAILABLE_AMBIGUOUS,
                isLocationResolved = false,
                alignmentName = targetAlignment?.name ?: activeRoute.name,
                unavailableReason = ambiguityExplanation
            )
        }

        // 5. Resolve exact alignment distance and geographic coordinate for destination chainage
        val (destDistAlongM, origKmzMetres) = SmartChainageCorrectionEngine.resolveAlignmentDistanceForCalibratedChainage(
            targetCalibratedMetres = destMetres,
            routeData = activeRoute,
            correctionPoints = activeCorrections
        )

        val exactMarker = activeRoute.markers.firstOrNull { m ->
            abs((m.chainage.metres + singleOffset) - destMetres) <= 1L &&
                activeCorrections.size <= 1
        }

        val resolvedPoint: GeoPoint? = when {
            cachedGeometry != null && cachedGeometry.points.size >= 2 -> {
                ChainageMatcher.interpolatePointAtDistanceAlongAlignment(cachedGeometry, destDistAlongM)
            }
            exactMarker != null -> exactMarker.point
            activeRoute.markers.isNotEmpty() -> {
                SmartChainageCorrectionEngine.resolveRoutePositionForChainage(activeRoute, origKmzMetres).point
            }
            else -> null
        }

        if (resolvedPoint == null || !LocationTracker.isValidCoordinate(resolvedPoint.latitude, resolvedPoint.longitude)) {
            return ChainageDestinationCalculation(
                rawInput = rawDestinationInput,
                isValidChainageInput = true,
                destinationChainage = destCh,
                formattedDestinationChainage = destFormatted,
                currentChainage = currentCh,
                formattedCurrentChainage = currentFormatted,
                isCurrentChainageLive = hasLiveCurrentChainage,
                currentSideLabel = currentSide,
                chainageDifferenceMetres = diffMetres,
                formattedChainageDifference = formattedDiff,
                formattedDifferenceMetresDetail = formattedDiffDetail,
                travelDirection = travelDirection,
                carriagewayCompatibilityNote = carriagewayNote,
                resolutionStatus = DestinationResolutionStatus.UNAVAILABLE_NO_GEOMETRY,
                isLocationResolved = false,
                alignmentName = targetAlignment?.name ?: activeRoute.name,
                unavailableReason = "Unable to resolve valid geographic coordinates for $destFormatted on the active alignment."
            )
        }

        // 6. Check for chainage equations / discontinuities between current chainage and destination chainage
        val currentDistAlongM: Double? = when {
            matchResult != null && matchResult.distanceAlongAlignmentMetres > 0.01 ->
                matchResult.distanceAlongAlignmentMetres
            currentCh != null ->
                SmartChainageCorrectionEngine.resolveAlignmentDistanceForCalibratedChainage(
                    targetCalibratedMetres = currentCh.metres,
                    routeData = activeRoute,
                    correctionPoints = activeCorrections
                ).first
            else -> null
        }

        val alignmentTravelMetres: Long? = if (currentDistAlongM != null && cachedGeometry != null) {
            abs(destDistAlongM - currentDistAlongM).roundToLong()
        } else null

        val equationDiscrepancyMetres = if (diffMetres != null && alignmentTravelMetres != null) {
            abs(alignmentTravelMetres - diffMetres)
        } else 0L

        val hasEquationOnSpan = equationDiscrepancyMetres >= 25L &&
            diffMetres != null &&
            diffMetres > 0L &&
            (equationDiscrepancyMetres.toDouble() / diffMetres.coerceAtLeast(1L).toDouble()) > 0.05

        val equationNote = if (hasEquationOnSpan && alignmentTravelMetres != null) {
            "Chainage equation along route: alignment travel distance is ${formatChainageDifference(alignmentTravelMetres)} (nominal Δ $formattedDiff)."
        } else null

        val nearestMarkerDescription = buildNearestMarkerDescription(
            activeRoute = activeRoute,
            origKmzMetres = origKmzMetres,
            destFormatted = destFormatted,
            alignmentName = targetAlignment?.name ?: activeRoute.name
        )

        val coordFormatted = String.format(
            Locale.US,
            "%.6f, %.6f",
            resolvedPoint.latitude,
            resolvedPoint.longitude
        )

        return ChainageDestinationCalculation(
            rawInput = rawDestinationInput,
            isValidChainageInput = true,
            destinationChainage = destCh,
            formattedDestinationChainage = destFormatted,
            currentChainage = currentCh,
            formattedCurrentChainage = currentFormatted,
            isCurrentChainageLive = hasLiveCurrentChainage,
            currentSideLabel = currentSide,
            chainageDifferenceMetres = diffMetres,
            formattedChainageDifference = formattedDiff,
            formattedDifferenceMetresDetail = formattedDiffDetail,
            alignmentTravelDistanceMetres = alignmentTravelMetres,
            hasChainageEquationOnSpan = hasEquationOnSpan,
            chainageEquationNote = equationNote,
            travelDirection = travelDirection,
            carriagewayCompatibilityNote = carriagewayNote,
            resolutionStatus = if (exactMarker != null) {
                DestinationResolutionStatus.RESOLVED_EXACT_MARKER
            } else {
                DestinationResolutionStatus.RESOLVED_INTERPOLATED
            },
            isLocationResolved = true,
            destinationPoint = resolvedPoint,
            destinationAlignmentDistanceMetres = destDistAlongM,
            currentAlignmentDistanceMetres = currentDistAlongM,
            alignmentName = targetAlignment?.name ?: activeRoute.name,
            locationDetailsText = "${targetAlignment?.name ?: activeRoute.name} ($coordFormatted)",
            nearestMarkerInfo = nearestMarkerDescription,
            unavailableReason = null
        )
    }

    private fun resolveEffectiveSideLabel(
        matchResult: ChainageMatchResult?,
        projectRoadSide: RoadSide,
        detectedSideOverride: String?
    ): String {
        val raw = detectedSideOverride?.trim()?.uppercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() && it != "---" }
            ?: matchResult?.detectedSide?.trim()?.uppercase(Locale.ROOT)
        return when {
            raw == "LHS" -> "LHS"
            raw == "RHS" -> "RHS"
            raw == "MEDIAN" -> "MEDIAN"
            raw == "BOTH" || raw == "BHS" -> "BHS"
            projectRoadSide == RoadSide.LHS -> "LHS"
            projectRoadSide == RoadSide.RHS -> "RHS"
            projectRoadSide == RoadSide.MEDIAN -> "MEDIAN"
            projectRoadSide == RoadSide.BOTH -> "BHS"
            else -> "RHS"
        }
    }

    private fun selectAlignmentForCarriageway(
        routeData: RouteData,
        matchedAlignmentName: String?,
        sideLabel: String
    ): RouteAlignment? {
        if (routeData.alignments.isEmpty()) return null
        // 1. Prefer explicitly selected alignment if set
        if (routeData.selectedAlignmentId != null) {
            routeData.alignments.firstOrNull { it.id == routeData.selectedAlignmentId }?.let { return it }
        }
        // 2. Prefer alignment matching current carriageway side if multiple carriageways exist
        if (routeData.alignments.size > 1 && (sideLabel == "LHS" || sideLabel == "RHS")) {
            routeData.alignments.firstOrNull {
                it.carriagewayType.equals(sideLabel, ignoreCase = true) ||
                    it.name.contains(sideLabel, ignoreCase = true)
            }?.let { return it }
        }
        // 3. Prefer currently matched alignment
        if (!matchedAlignmentName.isNullOrBlank()) {
            routeData.alignments.firstOrNull { it.name == matchedAlignmentName }?.let { return it }
        }
        return routeData.activeAlignment ?: routeData.alignments.firstOrNull()
    }

    /**
     * Checks whether the target chainage maps to multiple disjoint locations (> 120m apart)
     * on the same alignment due to an unresolved chainage inversion/overlap.
     */
    private fun detectAmbiguousChainageAtTarget(
        targetCalibratedMetres: Long,
        routeData: RouteData,
        cached: ChainageMatcher.CachedAlignmentGeometry?,
        activeCorrections: List<ChainageCorrectionPoint>
    ): String? {
        if (cached == null || cached.mappedMarkers.size < 3) return null
        val singleOffset = if (activeCorrections.size == 1) activeCorrections.first().offsetMetres else 0L
        val targetRawMetres = targetCalibratedMetres - singleOffset

        val matchingSpanPositions = mutableListOf<Double>()
        val mapped = cached.mappedMarkers
        for (i in 0 until mapped.size - 1) {
            val mA = mapped[i]
            val mB = mapped[i + 1]
            val minCh = minOf(mA.chainageMetres, mB.chainageMetres)
            val maxCh = maxOf(mA.chainageMetres, mB.chainageMetres)
            if (targetRawMetres in minCh..maxCh) {
                val spanCh = (mB.chainageMetres - mA.chainageMetres).toDouble()
                val frac = if (abs(spanCh) > 0.01) {
                    ((targetRawMetres - mA.chainageMetres).toDouble() / spanCh).coerceIn(0.0, 1.0)
                } else 0.0
                val posM = mA.distanceAlongAlignmentM + frac * (mB.distanceAlongAlignmentM - mA.distanceAlongAlignmentM)
                if (matchingSpanPositions.none { abs(it - posM) <= 120.0 }) {
                    matchingSpanPositions.add(posM)
                }
            }
        }
        if (matchingSpanPositions.size >= 2) {
            val chStr = Chainage(targetCalibratedMetres).format()
            return "Chainage $chStr is ambiguous on ${cached.alignmentName}: found at ${matchingSpanPositions.size} separate alignment sections due to a chainage equation/overlap."
        }
        return null
    }

    private fun buildNearestMarkerDescription(
        activeRoute: RouteData,
        origKmzMetres: Long,
        destFormatted: String,
        alignmentName: String
    ): String {
        val markers = activeRoute.markers
        if (markers.isEmpty()) return "Interpolated on $alignmentName"
        val exact = markers.firstOrNull { abs(it.chainage.metres - origKmzMetres) <= 1L }
        if (exact != null) {
            return "Exact KMZ Marker: ${exact.label.ifBlank { destFormatted }} on $alignmentName"
        }
        val sorted = markers.sortedBy { it.chainage.metres }
        val before = sorted.lastOrNull { it.chainage.metres <= origKmzMetres }
        val after = sorted.firstOrNull { it.chainage.metres >= origKmzMetres }
        return when {
            before != null && after != null && before.id != after.id ->
                "Interpolated between ${before.chainage.format()} and ${after.chainage.format()} on $alignmentName"
            before != null ->
                "Referenced from marker ${before.chainage.format()} on $alignmentName"
            after != null ->
                "Referenced from marker ${after.chainage.format()} on $alignmentName"
            else ->
                "Resolved along $alignmentName"
        }
    }
}

/**
 * Handles Google Maps and Mappls package detection, intent creation, and safe launching.
 * Never passes chainage text as geographic coordinates.
 */
object ExternalNavigationLauncher {

    fun isAppInstalled(context: Context, app: ExternalNavApp): Boolean {
        val pm = context.packageManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(app.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(app.packageName, 0)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getInstalledSupportedApps(context: Context): List<ExternalNavApp> {
        return ExternalNavApp.entries.filter { isAppInstalled(context, it) }
    }

    /**
     * Builds a turn-by-turn Directions intent (with origin and destination coordinates) for the given app.
     * Coordinates are strictly formatted as `latitude,longitude` in `Locale.US`.
     */
    fun buildDirectionsIntent(
        app: ExternalNavApp,
        originPoint: GeoPoint?,
        destinationPoint: GeoPoint,
        destinationChainageLabel: String
    ): Intent {
        val destLatLng = String.format(
            Locale.US,
            "%.6f,%.6f",
            destinationPoint.latitude,
            destinationPoint.longitude
        )
        val hasValidOrigin = originPoint != null &&
            LocationTracker.isValidCoordinate(originPoint.latitude, originPoint.longitude)
        val originParam = if (hasValidOrigin && originPoint != null) {
            "&origin=" + String.format(Locale.US, "%.6f,%.6f", originPoint.latitude, originPoint.longitude)
        } else {
            ""
        }

        return when (app) {
            ExternalNavApp.GOOGLE_MAPS -> {
                val uri = Uri.parse(
                    "https://www.google.com/maps/dir/?api=1$originParam&destination=$destLatLng&travelmode=driving"
                )
                Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(ExternalNavApp.GOOGLE_MAPS.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            ExternalNavApp.MAPPLS -> {
                // Mappls supports standard geo: and https://maps.mappls.com/ intents targeted to com.mmi.maps
                val encodedLabel = Uri.encode("KM $destinationChainageLabel")
                val uri = Uri.parse("geo:$destLatLng?q=$destLatLng($encodedLabel)")
                Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage(ExternalNavApp.MAPPLS.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        }
    }

    /**
     * Builds an active Navigation start intent for the given app using resolved destination coordinates.
     */
    fun buildStartNavigationIntent(
        app: ExternalNavApp,
        originPoint: GeoPoint?,
        destinationPoint: GeoPoint,
        destinationChainageLabel: String
    ): Intent {
        val destLatLng = String.format(
            Locale.US,
            "%.6f,%.6f",
            destinationPoint.latitude,
            destinationPoint.longitude
        )
        return when (app) {
            ExternalNavApp.GOOGLE_MAPS -> {
                val navUri = Uri.parse("google.navigation:q=$destLatLng&mode=d")
                Intent(Intent.ACTION_VIEW, navUri).apply {
                    setPackage(ExternalNavApp.GOOGLE_MAPS.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            ExternalNavApp.MAPPLS -> {
                buildDirectionsIntent(
                    app = ExternalNavApp.MAPPLS,
                    originPoint = originPoint,
                    destinationPoint = destinationPoint,
                    destinationChainageLabel = destinationChainageLabel
                )
            }
        }
    }

    /**
     * Builds a universal fallback geo/maps intent when a specific package is not installed.
     */
    fun buildUniversalFallbackDirectionsIntent(
        originPoint: GeoPoint?,
        destinationPoint: GeoPoint
    ): Intent {
        val destLatLng = String.format(
            Locale.US,
            "%.6f,%.6f",
            destinationPoint.latitude,
            destinationPoint.longitude
        )
        val hasValidOrigin = originPoint != null &&
            LocationTracker.isValidCoordinate(originPoint.latitude, originPoint.longitude)
        val originParam = if (hasValidOrigin && originPoint != null) {
            "&origin=" + String.format(Locale.US, "%.6f,%.6f", originPoint.latitude, originPoint.longitude)
        } else {
            ""
        }
        val uri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1$originParam&destination=$destLatLng&travelmode=driving"
        )
        return Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Launches turn-by-turn directions or navigation in the selected external app,
     * falling back gracefully if the preferred app is missing.
     */
    fun launchExternalNavigation(
        context: Context,
        preferredApp: ExternalNavApp?,
        isStartMode: Boolean,
        originPoint: GeoPoint?,
        destinationPoint: GeoPoint,
        destinationChainageLabel: String
    ): ExternalNavLaunchResult {
        if (!LocationTracker.isValidCoordinate(destinationPoint.latitude, destinationPoint.longitude)) {
            return ExternalNavLaunchResult(
                launchedExternalApp = false,
                appUsed = null,
                message = "Destination coordinates are invalid."
            )
        }

        val installedApps = getInstalledSupportedApps(context)

        // 1. Try preferred app first if specified and installed
        if (preferredApp != null && installedApps.contains(preferredApp)) {
            val primaryIntent = if (isStartMode) {
                buildStartNavigationIntent(preferredApp, originPoint, destinationPoint, destinationChainageLabel)
            } else {
                buildDirectionsIntent(preferredApp, originPoint, destinationPoint, destinationChainageLabel)
            }
            if (tryStartActivity(context, primaryIntent)) {
                return ExternalNavLaunchResult(
                    launchedExternalApp = true,
                    appUsed = preferredApp,
                    message = if (isStartMode) {
                        "Started navigation to KM $destinationChainageLabel in ${preferredApp.displayName}"
                    } else {
                        "Opened directions to KM $destinationChainageLabel in ${preferredApp.displayName}"
                    }
                )
            }
            // If google.navigation: scheme failed on a stripped Google Maps build, try directions URI on same package
            val secondaryIntent = buildDirectionsIntent(preferredApp, originPoint, destinationPoint, destinationChainageLabel)
            if (tryStartActivity(context, secondaryIntent)) {
                return ExternalNavLaunchResult(
                    launchedExternalApp = true,
                    appUsed = preferredApp,
                    message = "Opened route to KM $destinationChainageLabel in ${preferredApp.displayName}"
                )
            }
        }

        // 2. If preferredApp wasn't installed, but another supported app (Google Maps or Mappls) IS installed, try it
        val alternateApp = installedApps.firstOrNull { it != preferredApp }
        if (alternateApp != null) {
            val altIntent = if (isStartMode) {
                buildStartNavigationIntent(alternateApp, originPoint, destinationPoint, destinationChainageLabel)
            } else {
                buildDirectionsIntent(alternateApp, originPoint, destinationPoint, destinationChainageLabel)
            }
            if (tryStartActivity(context, altIntent) ||
                tryStartActivity(context, buildDirectionsIntent(alternateApp, originPoint, destinationPoint, destinationChainageLabel))
            ) {
                val notePrefix = if (preferredApp != null) {
                    "${preferredApp.displayName} is not installed — opened in ${alternateApp.displayName}"
                } else {
                    "Opened KM $destinationChainageLabel in ${alternateApp.displayName}"
                }
                return ExternalNavLaunchResult(
                    launchedExternalApp = true,
                    appUsed = alternateApp,
                    message = notePrefix
                )
            }
        }

        // 3. Even if getPackageInfo didn't report installed (or user has a browser/maps handler), try direct package intent then universal https/geo intent
        if (preferredApp != null) {
            val directAttempt = if (isStartMode) {
                buildStartNavigationIntent(preferredApp, originPoint, destinationPoint, destinationChainageLabel)
            } else {
                buildDirectionsIntent(preferredApp, originPoint, destinationPoint, destinationChainageLabel)
            }
            if (tryStartActivity(context, directAttempt)) {
                return ExternalNavLaunchResult(
                    launchedExternalApp = true,
                    appUsed = preferredApp,
                    message = "Opened KM $destinationChainageLabel in ${preferredApp.displayName}"
                )
            }
        }

        // 4. Universal fallback directions intent (opens default maps/browser chooser if available)
        val universalIntent = buildUniversalFallbackDirectionsIntent(originPoint, destinationPoint)
        if (tryStartActivity(context, universalIntent)) {
            return ExternalNavLaunchResult(
                launchedExternalApp = true,
                appUsed = null,
                usedSystemChooser = true,
                message = "Opened directions to KM $destinationChainageLabel"
            )
        }

        return ExternalNavLaunchResult(
            launchedExternalApp = false,
            appUsed = null,
            message = "Neither Google Maps nor Mappls is available on this device."
        )
    }

    private fun tryStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }
}
