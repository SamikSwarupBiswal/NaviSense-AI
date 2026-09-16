package dev.navisense.navigation.maps

import dev.navisense.navigation.maps.models.GeoPoint
import dev.navisense.navigation.maps.models.ManeuverType
import dev.navisense.navigation.maps.models.WalkingRoute
import dev.navisense.navigation.maps.models.WalkingStep

/**
 * Isolated test fixture builder for walking routes used in unit testing.
 * Strictly prohibited from production use.
 */
object WalkingRouteFixtureBuilder {

    fun createMockWalkingRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String,
        initialStreet: String = "Test Walkway"
    ): WalkingRoute {
        val step1End = GeoPoint(origin.latitude + 0.0003, origin.longitude)
        val step2End = GeoPoint(step1End.latitude, step1End.longitude + 0.0004)
        val step3End = destination

        val step1 = WalkingStep(
            instruction = "Head forward on $initialStreet",
            maneuver = ManeuverType.DEPART,
            distanceMeters = 35,
            durationSeconds = 25,
            startLocation = origin,
            endLocation = step1End,
            streetName = initialStreet,
            polylinePoints = listOf(origin, step1End)
        )

        val step2 = WalkingStep(
            instruction = "Turn right onto Test Avenue",
            maneuver = ManeuverType.RIGHT,
            distanceMeters = 45,
            durationSeconds = 35,
            startLocation = step1End,
            endLocation = step2End,
            streetName = "Test Avenue",
            polylinePoints = listOf(step1End, step2End)
        )

        val step3 = WalkingStep(
            instruction = "Turn left towards $destinationName",
            maneuver = ManeuverType.LEFT,
            distanceMeters = 40,
            durationSeconds = 30,
            startLocation = step2End,
            endLocation = step3End,
            streetName = destinationName,
            polylinePoints = listOf(step2End, step3End)
        )

        return WalkingRoute(
            destinationName = destinationName,
            totalDistanceMeters = 120,
            totalDurationSeconds = 90,
            steps = listOf(step1, step2, step3),
            overviewPolyline = listOf(origin, step1End, step2End, step3End)
        )
    }
}
