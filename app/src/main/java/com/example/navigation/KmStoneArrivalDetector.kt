package com.example.navigation

import com.example.location.LocationState
import com.example.model.Chainage
import com.example.model.ChainageMatchResult
import com.example.model.ChainageSource
import com.example.model.KmStoneAlertSettings
import com.example.model.MatchConfidence
import com.example.model.RoadSide
import com.example.model.RouteData
import com.example.model.SmartDirectionState
import com.example.ui.home.KmStoneReference
import com.example.ui.home.NextKmStoneTracker
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class KmStoneAlertKind {
    ADVANCE_WARNING,
    ARRIVAL,
    VOICE_DISTANCE
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
    val timestampMs: Long,
    val announcedIntervalMetres: Int? = null,
    val speedKmh: Int? = null
)

/**
 * Pure, stateful KM Stone arrival, advance-warning, and dynamic voice-interval detector that
 * reuses CTECS's existing calibrated chainage, LHS/RHS side resolution, and KMZ/KML KM Stone
 * references.
 *
 * Guarantees:
 * - Supports LHS (increasing) and RHS (decreasing) travel directions.
 * - Supports irregular KM Stone intervals from imported KMZ/KML markers and calibrated 1 km stones.
 * - Triggers arrival alert strictly once per KM Stone passage.
 * - Re-arms a stone only after the vehicle moves >= repeatAlertDistanceMetres away or progresses to another stone.
 * - Keeps Advance Warning, Dynamic Voice Distance Intervals, and Arrival alerts cleanly coordinated.
 * - Never announces markers behind the vehicle in the current travel direction.
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

    // Tracks per-stone announced dynamic voice intervals (1000, 500, 200, 100, 50)
    private val announcedVoiceIntervalsByStone = mutableMapOf<Long, MutableSet<Int>>()
    private var lastVoiceDirectionSide: String? = null
    private var lastVoiceTargetStoneMetres: Long? = null

    private var lastArrivalEventMs: Long = 0L
    private var lastAdvanceEventMs: Long = 0L
    private var lastVoiceDistanceEventMs: Long = 0L

    @Synchronized
    fun reset() {
        activeRouteId = null
        cachedMarkerCount = -1
        cachedStones = emptyList()
        lastValidChainageMetres = null
        lastConfirmedSide = null
        disarmedArrivalStones.clear()
        disarmedAdvanceStones.clear()
        announcedVoiceIntervalsByStone.clear()
        lastVoiceDirectionSide = null
        lastVoiceTargetStoneMetres = null
        lastArrivalEventMs = 0L
        lastAdvanceEventMs = 0L
        lastVoiceDistanceEventMs = 0L
    }

    @Synchronized
    fun evaluate(
        locationState: LocationState?,
        matchResult: ChainageMatchResult,
        routeData: RouteData?,
        projectRoadSide: RoadSide,
        settings: KmStoneAlertSettings,
        nowMs: Long = System.currentTimeMillis(),
        smartDirectionState: SmartDirectionState? = matchResult.smartDirectionState
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
            announcedVoiceIntervalsByStone.clear()
            lastVoiceDirectionSide = null
            lastVoiceTargetStoneMetres = null
        }

        if (cachedStones.isEmpty()) return emptyList()

        val curM = chainageObj.metres.coerceAtLeast(0L)
        val prevM = lastValidChainageMetres
        val speedKmh = locationState.speed
            ?.takeIf { !it.isNaN() && it >= 0f }
            ?.let { (it * 3.6f).roundToInt() }

        // 4. Resolve authoritative LHS/RHS travel direction & chainage progression trend
        val effectiveSide: String
        val isIncreasingTravel: Boolean

        if (smartDirectionState != null) {
            val confirmedBinary = smartDirectionState.confirmedBinarySide
            val confirmedInc = smartDirectionState.isIncreasingChainage
            if (confirmedBinary == null || confirmedInc == null) {
                // Authoritative engine reports uncertain / weak GPS / alignment unavailable:
                // do not guess a travel direction or announce markers behind the vehicle.
                lastValidChainageMetres = curM
                return emptyList()
            }
            effectiveSide = confirmedBinary
            isIncreasingTravel = confirmedInc
        } else {
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
            effectiveSide = explicitSide ?: trendSide ?: detectedSide ?: lastConfirmedSide ?: "LHS"
            val convention = SmartDirectionManager.resolveEffectiveConvention(
                routeData = routeData,
                activeAlignment = SmartDirectionManager.resolveMatchedOrActiveAlignment(routeData, matchResult),
                requested = com.example.model.AlignmentDirectionConvention.AUTO_FROM_ALIGNMENT
            )
            isIncreasingTravel = SmartDirectionManager.isIncreasingForSide(effectiveSide, convention)
        }
        lastConfirmedSide = effectiveSide

        // If vehicle reverses travel direction (LHS <-> RHS), reset voice interval & advance history so upcoming
        // stones in the new direction announce cleanly without stale state or duplicate arrival alerts.
        if (lastVoiceDirectionSide != null && lastVoiceDirectionSide != effectiveSide) {
            announcedVoiceIntervalsByStone.clear()
            disarmedAdvanceStones.clear()
            lastVoiceTargetStoneMetres = null
        }
        lastVoiceDirectionSide = effectiveSide

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
            val movedPastOrFar = if (isIncreasingTravel) {
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
            isIncreasingTravel = isIncreasingTravel,
            proximityM = proximityM,
            stones = cachedStones
        )

        if (arrivedStone != null && !disarmedArrivalStones.contains(arrivedStone.chainageMetres)) {
            if ((nowMs - lastArrivalEventMs) >= MIN_ALERT_COOLDOWN_MS) {
                disarmedArrivalStones.add(arrivedStone.chainageMetres)
                // Mark advance warning & all dynamic voice intervals for this stone as consumed
                disarmedAdvanceStones.add(arrivedStone.chainageMetres)
                announcedVoiceIntervalsByStone[arrivedStone.chainageMetres] =
                    KmStoneAlertSettings.VOICE_DISTANCE_INTERVAL_OPTIONS.toMutableSet()
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
                        timestampMs = nowMs,
                        announcedIntervalMetres = null,
                        speedKmh = speedKmh
                    )
                )
            }
        }

        val hasArrivalThisTick = triggeredEvents.any { it.kind == KmStoneAlertKind.ARRIVAL }

        // 7. Check Smart Advance Warning for the upcoming KM Stone in travel direction
        if (advanceWarnM > proximityM && !hasArrivalThisTick) {
            val upcomingStone = findUpcomingKmStone(
                curM = curM,
                isIncreasingTravel = isIncreasingTravel,
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
                                timestampMs = nowMs,
                                announcedIntervalMetres = null,
                                speedKmh = speedKmh
                            )
                        )
                    }
                }
            }
        }

        // 8. Smart Dynamic Multi-Interval Voice Announcements (road-alignment chainage distance)
        // Strictly ahead in travel direction; arrival takes priority over distance callouts.
        if (settings.isVoiceActiveForLiveAlerts() && !hasArrivalThisTick) {
            val enabledIntervals = settings.enabledVoiceIntervalsDescending()
                .filter { it.toLong() > proximityM }

            if (enabledIntervals.isNotEmpty()) {
                val upcomingStone = findUpcomingKmStone(
                    curM = curM,
                    isIncreasingTravel = isIncreasingTravel,
                    proximityM = proximityM,
                    stones = cachedStones
                )

                // Re-arm stones that are no longer the active target and have been left well behind
                val targetStoneM = upcomingStone?.chainageMetres
                val voiceIter = announcedVoiceIntervalsByStone.entries.iterator()
                while (voiceIter.hasNext()) {
                    val (stoneM, _) = voiceIter.next()
                    val isBehindAndFar = if (isIncreasingTravel) {
                        curM > stoneM + max(rearmDistanceM, 120L)
                    } else {
                        curM < stoneM - max(rearmDistanceM, 120L)
                    }
                    val isFarAhead = abs(curM - stoneM) > 1_300L
                    if (stoneM != targetStoneM && (isBehindAndFar || isFarAhead)) {
                        voiceIter.remove()
                    }
                }

                if (upcomingStone != null) {
                    val stoneM = upcomingStone.chainageMetres
                    val distRemaining = abs(stoneM - curM)
                    val prevOnApproachSide = prevM != null && if (isIncreasingTravel) {
                        prevM < stoneM
                    } else {
                        prevM > stoneM
                    }
                    val prevDist = if (prevOnApproachSide && prevM != null) {
                        abs(stoneM - prevM)
                    } else {
                        null
                    }
                    val isMovingCloser = prevDist == null || distRemaining < prevDist
                    val alreadyAnnounced = announcedVoiceIntervalsByStone.getOrPut(stoneM) { mutableSetOf() }

                    if (isMovingCloser && (nowMs - lastVoiceDistanceEventMs) >= MIN_VOICE_COOLDOWN_MS) {
                        val newlyCrossed = enabledIntervals.filter { intervalM ->
                            if (alreadyAnnounced.contains(intervalM)) return@filter false
                            val threshold = intervalM.toLong()
                            if (prevDist != null) {
                                // Clean threshold crossing during forward movement (handles highway speeds)
                                prevDist > threshold && distRemaining <= threshold
                            } else {
                                // Initial GPS lock: trigger only if inside a tight entry band below threshold
                                val entryBand = min(35L, max(12L, threshold / 4L))
                                distRemaining in (threshold - entryBand).coerceAtLeast(proximityM + 1L)..threshold
                            }
                        }

                        val chosenInterval = newlyCrossed.minOrNull()
                        if (chosenInterval != null) {
                            // Consume chosen interval and any larger intervals so stale larger distances never fire late
                            enabledIntervals.filter { it >= chosenInterval }.forEach { alreadyAnnounced.add(it) }
                            lastVoiceDistanceEventMs = nowMs
                            lastVoiceTargetStoneMetres = stoneM
                            triggeredEvents.add(
                                KmStoneAlertEvent(
                                    kind = KmStoneAlertKind.VOICE_DISTANCE,
                                    stoneChainageMetres = stoneM,
                                    stoneFormattedChainage = upcomingStone.formattedChainage,
                                    currentChainageMetres = curM,
                                    currentFormattedChainage = chainageObj.format(),
                                    directionSide = effectiveSide,
                                    distanceMetres = distRemaining,
                                    routeId = routeData.id,
                                    timestampMs = nowMs,
                                    announcedIntervalMetres = chosenInterval,
                                    speedKmh = speedKmh
                                )
                            )
                        }
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
        isIncreasingTravel: Boolean,
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

        // Next check if a stone was crossed between prevM and curM strictly along the active travel direction (<= 140m)
        if (prevM != null && abs(curM - prevM) in 1L..140L) {
            val crossed = if (isIncreasingTravel && curM > prevM) {
                stones.firstOrNull { it.chainageMetres in (prevM + 1L)..curM }
            } else if (!isIncreasingTravel && curM < prevM) {
                stones.lastOrNull { it.chainageMetres in curM until prevM }
            } else {
                null
            }
            if (crossed != null) {
                return crossed
            }
        }
        return null
    }

    private fun findUpcomingKmStone(
        curM: Long,
        isIncreasingTravel: Boolean,
        proximityM: Long,
        stones: List<KmStoneReference>
    ): KmStoneReference? {
        return if (isIncreasingTravel) {
            stones.firstOrNull { it.chainageMetres > curM + proximityM }
        } else {
            stones.lastOrNull { it.chainageMetres < curM - proximityM }
        }
    }

    companion object {
        const val MAX_GPS_FIX_AGE_MS = 12_000L
        const val MIN_ALERT_COOLDOWN_MS = 900L
        const val MIN_VOICE_COOLDOWN_MS = 650L

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
