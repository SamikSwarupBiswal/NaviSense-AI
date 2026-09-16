package dev.navisense.map

data class MapPoint(
    val lat: Double,
    val lon: Double
)

data class MapPOI(
    val id: String,
    val name: String,
    val category: String,
    val lat: Double,
    val lon: Double,
    val description: String,
    val nearestNodeId: Long,
    val distanceToNodeMeters: Double,
    val aliases: List<String> = emptyList()
)

enum class TurnType {
    START,
    STRAIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    ARRIVE
}

data class Maneuver(
    val turnType: TurnType,
    val instruction: String,
    val roadName: String,
    val distanceMeters: Double,
    val bearingDegrees: Double,
    val waypoint: MapPoint
)

data class NavigationRoute(
    val destination: MapPOI,
    val totalDistanceMeters: Double,
    val maneuvers: List<Maneuver>,
    val polylinePoints: List<MapPoint>
)
