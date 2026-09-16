package dev.navisense.navigation.maps

import android.content.Context
import android.location.Geocoder
import dev.navisense.navigation.maps.models.DestinationNotFoundException
import dev.navisense.navigation.maps.models.GeoPoint
import dev.navisense.navigation.maps.models.ManeuverType
import dev.navisense.navigation.maps.models.RouteNotFoundException
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
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Service for querying Google Directions & Routes API (walking mode), live pedestrian router (OSRM),
 * and Android native Geocoder for real-world street names and turn-by-turn guidance.
 */
class GoogleRoutesService(
    private val apiKey: String? = null,
    private val context: Context? = null,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val DIRECTIONS_API_URL = "https://maps.googleapis.com/maps/api/directions/json"
        private const val ROUTES_API_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
        private const val GEOCODING_API_URL = "https://maps.googleapis.com/maps/api/geocode/json"
        private const val OSRM_WALKING_URL = "https://router.project-osrm.org/route/v1/walking"
        private const val USER_AGENT = "NaviSense/1.0 (Android Pedestrian Assist)"
    }

    /**
     * Resolves the real-world street or road name for a coordinate using Android Geocoder.
     */
    fun resolveStreetName(point: GeoPoint): String {
        val ctx = context
        if (ctx != null && Geocoder.isPresent()) {
            try {
                val geocoder = Geocoder(ctx, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                val addr = addresses?.firstOrNull()
                val street = addr?.thoroughfare ?: addr?.subLocality ?: addr?.featureName
                if (!street.isNullOrBlank()) return street
            } catch (_: Exception) {}
        }
        return ""
    }

    /**
     * Resolves a spoken destination query to a GeoPoint via Geocoding API or Android Geocoder.
     */
    suspend fun geocodeDestination(destinationName: String, nearPoint: GeoPoint? = null): Result<GeoPoint> = withContext(Dispatchers.IO) {
        if (!apiKey.isNullOrBlank()) {
            try {
                val encodedQuery = URLEncoder.encode(destinationName, "UTF-8")
                val biasParam = if (nearPoint != null) "&bounds=${nearPoint.latitude - 0.2},${nearPoint.longitude - 0.2}|${nearPoint.latitude + 0.2},${nearPoint.longitude + 0.2}" else ""
                val url = "$GEOCODING_API_URL?address=$encodedQuery$biasParam&key=$apiKey"
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string()
                if (body != null) {
                    val json = JSONObject(body)
                    if (json.optString("status") == "OK") {
                        val results = json.getJSONArray("results")
                        if (results.length() > 0) {
                            val location = results.getJSONObject(0)
                                .getJSONObject("geometry")
                                .getJSONObject("location")
                            val lat = location.getDouble("lat")
                            val lng = location.getDouble("lng")
                            return@withContext Result.success(GeoPoint(lat, lng))
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // Native Android OS Geocoder fallback
        val ctx = context
        if (ctx != null && Geocoder.isPresent()) {
            try {
                val geocoder = Geocoder(ctx, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = if (nearPoint != null) {
                    geocoder.getFromLocationName(
                        destinationName,
                        5,
                        nearPoint.latitude - 0.25,
                        nearPoint.longitude - 0.25,
                        nearPoint.latitude + 0.25,
                        nearPoint.longitude + 0.25
                    )?.takeIf { it.isNotEmpty() } ?: geocoder.getFromLocationName(destinationName, 1)
                } else {
                    geocoder.getFromLocationName(destinationName, 1)
                }
                val match = addresses?.firstOrNull()
                if (match != null) {
                    return@withContext Result.success(GeoPoint(match.latitude, match.longitude))
                }
            } catch (_: Exception) {}
        }

        // OpenStreetMap Nominatim Geocoding fallback
        try {
            val encodedQuery = URLEncoder.encode(destinationName, "UTF-8")
            val url = if (nearPoint != null) {
                "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=1&viewbox=${nearPoint.longitude - 0.5},${nearPoint.latitude + 0.5},${nearPoint.longitude + 0.5},${nearPoint.latitude - 0.5}"
            } else {
                "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=1"
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()
            if (body != null && body.startsWith("[")) {
                val arr = JSONArray(body)
                if (arr.length() > 0) {
                    val item = arr.getJSONObject(0)
                    val lat = item.getDouble("lat")
                    val lon = item.getDouble("lon")
                    return@withContext Result.success(GeoPoint(lat, lon))
                }
            }
        } catch (_: Exception) {}

        Result.failure(DestinationNotFoundException("Destination '$destinationName' not found"))
    }

    /**
     * Computes pedestrian walking route between origin and destination.
     * Prioritizes Google Directions API (walking mode) if apiKey is available,
     * then Google Routes API v2, and then live OSRM walking router.
     */
    suspend fun computeWalkingRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String
    ): Result<WalkingRoute> = withContext(Dispatchers.IO) {
        if (!apiKey.isNullOrBlank()) {
            val directionsResult = fetchGoogleDirections(origin, destination, destinationName)
            if (directionsResult.isSuccess) {
                return@withContext directionsResult
            }
            val routesResult = fetchGoogleRoutes(origin, destination, destinationName)
            if (routesResult.isSuccess) {
                return@withContext routesResult
            }
        }

        // Live OpenStreetMap walking router (free, public, pedestrian paths)
        val osrmResult = fetchOsrmWalkingRoute(origin, destination, destinationName)
        if (osrmResult.isSuccess) {
            return@withContext osrmResult
        }

        Result.failure(RouteNotFoundException("Unable to compute walking route to $destinationName"))
    }

    private fun fetchGoogleDirections(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String
    ): Result<WalkingRoute> {
        val key = apiKey ?: return Result.failure(Exception("No API key provided"))
        return try {
            val url = "$DIRECTIONS_API_URL?origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}&mode=walking&key=$key"
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return Result.failure(Exception("Empty Directions API response"))
            val json = JSONObject(body)

            val status = json.optString("status")
            if (status != "OK") {
                val errMsg = json.optString("error_message", "Google Directions error: $status")
                return Result.failure(Exception(errMsg))
            }

            val routes = json.getJSONArray("routes")
            if (routes.length() == 0) return Result.failure(Exception("No routes found"))
            val routeObj = routes.getJSONObject(0)
            val legs = routeObj.getJSONArray("legs")
            if (legs.length() == 0) return Result.failure(Exception("No legs in route"))
            val leg = legs.getJSONObject(0)

            val totalDistance = leg.getJSONObject("distance").getInt("value")
            val totalDuration = leg.getJSONObject("duration").getInt("value")
            val overviewPoints = decodePolyline(routeObj.getJSONObject("overview_polyline").getString("points"))

            val stepsList = mutableListOf<WalkingStep>()
            val steps = leg.getJSONArray("steps")
            for (i in 0 until steps.length()) {
                val stepObj = steps.getJSONObject(i)
                val dist = stepObj.getJSONObject("distance").getInt("value")
                val dur = stepObj.getJSONObject("duration").getInt("value")
                val html = stepObj.getString("html_instructions")
                val cleanInstruction = cleanHtmlInstructions(html)
                val rawManeuver = stepObj.optString("maneuver", "")
                val maneuver = ManeuverType.fromGoogleManeuver(rawManeuver)
                val startLoc = parseLocationLatLng(stepObj.getJSONObject("start_location")) ?: origin
                val endLoc = parseLocationLatLng(stepObj.getJSONObject("end_location")) ?: destination
                val stepPolyline = decodePolyline(stepObj.getJSONObject("polyline").getString("points"))
                val street = extractStreetFromInstruction(cleanInstruction)

                stepsList.add(
                    WalkingStep(
                        instruction = cleanInstruction,
                        maneuver = maneuver,
                        distanceMeters = dist,
                        durationSeconds = dur,
                        startLocation = startLoc,
                        endLocation = endLoc,
                        streetName = street,
                        polylinePoints = stepPolyline
                    )
                )
            }

            if (stepsList.isEmpty()) {
                return Result.failure(Exception("No steps in Google Directions route"))
            }

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

    private fun fetchGoogleRoutes(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String
    ): Result<WalkingRoute> {
        return try {
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
                .addHeader("X-Goog-Api-Key", apiKey!!)
                .addHeader("X-Goog-FieldMask", "routes.duration,routes.distanceMeters,routes.polyline.encodedPolyline,routes.legs.steps")
                .header("User-Agent", USER_AGENT)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty routes response"))

            val json = JSONObject(responseBody)
            val routesArray = json.optJSONArray("routes")
            if (routesArray == null || routesArray.length() == 0) {
                return Result.failure(Exception("No walking routes found"))
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
                    val street = extractStreetFromInstruction(instruction)

                    stepsList.add(
                        WalkingStep(
                            instruction = cleanHtmlInstructions(instruction),
                            maneuver = maneuver,
                            distanceMeters = distanceMeters,
                            durationSeconds = stepDuration,
                            startLocation = startLoc,
                            endLocation = endLoc,
                            streetName = street,
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

    private fun fetchOsrmWalkingRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        destinationName: String
    ): Result<WalkingRoute> {
        return try {
            val url = "$OSRM_WALKING_URL/${origin.longitude},${origin.latitude};${destination.longitude},${destination.latitude}?steps=true&overview=full&geometries=polyline"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string() ?: return Result.failure(Exception("Empty OSRM response"))
            val json = JSONObject(body)
            if (json.optString("code") != "Ok") {
                return Result.failure(Exception("OSRM returned code: ${json.optString("code")}"))
            }
            val routes = json.getJSONArray("routes")
            if (routes.length() == 0) return Result.failure(Exception("No OSRM routes found"))

            val routeJson = routes.getJSONObject(0)
            val totalDistance = routeJson.optDouble("distance", 0.0).toInt()
            val totalDuration = routeJson.optDouble("duration", 0.0).toInt()
            val overviewPoints = decodePolyline(routeJson.optString("geometry", ""))

            val stepsList = mutableListOf<WalkingStep>()
            val legs = routeJson.optJSONArray("legs")
            if (legs != null && legs.length() > 0) {
                val leg = legs.getJSONObject(0)
                val steps = leg.optJSONArray("steps") ?: JSONArray()

                for (i in 0 until steps.length()) {
                    val stepJson = steps.getJSONObject(i)
                    val distanceMeters = stepJson.optDouble("distance", 0.0).toInt()
                    val durationSeconds = stepJson.optDouble("duration", 0.0).toInt()
                    var street = stepJson.optString("name", "").trim()

                    val stepPoints = decodePolyline(stepJson.optString("geometry", ""))
                    val startLoc = stepPoints.firstOrNull() ?: origin
                    val endLoc = stepPoints.lastOrNull() ?: destination

                    if (street.isEmpty()) {
                        val resolved = resolveStreetName(startLoc)
                        street = if (resolved.isNotBlank()) resolved else "the walkway"
                    }

                    val maneuverObj = stepJson.optJSONObject("maneuver")
                    val type = maneuverObj?.optString("type") ?: ""
                    val modifier = maneuverObj?.optString("modifier") ?: ""
                    val maneuver = mapOsrmManeuver(type, modifier)

                    val instruction = buildInstruction(maneuver, street, destinationName)

                    stepsList.add(
                        WalkingStep(
                            instruction = instruction,
                            maneuver = maneuver,
                            distanceMeters = distanceMeters,
                            durationSeconds = durationSeconds,
                            startLocation = startLoc,
                            endLocation = endLoc,
                            streetName = street,
                            polylinePoints = stepPoints
                        )
                    )
                }
            }

            if (stepsList.isEmpty()) {
                return Result.failure(Exception("No steps in OSRM route"))
            }

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

    private fun mapOsrmManeuver(type: String, modifier: String): ManeuverType {
        return when (type) {
            "depart" -> ManeuverType.DEPART
            "arrive" -> ManeuverType.ARRIVE
            else -> when (modifier.lowercase()) {
                "right" -> ManeuverType.RIGHT
                "slight right" -> ManeuverType.SLIGHT_RIGHT
                "sharp right" -> ManeuverType.SHARP_RIGHT
                "left" -> ManeuverType.LEFT
                "slight left" -> ManeuverType.SLIGHT_LEFT
                "sharp left" -> ManeuverType.SHARP_LEFT
                "u-turn", "uturn" -> ManeuverType.U_TURN
                else -> ManeuverType.STRAIGHT
            }
        }
    }

    private fun buildInstruction(maneuver: ManeuverType, street: String, destinationName: String): String {
        return when (maneuver) {
            ManeuverType.DEPART -> "Head forward on $street"
            ManeuverType.ARRIVE -> "Arrive at $destinationName"
            ManeuverType.RIGHT -> "Turn right onto $street"
            ManeuverType.SLIGHT_RIGHT -> "Bear slight right onto $street"
            ManeuverType.SHARP_RIGHT -> "Turn sharp right onto $street"
            ManeuverType.LEFT -> "Turn left onto $street"
            ManeuverType.SLIGHT_LEFT -> "Bear slight left onto $street"
            ManeuverType.SHARP_LEFT -> "Turn sharp left onto $street"
            ManeuverType.U_TURN -> "Make a U-turn onto $street"
            else -> "Continue straight on $street"
        }
    }

    private fun extractStreetFromInstruction(instruction: String): String {
        val ontoIdx = instruction.indexOf("onto ", ignoreCase = true)
        if (ontoIdx != -1) {
            return instruction.substring(ontoIdx + 5).trim()
        }
        val onIdx = instruction.indexOf("on ", ignoreCase = true)
        if (onIdx != -1) {
            return instruction.substring(onIdx + 3).trim()
        }
        return instruction
    }

    private fun parseLocation(locJson: JSONObject?): GeoPoint? {
        if (locJson == null) return null
        val latLng = locJson.optJSONObject("latLng") ?: return null
        val lat = latLng.optDouble("latitude", Double.NaN)
        val lng = latLng.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lng.isNaN()) return null
        return GeoPoint(lat, lng)
    }

    private fun parseLocationLatLng(locJson: JSONObject?): GeoPoint? {
        if (locJson == null) return null
        val lat = locJson.optDouble("lat", Double.NaN)
        val lng = locJson.optDouble("lng", Double.NaN)
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
}
