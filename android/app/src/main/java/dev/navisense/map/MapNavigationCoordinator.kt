package dev.navisense.map

import dev.navisense.contracts.IClock
import dev.navisense.contracts.SystemMonotonicClock
import dev.navisense.navigation.maps.PedestrianProgressCalculator
import dev.navisense.voice.AlertPriority
import dev.navisense.voice.ISpeechArbiter
import dev.navisense.voice.SpeechRequest
import kotlin.math.roundToInt

/**
 * Coordinates turn-by-turn spoken guidance along an active navigation route.
 */
class MapNavigationCoordinator(
    private val speechArbiter: ISpeechArbiter?,
    private val clock: IClock = SystemMonotonicClock()
) {
    private var activeRoute: NavigationRoute? = null
    private var activeSessionGeneration: Long = 1L
    private var currentManeuverIndex = 0
    private var hasAnnouncedApproach = false
    private var hasAnnouncedImmediateTurn = false
    private var isNavigating = false
    private var lastSpokenInstruction: String? = null
    private var lastSpokenMs: Long = 0L

    var onRouteUpdated: ((route: NavigationRoute?, currentManeuver: Maneuver?, remainingDistanceMeters: Double) -> Unit)? = null
    var onArrival: ((destination: MapPOI) -> Unit)? = null

    fun startNavigation(route: NavigationRoute, sessionGeneration: Long = 1L) {
        activeRoute = route
        activeSessionGeneration = sessionGeneration
        currentManeuverIndex = 0
        hasAnnouncedApproach = false
        hasAnnouncedImmediateTurn = false
        isNavigating = true

        val startManeuver = route.maneuvers.firstOrNull()
        val intro = if (startManeuver != null) {
            "Starting navigation to ${route.destination.name}. ${startManeuver.instruction}."
        } else {
            "Starting navigation to ${route.destination.name}."
        }
        speakInstruction(intro, force = true)
        onRouteUpdated?.invoke(route, startManeuver, route.totalDistanceMeters)
    }

    fun stopNavigation() {
        isNavigating = false
        activeRoute = null
        currentManeuverIndex = 0
        speakInstruction("Navigation stopped.", force = true)
        onRouteUpdated?.invoke(null, null, 0.0)
    }

    fun onLocationUpdated(lat: Double, lon: Double, bearingDegrees: Float) {
        val route = activeRoute ?: return
        if (!isNavigating) return

        if (currentManeuverIndex >= route.maneuvers.size) {
            handleArrival(route.destination)
            return
        }

        val currentManeuver = route.maneuvers[currentManeuverIndex]
        val distToWaypoint = MapRoutingEngine.computeDistanceMeters(
            lat, lon,
            currentManeuver.waypoint.lat, currentManeuver.waypoint.lon
        )

        // Compute total along-route remaining distance
        val polylinePairs = route.polylinePoints.map { Pair(it.lat, it.lon) }
        val remainingDist = if (polylinePairs.isNotEmpty()) {
            PedestrianProgressCalculator.computeAlongRouteRemainingDistance(
                lat, lon,
                polylinePairs,
                preferredStartIndex = currentManeuverIndex
            )
        } else {
            var dist = distToWaypoint
            for (i in (currentManeuverIndex + 1) until route.maneuvers.size) {
                dist += route.maneuvers[i].distanceMeters
            }
            dist
        }
        onRouteUpdated?.invoke(route, currentManeuver, remainingDist)

        if (currentManeuver.turnType == TurnType.ARRIVE) {
            if (distToWaypoint <= ARRIVAL_THRESHOLD_METERS) {
                handleArrival(route.destination)
            } else if (distToWaypoint <= 25.0 && !hasAnnouncedApproach) {
                hasAnnouncedApproach = true
                speakInstruction("Approaching destination, ${route.destination.name}, in ${distToWaypoint.roundToInt()} meters.")
            }
            return
        }

        // Advance to next maneuver if within transition threshold
        if (distToWaypoint <= WAYPOINT_TRANSITION_THRESHOLD_METERS) {
            advanceToNextManeuver()
            return
        }

        // Approaching turn cue (25 - 40 meters)
        if (distToWaypoint in 20.0..40.0 && !hasAnnouncedApproach) {
            hasAnnouncedApproach = true
            val prompt = "In ${distToWaypoint.roundToInt()} meters, ${MapRoutingEngine.turnTypeToSpeech(currentManeuver.turnType).lowercase()} onto ${currentManeuver.roadName}."
            speakInstruction(prompt)
        }
        // Immediate turn cue (8 - 15 meters)
        else if (distToWaypoint in 5.0..15.0 && !hasAnnouncedImmediateTurn) {
            hasAnnouncedImmediateTurn = true
            val prompt = "${MapRoutingEngine.turnTypeToSpeech(currentManeuver.turnType)} onto ${currentManeuver.roadName}."
            speakInstruction(prompt)
        }
    }

    private fun advanceToNextManeuver() {
        val route = activeRoute ?: return
        currentManeuverIndex++
        hasAnnouncedApproach = false
        hasAnnouncedImmediateTurn = false

        if (currentManeuverIndex < route.maneuvers.size) {
            val next = route.maneuvers[currentManeuverIndex]
            if (next.turnType == TurnType.ARRIVE) {
                speakInstruction("Continue straight towards ${route.destination.name}.")
            } else {
                speakInstruction("${next.instruction}.")
            }
        } else {
            handleArrival(route.destination)
        }
    }

    private fun handleArrival(destination: MapPOI) {
        isNavigating = false
        speakInstruction("You have arrived at ${destination.name}.", force = true)
        onArrival?.invoke(destination)
        onRouteUpdated?.invoke(null, null, 0.0)
        activeRoute = null
    }

    private fun speakInstruction(phrase: String, force: Boolean = false) {
        val now = clock.nowMonotonicMs()
        if (!force && phrase == lastSpokenInstruction && (now - lastSpokenMs) < 6000L) {
            return
        }
        lastSpokenInstruction = phrase
        lastSpokenMs = now
        speechArbiter?.speak(
            SpeechRequest(
                utteranceId = "nav_step_$now",
                phrase = phrase,
                priority = AlertPriority.DIRECTIONAL,
                sessionGeneration = activeSessionGeneration,
                requestMonotonicMs = now
            )
        )
    }

    companion object {
        private const val WAYPOINT_TRANSITION_THRESHOLD_METERS = 8.0
        private const val ARRIVAL_THRESHOLD_METERS = 12.0
    }
}
