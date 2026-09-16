package dev.navisense.navigation.maps

import dev.navisense.navigation.maps.models.*
import kotlin.math.abs
import kotlin.math.max

sealed class RouteValidationResult {
    data class Valid(val route: WalkingRoute, val computedGeometryLengthMeters: Double) : RouteValidationResult()
    data class Invalid(val reason: String, val exception: NavigationException) : RouteValidationResult()
}

/**
 * Pure, deterministic route validator enforcing endpoint proximity,
 * coordinate integrity, and geometric length vs provider total consistency.
 */
object RouteValidator {

    const val DEFAULT_MAX_ENDPOINT_DEVIATION_METERS = 150.0

    /**
     * Computes the total geometric length along a polyline.
     */
    fun computePolylineLengthMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += PedestrianProgressCalculator.computeDistanceMeters(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            )
        }
        return total
    }

    /**
     * Validates a walking route against requested endpoints and geometric consistency rules.
     */
    fun validateRoute(
        route: WalkingRoute,
        requestedOrigin: GeoPoint? = null,
        requestedDestination: GeoPoint? = null,
        maxEndpointDeviationMeters: Double = DEFAULT_MAX_ENDPOINT_DEVIATION_METERS
    ): RouteValidationResult {
        // 1. Check non-empty steps
        if (route.steps.isEmpty()) {
            return RouteValidationResult.Invalid(
                "Route contains no steps",
                RouteNotFoundException("Route contains no steps")
            )
        }

        // 2. Validate overview geometry or concatenated step geometry
        val polyline = if (route.overviewPolyline.isNotEmpty()) {
            route.overviewPolyline
        } else {
            route.steps.flatMap { step ->
                if (step.polylinePoints.isNotEmpty()) step.polylinePoints else listOf(step.startLocation, step.endLocation)
            }
        }

        if (polyline.size < 2) {
            return RouteValidationResult.Invalid(
                "Route geometry contains fewer than 2 points",
                MalformedGeometryException("Route geometry contains fewer than 2 points")
            )
        }

        // 3. Verify all coordinates are finite and valid WGS84 coordinates
        for (pt in polyline) {
            if (pt.latitude.isNaN() || pt.latitude.isInfinite() || pt.latitude < -90.0 || pt.latitude > 90.0 ||
                pt.longitude.isNaN() || pt.longitude.isInfinite() || pt.longitude < -180.0 || pt.longitude > 180.0) {
                return RouteValidationResult.Invalid(
                    "Route geometry contains invalid/out-of-range coordinate: (${pt.latitude}, ${pt.longitude})",
                    MalformedGeometryException("Route geometry contains invalid coordinates")
                )
            }
        }

        // 4. Verify endpoints proximity to requested coordinates if supplied
        val routeStart = polyline.first()
        val routeEnd = polyline.last()

        if (requestedOrigin != null) {
            val startDist = PedestrianProgressCalculator.computeDistanceMeters(
                requestedOrigin.latitude, requestedOrigin.longitude,
                routeStart.latitude, routeStart.longitude
            )
            if (startDist > maxEndpointDeviationMeters) {
                return RouteValidationResult.Invalid(
                    "Route start point deviates ${startDist.toInt()}m from requested origin (exceeds ${maxEndpointDeviationMeters}m limit)",
                    InconsistentRouteException("Route start point deviates excessively from requested origin")
                )
            }
        }

        if (requestedDestination != null) {
            val endDist = PedestrianProgressCalculator.computeDistanceMeters(
                requestedDestination.latitude, requestedDestination.longitude,
                routeEnd.latitude, routeEnd.longitude
            )
            if (endDist > maxEndpointDeviationMeters) {
                return RouteValidationResult.Invalid(
                    "Route end point deviates ${endDist.toInt()}m from requested destination (exceeds ${maxEndpointDeviationMeters}m limit)",
                    InconsistentRouteException("Route end point deviates excessively from requested destination")
                )
            }
        }

        // 5. Compare geometric polyline length with provider total distance
        val geometryLength = computePolylineLengthMeters(polyline)
        val providerTotal = route.totalDistanceMeters.toDouble()

        if (providerTotal < 0.0 || providerTotal.isNaN()) {
            return RouteValidationResult.Invalid(
                "Provider total distance is negative or invalid: $providerTotal",
                InconsistentRouteException("Invalid provider distance")
            )
        }

        // Consistency policy: abs(geometryLength - providerTotal) > max(25.0, 0.15 * providerTotal)
        val maxAllowedDiscrepancy = max(25.0, 0.15 * providerTotal)
        val discrepancy = abs(geometryLength - providerTotal)

        if (discrepancy > maxAllowedDiscrepancy) {
            return RouteValidationResult.Invalid(
                "Route geometry length ($geometryLength m) diverges from provider distance ($providerTotal m) by ${discrepancy.toInt()}m (tolerance: ${maxAllowedDiscrepancy.toInt()}m)",
                InconsistentRouteException("Geometry length diverges from provider distance")
            )
        }

        // 6. Check step distances sum coherence
        val sumStepDistances = route.steps.sumOf { it.distanceMeters }.toDouble()
        val stepDiscrepancy = abs(sumStepDistances - providerTotal)
        val maxStepAllowedDiscrepancy = max(30.0, 0.20 * providerTotal)
        if (stepDiscrepancy > maxStepAllowedDiscrepancy) {
            return RouteValidationResult.Invalid(
                "Sum of step distances ($sumStepDistances m) diverges from provider total ($providerTotal m) by ${stepDiscrepancy.toInt()}m",
                InconsistentRouteException("Sum of step distances diverges from provider total")
            )
        }

        return RouteValidationResult.Valid(route, geometryLength)
    }
}
