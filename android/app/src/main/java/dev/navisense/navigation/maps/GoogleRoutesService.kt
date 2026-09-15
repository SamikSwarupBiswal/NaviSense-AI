package dev.navisense.navigation.maps

import dev.navisense.navigation.maps.models.GeoPoint
import dev.navisense.navigation.maps.models.ManeuverType
import dev.navisense.navigation.maps.models.WalkingRoute
import dev.navisense.navigation.maps.models.WalkingStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Service for querying Google Routes API (walking mode) and Geocoding API,
 * with resilient offline mock fallback for local testing and zero-network operation.
 */
class GoogleRoutesService(
    private val apiKey: String? = null,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val ROUTES_API_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
        private const val GEOCODING_API_URL = "https://maps.googleapis.com/maps/api/geocode/json"
    }

    /**
     * Resolves a spoken destination query to a GeoPoint via Geocoding API.
     */
    suspend fun geocodeDestination(destinationName: String): Result<GeoPoint> = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) {
            // Stub coordinate for offline demo
            return@withContext Result.success(GeoPoint(12.8442, 80.1549))
        }

        try {
            val encodedQuery = URLEncoder.encode(destinationName, "UTF-8")
            val url = "$GEOCODING_API_URL?address=$encodedQuery&key=$apiKey"
            val request = Request.Builder().url(url).get().build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty geocoding response"))

            val json = JSONObject(body)
            val status = json.optString("status")
            if (status != "OK") {
                return@withContext Result.failure(Exception("Geocoding failed with status: $status"))
            }

            val results = json.getJSONArray("results")
            if (results.length() == 0) {
                return@withContext Result.failure(Exception("Destination not found"))
            }

            val location = results.getJSONObject(0)
                .getJSONObject("geometry")
                .getJSONObject("location")

            val lat = location.getDouble("lat")
            val lng = location.getDouble("lng")
            Result.success(GeoPoint(lat, lng))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Computes pedestrian walking route between origin and destination using Google Routes API v2.
     */
    suspend fun computeWalkingRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String
    ): Result<WalkingRoute> = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrBlank()) {
            return@withContext Result.success(createMockWalkingRoute(origin, destination, destinationName))
        }

        try {
            val requestPayload = JSONObject().apply {
                put("origin", JSONObject().put("location", JSONObject().put("latLng", JSONObject().apply {
                    put("latitude", origin.latitude)
                    put("longitude", origin.longitude)
                })))
                put("destination", JSONObject().put("location", JSONObject().put("latLng", JSONObject().apply {
                    put("latitude", destination.latitude)
                    put("longitude", destination.longitude)
                })))
                put("travelMode", "WALK")
                put("routingPreference", "ROUTING_PREFERENCE_UNSPECIFIED")
                put("computeAlternativeRoutes", false)
                put("languageCode", "en-US")
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestPayload.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(ROUTES_API_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Goog-Api-Key", apiKey)
                .addHeader("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.legs.steps")
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()
                ?: return@withContext Result.failure(Exception("Empty routes response"))

            val json = JSONObject(responseBody)
            val routesArray = json.optJSONArray("routes")
            if (routesArray == null || routesArray.length() == 0) {
                return@withContext Result.failure(Exception("No walking routes found"))
            }

            val routeJson = routesArray.getJSONObject(0)
            val totalDistance = routeJson.optInt("distanceMeters", 0)
            val durationString = routeJson.optString("duration", "0s")
            val totalDuration = durationString.removeSuffix("s").toIntOrNull() ?: 0

            val stepsList = mutableListOf<WalkingStep>()
            val legs = routeJson.optJSONArray("legs")
            if (legs != null && legs.length() > 0) {
                val leg = legs.getJSONObject(0)
                val steps = leg.optJSONArray("steps") ?: JSONArray()

                for (i in 0 until steps.length()) {
                    val stepJson = steps.getJSONObject(i)
                    val distanceMeters = stepJson.optInt("distanceMeters", 0)
                    val stepDuration = stepJson.optString("staticDuration", "0s").removeSuffix("s").toIntOrNull() ?: 0

                    val navInstruction = stepJson.optJSONObject("navigationInstruction")
                    val instruction = navInstruction?.optString("instructions") ?: "Continue"
                    val rawManeuver = navInstruction?.optString("maneuver")
                    val maneuver = ManeuverType.fromGoogleManeuver(rawManeuver)

                    val startLoc = parseLocation(stepJson.optJSONObject("startLocation")) ?: origin
                    val endLoc = parseLocation(stepJson.optJSONObject("endLocation")) ?: destination

                    val stepPolyline = stepJson.optJSONObject("polyline")?.optString("encodedPolyline") ?: ""
                    val decodedPoints = decodePolyline(stepPolyline)

                    stepsList.add(
                        WalkingStep(
                            instruction = cleanHtmlInstructions(instruction),
                            maneuver = maneuver,
                            distanceMeters = distanceMeters,
                            durationSeconds = stepDuration,
                            startLocation = startLoc,
                            endLocation = endLoc,
                            polylinePoints = decodedPoints
                        )
                    )
                }
            }

            val overviewEncoded = routeJson.optJSONObject("polyline")?.optString("encodedPolyline") ?: ""
            val overviewPoints = decodePolyline(overviewEncoded)

            Result.success(
                WalkingRoute(
                    destinationName = destinationName,
                    totalDistanceMeters = totalDistance,
                    totalDurationSeconds = totalDuration,
                    steps = stepsList,
                    overviewPolyline = overviewPoints
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseLocation(locJson: JSONObject?): GeoPoint? {
        if (locJson == null) return null
        val latLng = locJson.optJSONObject("latLng") ?: return null
        val lat = latLng.optDouble("latitude", Double.NaN)
        val lng = latLng.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lng.isNaN()) return null
        return GeoPoint(lat, lng)
    }

    /**
     * Decodes an encoded path string into a list of GeoPoints using standard Google polyline algorithm.
     */
    fun decodePolyline(encoded: String): List<GeoPoint> {
        val poly = mutableListOf<GeoPoint>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = GeoPoint(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }

    private fun cleanHtmlInstructions(html: String): String {
        return html.replace(Regex("<[^>]*>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Creates a realistic pedestrian walking test route for testing and offline development.
     */
    fun createMockWalkingRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String
    ): WalkingRoute {
        val step1End = GeoPoint(origin.latitude + 0.0003, origin.longitude)
        val step2End = GeoPoint(step1End.latitude, step1End.longitude + 0.0004)
        val step3End = destination

        val step1 = WalkingStep(
            instruction = "Walk straight along the walkway",
            maneuver = ManeuverType.DEPART,
            distanceMeters = 35,
            durationSeconds = 25,
            startLocation = origin,
            endLocation = step1End,
            polylinePoints = listOf(origin, step1End)
        )

        val step2 = WalkingStep(
            instruction = "Turn right onto the pedestrian path",
            maneuver = ManeuverType.RIGHT,
            distanceMeters = 45,
            durationSeconds = 35,
            startLocation = step1End,
            endLocation = step2End,
            polylinePoints = listOf(step1End, step2End)
        )

        val step3 = WalkingStep(
            instruction = "Turn left towards $destinationName",
            maneuver = ManeuverType.LEFT,
            distanceMeters = 40,
            durationSeconds = 30,
            startLocation = step2End,
            endLocation = step3End,
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
