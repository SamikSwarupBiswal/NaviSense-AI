package dev.navisense.navigation.maps

import dev.navisense.navigation.maps.models.*
import org.junit.Assert.*
import org.junit.Test

class RouteValidatorTest {

    @Test
    fun testG02_ProviderTotalDiscrepancyRejection() {
        // Simulates the AB1 bug: provider claimed 178m (or small distance) but geometry is 728.1 km
        val p1 = GeoPoint(12.8398, 80.1550) // VIT Chennai
        val p2 = GeoPoint(17.3850, 78.4867) // Hyderabad (~520 km away)
        val route = WalkingRoute(
            destinationName = "Academic Block 1 (AB1)",
            totalDistanceMeters = 178, // Fraudulent / mismatching provider total
            totalDurationSeconds = 150,
            steps = listOf(
                WalkingStep(
                    instruction = "Walk to destination",
                    maneuver = ManeuverType.STRAIGHT,
                    distanceMeters = 178,
                    durationSeconds = 150,
                    startLocation = p1,
                    endLocation = p2
                )
            ),
            overviewPolyline = listOf(p1, p2)
        )

        val result = RouteValidator.validateRoute(route)
        assertTrue("Route with massive discrepancy must be Invalid", result is RouteValidationResult.Invalid)
        val invalid = result as RouteValidationResult.Invalid
        assertTrue(invalid.exception is InconsistentRouteException)
        assertTrue(invalid.reason.contains("diverges from provider distance"))
    }

    @Test
    fun testG03_ValidRouteWithMatchingGeometry() {
        // Legitimate ~200m walking path
        val p1 = GeoPoint(12.8406, 80.1534)
        val p2 = GeoPoint(12.8415, 80.1534) // ~100m north
        val p3 = GeoPoint(12.8415, 80.1543) // ~97m east
        val expectedLength = PedestrianProgressCalculator.computeDistanceMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude) +
                PedestrianProgressCalculator.computeDistanceMeters(p2.latitude, p2.longitude, p3.latitude, p3.longitude)

        val route = WalkingRoute(
            destinationName = "Valid Campus Path",
            totalDistanceMeters = expectedLength.toInt(),
            totalDurationSeconds = 160,
            steps = listOf(
                WalkingStep(
                    instruction = "Head north",
                    maneuver = ManeuverType.STRAIGHT,
                    distanceMeters = 100,
                    durationSeconds = 80,
                    startLocation = p1,
                    endLocation = p2
                ),
                WalkingStep(
                    instruction = "Turn right",
                    maneuver = ManeuverType.RIGHT,
                    distanceMeters = (expectedLength - 100).toInt(),
                    durationSeconds = 80,
                    startLocation = p2,
                    endLocation = p3
                )
            ),
            overviewPolyline = listOf(p1, p2, p3)
        )

        val result = RouteValidator.validateRoute(route, requestedOrigin = p1, requestedDestination = p3)
        assertTrue("Expected Valid for matching geometry and endpoints, got $result", result is RouteValidationResult.Valid)
    }

    @Test
    fun testG05_TruncatedOrCorruptGeometryRejection() {
        val p1 = GeoPoint(12.8406, 80.1534)
        val singlePointRoute = WalkingRoute(
            destinationName = "Corrupt Route",
            totalDistanceMeters = 100,
            totalDurationSeconds = 60,
            steps = listOf(
                WalkingStep("Go", ManeuverType.STRAIGHT, 100, 60, p1, p1)
            ),
            overviewPolyline = listOf(p1) // Only 1 point
        )

        val res1 = RouteValidator.validateRoute(singlePointRoute)
        assertTrue(res1 is RouteValidationResult.Invalid)
        assertTrue((res1 as RouteValidationResult.Invalid).exception is MalformedGeometryException)

        // Nan / infinite coordinate
        val badCoord = GeoPoint(Double.NaN, 80.1534)
        val nanRoute = WalkingRoute(
            destinationName = "NaN Route",
            totalDistanceMeters = 100,
            totalDurationSeconds = 60,
            steps = listOf(
                WalkingStep("Go", ManeuverType.STRAIGHT, 100, 60, p1, badCoord)
            ),
            overviewPolyline = listOf(p1, badCoord)
        )
        val res2 = RouteValidator.validateRoute(nanRoute)
        assertTrue(res2 is RouteValidationResult.Invalid)
        assertTrue((res2 as RouteValidationResult.Invalid).exception is MalformedGeometryException)
    }

    @Test
    fun testG06_ExcessiveEndpointDeviationRejection() {
        val reqOrigin = GeoPoint(12.8406, 80.1534)
        val farStart = GeoPoint(12.8500, 80.1534) // > 1 km away
        val dest = GeoPoint(12.8510, 80.1534)

        val route = WalkingRoute(
            destinationName = "Deviated Start",
            totalDistanceMeters = 111,
            totalDurationSeconds = 90,
            steps = listOf(
                WalkingStep("Go", ManeuverType.STRAIGHT, 111, 90, farStart, dest)
            ),
            overviewPolyline = listOf(farStart, dest)
        )

        val result = RouteValidator.validateRoute(route, requestedOrigin = reqOrigin, requestedDestination = dest)
        assertTrue(result is RouteValidationResult.Invalid)
        assertTrue((result as RouteValidationResult.Invalid).exception is InconsistentRouteException)
        assertTrue(result.reason.contains("start point deviates"))
    }
}
