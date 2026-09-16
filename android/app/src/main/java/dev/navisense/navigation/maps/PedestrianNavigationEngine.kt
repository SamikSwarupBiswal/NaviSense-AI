package dev.navisense.navigation.maps

import dev.navisense.contracts.IClock
import dev.navisense.contracts.SystemMonotonicClock
import dev.navisense.navigation.maps.models.GeoPoint
import dev.navisense.navigation.maps.models.ManeuverType
import dev.navisense.navigation.maps.models.NavigationEngineStatus
import dev.navisense.navigation.maps.models.NavigationGuidance
import dev.navisense.navigation.maps.models.NavigationSnapshot
import dev.navisense.navigation.maps.models.WalkingRoute
import dev.navisense.voice.AlertPriority
import kotlin.math.abs

/**
 * Pedestrian turn-by-turn navigation engine combining GPS positioning,
 * Google Maps walking steps, and compass heading for orientation-aware guidance.
 */
class PedestrianNavigationEngine(
    private val clock: IClock = SystemMonotonicClock()
) {

    companion object {
        const val UPCOMING_ALERT_DISTANCE_METERS = 25f
        const val ACTIONABLE_TURN_DISTANCE_METERS = 6f
        const val STEP_ADVANCE_DISTANCE_METERS = 4f
        const val ARRIVAL_DISTANCE_METERS = 5f
        const val OFF_ROUTE_DISTANCE_METERS = 30f
        const val MIN_UPDATE_INTERVAL_MS = 1500L
    }

    interface NavigationListener {
        fun onGuidanceGenerated(guidance: NavigationGuidance)
        fun onStatusUpdated(status: NavigationEngineStatus)
        fun onOffRouteDetected()
        fun onArrival()
        fun onSnapshotUpdated(snapshot: NavigationSnapshot) {}
    }

    private var activeRoute: WalkingRoute? = null
    private var currentStepIndex: Int = 0
    private var lastMatchedSegmentIndex: Int = 0
    private var cachedChainage: PedestrianProgressCalculator.PolylineChainage? = null
    private var currentHeadingDegrees: Float = 0f
    private var lastLocation: GeoPoint? = null
    private var lastGuidanceTimeMs: Long = 0L

    private var alert50mGivenForStep = false
    private var alert20mGivenForStep = false
    private var actionableAlertGivenForStep = false
    private var offRouteCount = 0
    private var hasArrived = false

    private val listeners = mutableListOf<NavigationListener>()

    fun addListener(listener: NavigationListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: NavigationListener) {
        listeners.remove(listener)
    }

    /**
     * Initializes navigation along a new walking route.
     */
    @Synchronized
    fun startRoute(route: WalkingRoute) {
        this.activeRoute = route
        this.currentStepIndex = 0
        this.lastMatchedSegmentIndex = 0
        val fullPolyline = if (route.overviewPolyline.isNotEmpty()) {
            route.overviewPolyline
        } else {
            route.steps.flatMap { step ->
                if (step.polylinePoints.isNotEmpty()) step.polylinePoints else listOf(step.startLocation, step.endLocation)
            }
        }
        this.cachedChainage = PedestrianProgressCalculator.buildChainage(fullPolyline)
        this.alert50mGivenForStep = false
        this.alert20mGivenForStep = false
        this.actionableAlertGivenForStep = false
        this.offRouteCount = 0
        this.hasArrived = false
        this.lastGuidanceTimeMs = 0L

        if (route.steps.isNotEmpty()) {
            val initialStep = route.steps[0]
            val initialStreet = initialStep.streetName.ifBlank { "the route" }
            val guidance = NavigationGuidance(
                phrase = "Starting walking navigation to ${route.destinationName}. We are walking on $initialStreet. Head forward for ${initialStep.distanceMeters} meters.",
                priority = AlertPriority.DIRECTIONAL,
                distanceToManeuverMeters = initialStep.distanceMeters.toFloat(),
                relativeBearingDegrees = 0f,
                isActionableCue = false
            )
            dispatchGuidance(guidance)
        }
    }

    /**
     * Updates device compass azimuth heading [0, 360) degrees.
     */
    @Synchronized
    fun onHeadingUpdated(azimuthDegrees: Float) {
        this.currentHeadingDegrees = azimuthDegrees
    }

    /**
     * Ingests latest GPS location update and evaluates progress along the route.
     */
    @Synchronized
    fun onLocationUpdated(currentLocation: GeoPoint, accuracyMeters: Float = 5f) {
        val route = activeRoute ?: return
        if (hasArrived || route.steps.isEmpty()) return

        this.lastLocation = currentLocation
        val now = clock.nowMonotonicMs()

        // 1. Destination Arrival Check
        val lastStep = route.steps.last()
        val distanceToFinalDestination = currentLocation.distanceTo(lastStep.endLocation)

        val fullPolyline = if (route.overviewPolyline.isNotEmpty()) {
            route.overviewPolyline
        } else {
            route.steps.flatMap { step ->
                if (step.polylinePoints.isNotEmpty()) step.polylinePoints else listOf(step.startLocation, step.endLocation)
            }
        }
        val chainage = cachedChainage ?: PedestrianProgressCalculator.buildChainage(fullPolyline).also { cachedChainage = it }
        val match = PedestrianProgressCalculator.matchFixToRoute(
            fix = currentLocation,
            chainage = chainage,
            preferredSegmentIndex = lastMatchedSegmentIndex,
            searchWindowRadius = 5,
            maxCrossTrackMeters = 50.0
        )
        if (match.isValid) {
            lastMatchedSegmentIndex = match.segmentIndex
        }
        val remainingDistanceAlongRoute = if (match.isValid) {
            kotlin.math.max(0.0, chainage.totalLengthMeters - match.chainageMeters).toFloat()
        } else {
            PedestrianProgressCalculator.computeRemainingDistanceGeoPoints(
                currentLocation,
                fullPolyline,
                preferredStartIndex = lastMatchedSegmentIndex
            )
        }

        if (distanceToFinalDestination <= ARRIVAL_DISTANCE_METERS && accuracyMeters <= 20f) {
            hasArrived = true
            val arrivalGuidance = NavigationGuidance(
                phrase = "You have arrived at your destination: ${route.destinationName}.",
                priority = AlertPriority.DIRECTIONAL,
                distanceToManeuverMeters = 0f,
                relativeBearingDegrees = 0f,
                isActionableCue = true
            )
            dispatchGuidance(arrivalGuidance)
            listeners.forEach { it.onArrival() }
            updateStatus(currentLocation, 0f, 0f)
            return
        }

        // 2. Step Progress & Advancement
        val currentStep = route.steps.getOrNull(currentStepIndex) ?: return
        val nextStep = route.steps.getOrNull(currentStepIndex + 1)
        val distanceToStepEnd = currentLocation.distanceTo(currentStep.endLocation)

        // Advance step if close enough to waypoint
        if (distanceToStepEnd <= STEP_ADVANCE_DISTANCE_METERS && currentStepIndex < route.steps.size - 1) {
            currentStepIndex++
            alert50mGivenForStep = false
            alert20mGivenForStep = false
            actionableAlertGivenForStep = false
            val advancedStep = route.steps[currentStepIndex]
            val stepBearing = currentLocation.bearingTo(advancedStep.endLocation)
            val street = advancedStep.streetName.ifBlank { advancedStep.instruction }

            val phrase = "We are walking on $street. Continue for ${advancedStep.distanceMeters} meters."

            dispatchGuidance(
                NavigationGuidance(
                    phrase = phrase,
                    priority = AlertPriority.DIRECTIONAL,
                    distanceToManeuverMeters = advancedStep.distanceMeters.toFloat(),
                    relativeBearingDegrees = calculateRelativeAngle(stepBearing, currentHeadingDegrees),
                    isActionableCue = true
                )
            )
            lastGuidanceTimeMs = now
            updateStatus(currentLocation, distanceToStepEnd, remainingDistanceAlongRoute)
            return
        }

        val targetManeuver = nextStep?.maneuver ?: currentStep.maneuver
        val nextStreet = nextStep?.streetName?.ifBlank { nextStep.instruction } ?: ""
        val turnAction = getTurnActionWord(targetManeuver)
        val targetStreetPhrase = if (nextStreet.isNotBlank()) {
            if (targetManeuver == ManeuverType.ARRIVE) " at $nextStreet" else " onto $nextStreet"
        } else ""

        // 3. Immediate Actionable Turn Cue (e.g. <= 6m)
        if (distanceToStepEnd <= ACTIONABLE_TURN_DISTANCE_METERS && !actionableAlertGivenForStep) {
            actionableAlertGivenForStep = true
            val stepBearing = currentLocation.bearingTo(currentStep.endLocation)
            val turnPhrase = "${turnAction.replaceFirstChar { it.uppercase() }} now$targetStreetPhrase."

            dispatchGuidance(
                NavigationGuidance(
                    phrase = turnPhrase,
                    priority = AlertPriority.DIRECTIONAL,
                    distanceToManeuverMeters = distanceToStepEnd,
                    relativeBearingDegrees = calculateRelativeAngle(stepBearing, currentHeadingDegrees),
                    isActionableCue = true
                )
            )
            lastGuidanceTimeMs = now
        }
        // 4. Upcoming 20m Alert (e.g. <= 25m)
        else if (distanceToStepEnd <= 25f && distanceToStepEnd > 6f && !alert20mGivenForStep) {
            alert20mGivenForStep = true
            val distRounded = (distanceToStepEnd / 5).toInt() * 5
            val phrase = "In $distRounded meters, $turnAction$targetStreetPhrase."

            dispatchGuidance(
                NavigationGuidance(
                    phrase = phrase,
                    priority = AlertPriority.AWARENESS,
                    distanceToManeuverMeters = distanceToStepEnd,
                    relativeBearingDegrees = 0f,
                    isActionableCue = false
                )
            )
            lastGuidanceTimeMs = now
        }
        // 5. Early 50m Alert (e.g. 40m .. 65m)
        else if (distanceToStepEnd <= 65f && distanceToStepEnd > 30f && !alert50mGivenForStep) {
            alert50mGivenForStep = true
            val distRounded = (distanceToStepEnd / 5).toInt() * 5
            val phrase = "In $distRounded meters, $turnAction$targetStreetPhrase."

            dispatchGuidance(
                NavigationGuidance(
                    phrase = phrase,
                    priority = AlertPriority.AWARENESS,
                    distanceToManeuverMeters = distanceToStepEnd,
                    relativeBearingDegrees = 0f,
                    isActionableCue = false
                )
            )
            lastGuidanceTimeMs = now
        }

        // 6. Off-Route Check
        val minDistanceToPolyline = computeMinDistanceToRoute(currentLocation, route)
        if (minDistanceToPolyline > OFF_ROUTE_DISTANCE_METERS) {
            offRouteCount++
            if (offRouteCount >= 3 && (now - lastGuidanceTimeMs) > 10000L) {
                dispatchGuidance(
                    NavigationGuidance(
                        phrase = "You appear to be off route. Recalculating path.",
                        priority = AlertPriority.AWARENESS,
                        distanceToManeuverMeters = minDistanceToPolyline,
                        relativeBearingDegrees = 0f,
                        isActionableCue = false
                    )
                )
                listeners.forEach { it.onOffRouteDetected() }
                lastGuidanceTimeMs = now
            }
        } else {
            offRouteCount = 0
        }

        updateStatus(currentLocation, distanceToStepEnd, remainingDistanceAlongRoute)
    }

    /**
     * Calculates relative angle from user's current heading to target bearing: [0, 360).
     */
    fun calculateRelativeAngle(targetBearing: Float, userHeading: Float): Float {
        return ((targetBearing - userHeading + 360f) % 360f)
    }

    /**
     * Translates relative angle into spoken pedestrian orientation cues.
     */
    fun calculateRelativeTurnPhrase(targetBearing: Float, userHeading: Float): String {
        val rel = calculateRelativeAngle(targetBearing, userHeading)
        return when {
            rel in 345f..360f || rel in 0f..15f -> "Continue straight"
            rel in 15f..60f -> "Bear slight right"
            rel in 60f..120f -> "Turn right"
            rel in 120f..170f -> "Turn sharp right"
            rel in 170f..190f -> "Make a U-turn"
            rel in 190f..240f -> "Turn sharp left"
            rel in 240f..300f -> "Turn left"
            rel in 300f..345f -> "Bear slight left"
            else -> "Continue"
        }
    }

    fun getTurnActionWord(maneuver: ManeuverType): String {
        return when (maneuver) {
            ManeuverType.RIGHT, ManeuverType.SHARP_RIGHT -> "turn right"
            ManeuverType.SLIGHT_RIGHT -> "bear slight right"
            ManeuverType.LEFT, ManeuverType.SHARP_LEFT -> "turn left"
            ManeuverType.SLIGHT_LEFT -> "bear slight left"
            ManeuverType.U_TURN -> "make a U-turn"
            ManeuverType.ARRIVE -> "arrive at destination"
            else -> "continue straight"
        }
    }

    private fun getActionableTurnPhrase(maneuver: ManeuverType, targetBearing: Float, userHeading: Float): String {
        return when (maneuver) {
            ManeuverType.RIGHT, ManeuverType.SHARP_RIGHT -> "Turn right now"
            ManeuverType.SLIGHT_RIGHT -> "Bear right now"
            ManeuverType.LEFT, ManeuverType.SHARP_LEFT -> "Turn left now"
            ManeuverType.SLIGHT_LEFT -> "Bear left now"
            ManeuverType.U_TURN -> "Make a U-turn now"
            ManeuverType.ARRIVE -> "Arriving at destination ahead"
            else -> calculateRelativeTurnPhrase(targetBearing, userHeading) + " now"
        }
    }

    private fun computeMinDistanceToRoute(point: GeoPoint, route: WalkingRoute): Float {
        var minDistance = Float.MAX_VALUE
        for (step in route.steps) {
            val distStart = point.distanceTo(step.startLocation)
            val distEnd = point.distanceTo(step.endLocation)
            if (distStart < minDistance) minDistance = distStart
            if (distEnd < minDistance) minDistance = distEnd
            for (p in step.polylinePoints) {
                val d = point.distanceTo(p)
                if (d < minDistance) minDistance = d
            }
        }
        return minDistance
    }

    private fun dispatchGuidance(guidance: NavigationGuidance) {
        listeners.forEach { it.onGuidanceGenerated(guidance) }
    }

    private fun updateStatus(currentLocation: GeoPoint, distanceToStepEnd: Float, distanceToFinal: Float) {
        val route = activeRoute ?: return
        val currentStep = route.steps.getOrNull(currentStepIndex)
        val nextStep = route.steps.getOrNull(currentStepIndex + 1)
        val curStreet = currentStep?.streetName?.ifBlank { "pedestrian walkway" } ?: "pedestrian walkway"
        val nextStreet = nextStep?.streetName?.ifBlank { nextStep.instruction } ?: ""

        val status = NavigationEngineStatus(
            destinationName = route.destinationName,
            currentStepIndex = currentStepIndex,
            totalSteps = route.steps.size,
            currentInstruction = currentStep?.instruction ?: "Complete",
            currentStreetName = curStreet,
            nextManeuverStreet = nextStreet,
            distanceToNextStepMeters = distanceToStepEnd,
            totalRemainingDistanceMeters = distanceToFinal,
            isOffRoute = offRouteCount >= 3,
            hasArrived = hasArrived
        )
        val snapshot = NavigationSnapshot(
            destinationName = route.destinationName,
            totalRemainingDistanceMeters = distanceToFinal.toDouble(),
            nextManeuverDistanceMeters = distanceToStepEnd.toDouble(),
            nextManeuverInstruction = currentStep?.instruction ?: "Complete",
            formattedDistanceLeft = NavigationSnapshot.formatDistance(distanceToFinal.toDouble()),
            isStale = false,
            isOffRoute = offRouteCount >= 3,
            hasArrived = hasArrived
        )
        listeners.forEach {
            it.onStatusUpdated(status)
            it.onSnapshotUpdated(snapshot)
        }
    }

    @Synchronized
    fun stopNavigation() {
        activeRoute = null
        currentStepIndex = 0
        lastMatchedSegmentIndex = 0
        cachedChainage = null
        hasArrived = false
        offRouteCount = 0
    }
}
