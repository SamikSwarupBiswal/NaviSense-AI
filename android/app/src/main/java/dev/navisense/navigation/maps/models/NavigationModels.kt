package dev.navisense.navigation.maps.models

import dev.navisense.voice.AlertPriority
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-precision geographic coordinate representation.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double
) {
    /**
     * Calculates distance to another GeoPoint in meters using the Haversine formula.
     */
    fun distanceTo(other: GeoPoint): Float {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(other.latitude - this.latitude)
        val dLon = Math.toRadians(other.longitude - this.longitude)
        val lat1 = Math.toRadians(this.latitude)
        val lat2 = Math.toRadians(other.latitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }

    /**
     * Calculates initial bearing (azimuth) from this point to another point in degrees [0, 360).
     */
    fun bearingTo(other: GeoPoint): Float {
        val lat1 = Math.toRadians(this.latitude)
        val lat2 = Math.toRadians(other.latitude)
        val dLon = Math.toRadians(other.longitude - this.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val initialBearing = Math.toDegrees(atan2(y, x))
        return ((initialBearing + 360.0) % 360.0).toFloat()
    }
}

/**
 * Standardized pedestrian navigation maneuvers from Google Routes API.
 */
enum class ManeuverType {
    DEPART,
    STRAIGHT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    U_TURN,
    ARRIVE,
    UNKNOWN;

    companion object {
        fun fromGoogleManeuver(maneuver: String?): ManeuverType {
            if (maneuver == null) return STRAIGHT
            return when (maneuver.lowercase().replace("-", "_").trim()) {
                "turn_slight_right", "slight_right", "ramp_slight_right", "fork_slight_right" -> SLIGHT_RIGHT
                "turn_right", "right", "ramp_right", "fork_right" -> RIGHT
                "turn_sharp_right", "sharp_right" -> SHARP_RIGHT
                "turn_slight_left", "slight_left", "ramp_slight_left", "fork_slight_left" -> SLIGHT_LEFT
                "turn_left", "left", "ramp_left", "fork_left" -> LEFT
                "turn_sharp_left", "sharp_left" -> SHARP_LEFT
                "uturn_left", "uturn_right", "u_turn" -> U_TURN
                "straight" -> STRAIGHT
                "depart" -> DEPART
                "arrive" -> ARRIVE
                else -> STRAIGHT
            }
        }
    }
}

/**
 * Single walking navigation step along the route.
 */
data class WalkingStep(
    val instruction: String,
    val maneuver: ManeuverType,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val startLocation: GeoPoint,
    val endLocation: GeoPoint,
    val streetName: String = "",
    val polylinePoints: List<GeoPoint> = emptyList()
)

/**
 * Complete pedestrian walking route from origin to destination.
 */
data class WalkingRoute(
    val destinationName: String,
    val totalDistanceMeters: Int,
    val totalDurationSeconds: Int,
    val steps: List<WalkingStep>,
    val overviewPolyline: List<GeoPoint> = emptyList()
)

/**
 * Spoken guidance package dispatched to SpeechArbiter.
 */
data class NavigationGuidance(
    val phrase: String,
    val priority: AlertPriority,
    val distanceToManeuverMeters: Float,
    val relativeBearingDegrees: Float,
    val isActionableCue: Boolean = false
)

/**
 * Live status update emitted by PedestrianNavigationEngine.
 */
data class NavigationEngineStatus(
    val destinationName: String,
    val currentStepIndex: Int,
    val totalSteps: Int,
    val currentInstruction: String,
    val currentStreetName: String = "",
    val nextManeuverStreet: String = "",
    val distanceToNextStepMeters: Float,
    val totalRemainingDistanceMeters: Float,
    val isOffRoute: Boolean,
    val hasArrived: Boolean
)

open class NavigationException(message: String, cause: Throwable? = null) : Exception(message, cause)
class DestinationNotFoundException(message: String) : NavigationException(message)
class RouteNotFoundException(message: String, cause: Throwable? = null) : NavigationException(message, cause)
class OutsideCoverageException(message: String) : NavigationException(message)
class LocationUnavailableException(message: String) : NavigationException(message)
