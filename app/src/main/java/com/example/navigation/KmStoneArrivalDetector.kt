package com.example.navigation

import com.example.location.LocationState
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.KmStoneAlertSettings
import com.example.model.MatchConfidence
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.ui.home.KmStoneReference
import com.example.ui.home.NextKmStoneTracker
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

enum class KmStoneAlertKind {
    ADVANCE_WARNING,
    ARRIVAL
}

data class KmStoneAlertEvent(
    val kind: KmStoneAlertKind,
    val stoneChainageMetres: Long,
    val stoneFormattedChainage: String,
    val currentChainageMetres: Long,
    val currentFormattedChainage: String,
    val directionSide: String,
    val distanceMetres: Long,
    val routeId: String,
    val timestampMs: Long
)

/**
 * Pure, stateful KM Stone arrival and advance-warning detector that reuses CTECS's existing
 * calibrated chainage, LHS/RHS side resolution, and KMZ/KML KM Stone references.
 *
 * Guarantees:
 * - Supports LHS (increasing) and RHS (decreasing) travel directions.
 * - Supports irregular KM Stone intervals from imported KMZ/KML markers and calibrated 1 km stones.
 * - Triggers arrival alert strictly once per KM Stone passage.
 * - Re-arms a stone only after the vehicle moves >= repeatAlertDistanceMetres away or progresses to another stone.
 * - Keeps Advance Warning and Arrival alerts completely separate.
 * - Never triggers on manual override, missing/stale GPS, poor GPS accuracy, or unverified off-route states.
 */
class KmStoneArrivalDetector {

    private var activeRouteId: String? = null
    private var cachedMarkerCount: Int = -1
    private var cachedStones: List<KmStoneReference> = emptyList()

    private var lastValidChainageMetres: Long? = null
    private var lastConfirmedSide: String? = null

    // Tracks stones that have already fired during the current passage until re-armed
    private val disarmedArrivalStones = linkedSetOf<Long>()
    private val disarmedAdvanceStones = linkedSetOf<Long>()

    private var lastArrivalEventMs: Long = 0L
    private var lastAdvanceEventMs: Long = 0L

    fun reset() {
        activeRouteId = null
        cachedMarkerCount = -1
        cachedStones = emptyList()
        lastValidChainageMetres = null
        lastConfirmedSide = null
        disarmedArrivalStones.clear()
        disarmedAdvanceStones.clear()
        lastArrivalEventMs = 0L
        lastAdvanceEventMs = 0L
    }

    fun evaluate(
        locationState: LocationState?,
        matchResult: ChainageMatchResult,
        routeData: RouteData?,
        projectRoadSide: RoadSide,
        settings: KmStoneAlertSettings,
        nowMs: Long = System.currentTimeMillis()
    ): List<KmStoneAlertEvent> {
        if (!settings.enabled) return emptyList()
        if (routeData == null) return emptyList()

        // 1. Validate live GPS fix & accuracy requirement
        if (locationState == null || locationState.point == null || !locationState.isUsable) {
            return emptyList()
        }
        if (locationState.lastFixTime > 0L && (nowMs - locationState.lastFixTime) > MAX_GPS_FIX_AGE_MS) {
            return emptyList()
        }
        val accuracyM = locationState.accuracy
        if (accuracyM != null && !accuracyM.isNaN() && accuracyM > settings.maxGpsAccuracyMetres.toFloat()) {
            return emptyList()
        }

        // 2. Validate calibrated chainage match (never trigger for manual override or unverified match)
        if (matchResult.isManual || matchResult.source == ChainageSource.MANUAL) {
            return emptyList()
        }
        val chainageObj = matchResult.chainage ?: return emptyList()
        if (matchResult.confidence == MatchConfidence.NO_MATCH || matchResult.isUncertain) {
            return emptyList()
        }
        if (matchResult.confidence == MatchConfidence.LOW && matchResult.offsetMetres > 80.0) {
            return emptyList()
        }

        // 3. Handle route change cleanly
        if (activeRouteId != routeData.id || cachedMarkerCount != routeData.markers.size) {
            activeRouteId = routeData.id
            cachedMarkerCount = routeData.markers.size
            cachedStones = extractVerifiedRouteKmStones(routeData)
            lastValidChainageMetres = null
            disarmedArrivalStones.clear()
            disarmedAdvanceStones.clear()
        }

        if (cachedStones.isEmpty()) return emptyList()

        val curM = chainageObj.metres.coerceAtLeast(0L)
        val prevM = lastValidChainageMetres

        // 4. Resolve effective LHS / RHS travel direction
        val explicitSide = when (projectRoadSide) {
            RoadSide.LHS -> "LHS"
            RoadSide.RHS -> "RHS"
            else -> null
        }
        val detectedRaw = matchResult.detectedSide.trim().uppercase(Locale.ROOT)
        val detectedSide = if (detectedRaw == "LHS" || detectedRaw == "RHS") detectedRaw else null
        val trendSide = if (prevM != null && abs(curM - prevM) >= 4L) {
            if (curM > prevM) "LHS" else "RHS"
        } else {
            null
        }
        val effectiveSide = explicitSide ?: trendSide ?: detectedSide ?: lastConfirmedSide ?: "LHS"
        lastConfirmedSide = effectiveSide

        val proximityM = settings.proximityThresholdMetres.coerceIn(5, 100).toLong()
        val rearmDistanceM = if (settings.repeatAlertPreventionEnabled) {
            max(settings.repeatAlertDistanceMetres.toLong(), proximityM + 25L)
        } else {
            proximityM + 12L
        }

        // 5. Re-arm any previously triggered stones once the vehicle has moved sufficiently far away
        val arrivalIter = disarmedArrivalStones.iterator()
        while (arrivalIter.hasNext()) {
            val stoneM = arrivalIter.next()
            if (abs(curM - stoneM) >= rearmDistanceM) {
                arrivalIter.remove()
            }
        }
        val advanceWarnM = settings.resolveAdvanceWarningDistanceMetres().toLong()
        val advanceRearmM = max(advanceWarnM + 35L, rearmDistanceM)
        val advanceIter = disarmedAdvanceStones.iterator()
        while (advanceIter.hasNext()) {
            val stoneM = advanceIter.next()
            val movedPastOrFar = if (effectiveSide == "LHS") {
                curM > stoneM + proximityM || (stoneM - curM) > advanceRearmM
            } else {
                curM < stoneM - proximityM || (curM - stoneM) > advanceRearmM
            }
            if (movedPastOrFar) {
                advanceIter.remove()
            }
        }

        val triggeredEvents = mutableListOf<KmStoneAlertEvent>()

        // 6. Check Actual KM Stone Arrival (within proximity threshold OR clean single-step passage)
        val arrivedStone = findArrivedKmStone(
            curM = curM,
            prevM = prevM,
            effectiveSide = effectiveSide,
            proximityM = proximityM,
            stones = cachedStones
        )

        if (arrivedStone != null && !disarmedArrivalStones.contains(arrivedStone.chainageMetres)) {
            if ((nowMs - lastArrivalEventMs) >= MIN_ALERT_COOLDOWN_MS) {
                disarmedArrivalStones.add(arrivedStone.chainageMetres)
                // Also mark advance warning for this stone as consumed so it cannot fire after arrival
                disarmedAdvanceStones.add(arrivedStone.chainageMetres)
                lastArrivalEventMs = nowMs
                triggeredEvents.add(
                    KmStoneAlertEvent(
                        kind = KmStoneAlertKind.ARRIVAL,
                        stoneChainageMetres = arrivedStone.chainageMetres,
                        stoneFormattedChainage = arrivedStone.formattedChainage,
                        currentChainageMetres = curM,
                        currentFormattedChainage = chainageObj.format(),
                        directionSide = effectiveSide,
                        distanceMetres = abs(curM - arrivedStone.chainageMetres),
                        routeId = routeData.id,
                        timestampMs = nowMs
                    )
                )
            }
        }

        // 7. Check Smart Advance Warning for the upcoming KM Stone in travel direction
        if (advanceWarnM > proximityM && triggeredEvents.isEmpty()) {
            val upcomingStone = findUpcomingKmStone(
                curM = curM,
                effectiveSide = effectiveSide,
                proximityM = proximityM,
                stones = cachedStones
            )
            if (upcomingStone != null && !disarmedAdvanceStones.contains(upcomingStone.chainageMetres)) {
                val distRemaining = abs(upcomingStone.chainageMetres - curM)
                if (distRemaining in (proximityM + 1L)..advanceWarnM) {
                    if ((nowMs - lastAdvanceEventMs) >= MIN_ALERT_COOLDOWN_MS) {
                        disarmedAdvanceStones.add(upcomingStone.chainageMetres)
                        lastAdvanceEventMs = nowMs
                        triggeredEvents.add(
                            KmStoneAlertEvent(
                                kind = KmStoneAlertKind.ADVANCE_WARNING,
                                stoneChainageMetres = upcomingStone.chainageMetres,
                                stoneFormattedChainage = upcomingStone.formattedChainage,
                                currentChainageMetres = curM,
                                currentFormattedChainage = chainageObj.format(),
                                directionSide = effectiveSide,
                                distanceMetres = distRemaining,
                                routeId = routeData.id,
                                timestampMs = nowMs
                            )
                        )
                    }
                }
            }
        }

        lastValidChainageMetres = curM
        return triggeredEvents
    }

    private fun findArrivedKmStone(
        curM: Long,
        prevM: Long?,
        effectiveSide: String,
        proximityM: Long,
        stones: List<KmStoneReference>
    ): KmStoneReference? {
        // First check direct proximity threshold
        val nearestInProximity = stones
            .filter { abs(curM - it.chainageMetres) <= proximityM }
            .minByOrNull { abs(curM - it.chainageMetres) }
        if (nearestInProximity != null) {
            return nearestInProximity
        }

        // Next check if a stone was crossed between prevM and curM during normal forward/reverse travel step (<= 140m)
        if (prevM != null && abs(curM - prevM) in 1L..140L) {
            val crossed = if (curM > prevM) {
                stones.firstOrNull { it.chainageMetres in (prevM + 1L)..curM }
            } else {
                stones.lastOrNull { it.chainageMetres in curM until prevM }
            }
            if (crossed != null) {
                return crossed
            }
        }
        return null
    }

    private fun findUpcomingKmStone(
        curM: Long,
        effectiveSide: String,
        proximityM: Long,
        stones: List<KmStoneReference>
    ): KmStoneReference? {
        return if (effectiveSide == "LHS") {
            stones.firstOrNull { it.chainageMetres > curM + proximityM }
        } else {
            stones.lastOrNull { it.chainageMetres < curM - proximityM }
        }
    }

    companion object {
        const val MAX_GPS_FIX_AGE_MS = 12_000L
        const val MIN_ALERT_COOLDOWN_MS = 900L

        /**
         * Extracts verified KM Stone references from [RouteData].
         * Uses explicit KMZ/KML KM Stone markers when present; otherwise derives verified 1 km
         * chainage boundaries strictly within the route's calibrated [startChainage..endChainage] span.
         */
        fun extractVerifiedRouteKmStones(route: RouteData): List<KmStoneReference> {
            val fromMarkers = NextKmStoneTracker.extractKmStoneReferences(route.markers)
            if (fromMarkers.isNotEmpty()) {
                return fromMarkers
            }
            if (route.alignments.isEmpty()) return emptyList()
            val startM = (route.startChainage?.metres ?: 0L).coerceAtLeast(0L)
            val endM = (route.endChainage?.metres ?: 0L).coerceAtLeast(startM)
            if (endM <= startM) return emptyList()

            val firstKm = if (startM % 1000L == 0L) startM else ((startM / 1000L) + 1L) * 1000L
            val generated = mutableListOf<KmStoneReference>()
            var km = firstKm
            var count = 0
            while (km <= endM && count < 500) {
                generated.add(
                    KmStoneReference(
                        chainageMetres = km,
                        formattedChainage = Chainage.fromMetres(km).format()
                    )
                )
                km += 1000L
                count++
            }
            return generated
        }
    }
}
