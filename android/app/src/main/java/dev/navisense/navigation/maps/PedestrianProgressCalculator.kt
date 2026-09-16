package dev.navisense.navigation.maps

import dev.navisense.navigation.maps.models.GeoPoint
import kotlin.math.*

/**
 * Pure, deterministic utility for calculating pedestrian route progress,
 * polyline projection, and along-route remaining distance.
 */
object PedestrianProgressCalculator {

    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Computes Haversine distance in meters between two lat/lon coordinates.
     */
    fun computeDistanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) + cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    data class SegmentProjection(
        val tClamped: Double,
        val tRaw: Double,
        val distanceToSegmentMeters: Double,
        val alongSegmentDistanceMeters: Double
    )

    /**
     * Projects point P onto line segment AB.
     * Returns SegmentProjection containing both clamped and raw projection factors.
     */
    fun projectPointOntoSegment(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): SegmentProjection {
        val segmentDist = computeDistanceMeters(aLat, aLon, bLat, bLon)
        if (segmentDist < 1e-4) {
            val distToA = computeDistanceMeters(pLat, pLon, aLat, aLon)
            return SegmentProjection(0.0, 0.0, distToA, 0.0)
        }

        // Local flat-earth projection centered at segment midpoint
        val midLatRad = Math.toRadians((aLat + bLat) / 2.0)
        val cosLat = cos(midLatRad)

        val dx = Math.toRadians(bLon - aLon) * cosLat * EARTH_RADIUS_METERS
        val dy = Math.toRadians(bLat - aLat) * EARTH_RADIUS_METERS

        val px = Math.toRadians(pLon - aLon) * cosLat * EARTH_RADIUS_METERS
        val py = Math.toRadians(pLat - aLat) * EARTH_RADIUS_METERS

        val lenSq = dx * dx + dy * dy
        val tRaw = if (lenSq > 1e-6) (px * dx + py * dy) / lenSq else 0.0
        val t = tRaw.coerceIn(0.0, 1.0)

        val projLat = aLat + t * (bLat - aLat)
        val projLon = aLon + t * (bLon - aLon)
        val distToSegment = computeDistanceMeters(pLat, pLon, projLat, projLon)
        val alongSegmentDist = t * segmentDist

        return SegmentProjection(t, tRaw, distToSegment, alongSegmentDist)
    }

    /**
     * Calculates true along-route remaining walking distance in meters from a given fix
     * along the polyline to the end of the route.
     *
     * @param currentLat User's current latitude
     * @param currentLon User's current longitude
     * @param polyline Ordered list of coordinates representing the route geometry
     * @param preferredStartIndex Hint for segment index search (to avoid jumping across loops)
     */
    fun computeAlongRouteRemainingDistance(
        currentLat: Double,
        currentLon: Double,
        polyline: List<Pair<Double, Double>>,
        preferredStartIndex: Int = 0
    ): Double {
        if (polyline.isEmpty()) return 0.0
        if (polyline.size == 1) {
            return computeDistanceMeters(currentLat, currentLon, polyline[0].first, polyline[0].second)
        }

        // Precompute segment lengths
        val segmentCount = polyline.size - 1
        val segmentLengths = DoubleArray(segmentCount)
        for (i in 0 until segmentCount) {
            segmentLengths[i] = computeDistanceMeters(
                polyline[i].first, polyline[i].second,
                polyline[i + 1].first, polyline[i + 1].second
            )
        }

        // Find best matching segment on polyline
        var bestSegmentIndex = 0
        var minCrossTrackDist = Double.MAX_VALUE
        var bestFraction = 0.0
        var bestRawFraction = 0.0

        for (i in 0 until segmentCount) {
            val proj = projectPointOntoSegment(
                currentLat, currentLon,
                polyline[i].first, polyline[i].second,
                polyline[i + 1].first, polyline[i + 1].second
            )

            // Add a small hysteresis penalty for segments far ahead of preferredStartIndex
            val indexDelta = abs(i - preferredStartIndex)
            val score = proj.distanceToSegmentMeters + indexDelta * 0.5

            if (score < minCrossTrackDist) {
                minCrossTrackDist = score
                bestSegmentIndex = i
                bestFraction = proj.tClamped
                bestRawFraction = proj.tRaw
            }
        }

        // If matched segment is the very first segment and user is behind the start point
        if (bestSegmentIndex == 0 && bestRawFraction < 0.0) {
            val distToStart = computeDistanceMeters(currentLat, currentLon, polyline[0].first, polyline[0].second)
            var total = distToStart
            for (len in segmentLengths) total += len
            return max(0.0, total)
        }

        // If matched segment is the very last segment and user is past the end point
        if (bestSegmentIndex == segmentCount - 1 && bestRawFraction > 1.0) {
            val distPastEnd = computeDistanceMeters(currentLat, currentLon, polyline.last().first, polyline.last().second)
            return distPastEnd
        }

        // Remaining distance on matched segment: (1 - t) * length(segment)
        var remaining = (1.0 - bestFraction) * segmentLengths[bestSegmentIndex]

        // Sum remaining segments to destination
        for (j in (bestSegmentIndex + 1) until segmentCount) {
            remaining += segmentLengths[j]
        }

        return max(0.0, remaining)
    }

    /**
     * Overload for GeoPoint polyline.
     */
    fun computeRemainingDistanceGeoPoints(
        current: GeoPoint,
        polyline: List<GeoPoint>,
        preferredStartIndex: Int = 0
    ): Float {
        val pairs = polyline.map { Pair(it.latitude, it.longitude) }
        return computeAlongRouteRemainingDistance(
            current.latitude,
            current.longitude,
            pairs,
            preferredStartIndex
        ).toFloat()
    }

    data class PolylineChainage(
        val polyline: List<GeoPoint>,
        val segmentLengths: DoubleArray,
        val cumulativeChainage: DoubleArray,
        val totalLengthMeters: Double
    )

    fun buildChainage(polyline: List<GeoPoint>): PolylineChainage {
        if (polyline.size < 2) {
            return PolylineChainage(polyline, DoubleArray(0), doubleArrayOf(0.0), 0.0)
        }
        val count = polyline.size - 1
        val lengths = DoubleArray(count)
        val cumulative = DoubleArray(polyline.size)
        cumulative[0] = 0.0
        var total = 0.0
        for (i in 0 until count) {
            val d = computeDistanceMeters(
                polyline[i].latitude, polyline[i].longitude,
                polyline[i + 1].latitude, polyline[i + 1].longitude
            )
            lengths[i] = d
            total += d
            cumulative[i + 1] = total
        }
        return PolylineChainage(polyline, lengths, cumulative, total)
    }

    fun matchFixToRoute(
        fix: GeoPoint,
        chainage: PolylineChainage,
        preferredSegmentIndex: Int = 0,
        searchWindowRadius: Int = 5,
        maxCrossTrackMeters: Double = 50.0
    ): dev.navisense.navigation.maps.models.RouteMatch {
        val poly = chainage.polyline
        if (poly.size < 2) {
            return dev.navisense.navigation.maps.models.RouteMatch(0, 0.0, 0.0, 0.0, isValid = false)
        }
        val segmentCount = chainage.segmentLengths.size

        val minWindow = max(0, preferredSegmentIndex - 1)
        val maxWindow = min(segmentCount - 1, preferredSegmentIndex + searchWindowRadius)

        var bestSeg = preferredSegmentIndex.coerceIn(0, segmentCount - 1)
        var bestScore = Double.MAX_VALUE
        var bestProj: SegmentProjection? = null

        fun evaluateSegment(idx: Int) {
            val p = projectPointOntoSegment(
                fix.latitude, fix.longitude,
                poly[idx].latitude, poly[idx].longitude,
                poly[idx + 1].latitude, poly[idx + 1].longitude
            )
            val score = p.distanceToSegmentMeters + abs(idx - preferredSegmentIndex) * 0.5
            if (score < bestScore) {
                bestScore = score
                bestSeg = idx
                bestProj = p
            }
        }

        for (i in minWindow..maxWindow) {
            evaluateSegment(i)
        }

        if ((bestProj?.distanceToSegmentMeters ?: Double.MAX_VALUE) > maxCrossTrackMeters) {
            for (i in 0 until segmentCount) {
                evaluateSegment(i)
            }
        }

        val proj = bestProj ?: return dev.navisense.navigation.maps.models.RouteMatch(0, 0.0, 0.0, 0.0, isValid = false)
        val isCrossTrackValid = proj.distanceToSegmentMeters <= maxCrossTrackMeters

        val matchedChainage = chainage.cumulativeChainage[bestSeg] + proj.tClamped * chainage.segmentLengths[bestSeg]

        return dev.navisense.navigation.maps.models.RouteMatch(
            segmentIndex = bestSeg,
            fraction = proj.tClamped,
            chainageMeters = matchedChainage,
            crossTrackMeters = proj.distanceToSegmentMeters,
            isValid = isCrossTrackValid
        )
    }
}
