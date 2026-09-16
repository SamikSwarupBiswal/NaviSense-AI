package dev.navisense.map

import org.json.JSONObject
import java.io.InputStream
import java.util.PriorityQueue
import kotlin.math.*

/**
 * Offline pedestrian routing engine for VIT Chennai campus using A* pathfinding.
 */
class MapRoutingEngine private constructor(
    val pois: List<MapPOI>,
    private val nodes: Map<Long, MapPoint>,
    private val adjacency: Map<Long, List<GraphEdge>>
) {
    data class GraphEdge(
        val toNodeId: Long,
        val distanceMeters: Double,
        val roadName: String
    )

    private data class AStarNode(
        val nodeId: Long,
        val costFromStart: Double,
        val estimatedTotalCost: Double
    ) : Comparable<AStarNode> {
        override fun compareTo(other: AStarNode): Int =
            estimatedTotalCost.compareTo(other.estimatedTotalCost)
    }

    fun getPoi(id: String): MapPOI? = pois.find { it.id == id }

    fun findNearestNode(lat: Double, lon: Double, maxDistanceMeters: Double = DEFAULT_MAX_SNAP_DISTANCE_METERS): Long? {
        var closestId: Long? = null
        var minDistance = Double.MAX_VALUE
        for ((nodeId, point) in nodes) {
            val dist = computeDistanceMeters(lat, lon, point.lat, point.lon)
            if (dist < minDistance) {
                minDistance = dist
                closestId = nodeId
            }
        }
        return if (minDistance <= maxDistanceMeters) closestId else null
    }

    /**
     * Finds shortest pedestrian route from current location to target POI.
     */
    fun planRoute(
        startLat: Double,
        startLon: Double,
        destinationPoiId: String,
        maxSnapDistanceMeters: Double = DEFAULT_MAX_SNAP_DISTANCE_METERS
    ): NavigationRoute? {
        val destination = getPoi(destinationPoiId) ?: return null
        val startNodeId = findNearestNode(startLat, startLon, maxSnapDistanceMeters) ?: return null
        val targetNodeId = destination.nearestNodeId

        if (startNodeId == targetNodeId) {
            val destPoint = nodes[targetNodeId] ?: MapPoint(destination.lat, destination.lon)
            val dist = computeDistanceMeters(startLat, startLon, destPoint.lat, destPoint.lon)
            return NavigationRoute(
                destination = destination,
                totalDistanceMeters = dist,
                maneuvers = listOf(
                    Maneuver(
                        turnType = TurnType.ARRIVE,
                        instruction = "You have arrived at ${destination.name}",
                        roadName = destination.name,
                        distanceMeters = dist,
                        bearingDegrees = computeBearingDegrees(startLat, startLon, destPoint.lat, destPoint.lon),
                        waypoint = destPoint
                    )
                ),
                polylinePoints = listOf(MapPoint(startLat, startLon), destPoint)
            )
        }

        // A* Pathfinding
        val openSet = PriorityQueue<AStarNode>()
        val gScore = mutableMapOf<Long, Double>().withDefault { Double.MAX_VALUE }
        val cameFrom = mutableMapOf<Long, Pair<Long, GraphEdge>>()

        val targetPoint = nodes[targetNodeId] ?: return null
        gScore[startNodeId] = 0.0
        val initialHeuristic = computeDistanceMeters(
            nodes[startNodeId]!!.lat, nodes[startNodeId]!!.lon,
            targetPoint.lat, targetPoint.lon
        )
        openSet.add(AStarNode(startNodeId, 0.0, initialHeuristic))

        val visited = mutableSetOf<Long>()

        while (openSet.isNotEmpty()) {
            val current = openSet.poll() ?: break
            val currNodeId = current.nodeId

            if (currNodeId == targetNodeId) {
                return reconstructRoute(startLat, startLon, targetNodeId, destination, cameFrom)
            }

            if (currNodeId in visited) continue
            visited.add(currNodeId)

            val neighbors = adjacency[currNodeId] ?: emptyList()
            for (edge in neighbors) {
                val neighborId = edge.toNodeId
                if (neighborId in visited) continue

                val tentativeG = gScore.getValue(currNodeId) + edge.distanceMeters
                if (tentativeG < gScore.getValue(neighborId)) {
                    cameFrom[neighborId] = Pair(currNodeId, edge)
                    gScore[neighborId] = tentativeG
                    val neighborPoint = nodes[neighborId]
                    val h = if (neighborPoint != null) {
                        computeDistanceMeters(neighborPoint.lat, neighborPoint.lon, targetPoint.lat, targetPoint.lon)
                    } else 0.0
                    openSet.add(AStarNode(neighborId, tentativeG, tentativeG + h))
                }
            }
        }

        return null // No path found
    }

    private fun reconstructRoute(
        startLat: Double,
        startLon: Double,
        targetNodeId: Long,
        destination: MapPOI,
        cameFrom: Map<Long, Pair<Long, GraphEdge>>
    ): NavigationRoute {
        val pathNodes = mutableListOf<Long>()
        val pathEdges = mutableListOf<GraphEdge>()
        var curr = targetNodeId

        while (curr in cameFrom) {
            pathNodes.add(curr)
            val (prevNode, edge) = cameFrom[curr]!!
            pathEdges.add(edge)
            curr = prevNode
        }
        pathNodes.add(curr) // startNodeId
        pathNodes.reverse()
        pathEdges.reverse()

        val points = mutableListOf<MapPoint>()
        val startPoint = MapPoint(startLat, startLon)
        val firstGraphNode = nodes[pathNodes.first()] ?: startPoint
        val originConnectorDist = computeDistanceMeters(startPoint.lat, startPoint.lon, firstGraphNode.lat, firstGraphNode.lon)

        // Include start coordinate connector if distinct from first graph node
        if (originConnectorDist > 0.5) {
            points.add(startPoint)
        }
        for (nid in pathNodes) {
            nodes[nid]?.let { points.add(it) }
        }

        // Include destination coordinate connector if distinct from last graph node
        val destPoint = MapPoint(destination.lat, destination.lon)
        val lastGraphNode = nodes[pathNodes.last()] ?: destPoint
        val destConnectorDist = computeDistanceMeters(lastGraphNode.lat, lastGraphNode.lon, destPoint.lat, destPoint.lon)
        if (destConnectorDist > 0.5) {
            points.add(destPoint)
        }

        var totalDist = originConnectorDist + destConnectorDist
        for (edge in pathEdges) {
            totalDist += edge.distanceMeters
        }

        val maneuvers = mutableListOf<Maneuver>()

        if (pathEdges.isNotEmpty()) {
            var currentRoadName = pathEdges.first().roadName.ifBlank { "Campus Walkway" }
            var currentSegmentDist = originConnectorDist

            for (i in 0 until pathEdges.size) {
                val edge = pathEdges[i]
                currentSegmentDist += edge.distanceMeters

                val nodeA = nodes[pathNodes[i]]
                val nodeB = nodes[pathNodes[i + 1]]

                val edgeBearing = if (nodeA != null && nodeB != null) {
                    computeBearingDegrees(nodeA.lat, nodeA.lon, nodeB.lat, nodeB.lon)
                } else 0.0

                // Generate initial departure maneuver if needed
                if (maneuvers.isEmpty()) {
                    val initialBearing = if (originConnectorDist > 0.5 && nodeA != null) {
                        computeBearingDegrees(startLat, startLon, nodeA.lat, nodeA.lon)
                    } else edgeBearing

                    maneuvers.add(
                        Maneuver(
                            turnType = TurnType.START,
                            instruction = "Head ${bearingToCardinal(initialBearing)} on $currentRoadName for ${currentSegmentDist.roundToInt()} meters",
                            roadName = currentRoadName,
                            distanceMeters = currentSegmentDist,
                            bearingDegrees = initialBearing,
                            waypoint = nodeB ?: firstGraphNode
                        )
                    )
                    currentSegmentDist = 0.0
                }

                // Check for turn between edge i and edge i + 1
                if (i < pathEdges.size - 1) {
                    val nextEdge = pathEdges[i + 1]
                    val nodeC = nodes[pathNodes[i + 2]]

                    if (nodeA != null && nodeB != null && nodeC != null) {
                        val b1 = computeBearingDegrees(nodeA.lat, nodeA.lon, nodeB.lat, nodeB.lon)
                        val b2 = computeBearingDegrees(nodeB.lat, nodeB.lon, nodeC.lat, nodeC.lon)
                        val turnAngle = normalizeAngleDelta(b2 - b1)
                        val isSignificantTurn = abs(turnAngle) >= 35.0 || edge.roadName != nextEdge.roadName

                        if (isSignificantTurn) {
                            val turnType = when {
                                turnAngle in -60.0..-35.0 -> TurnType.SLIGHT_LEFT
                                turnAngle in -120.0..-60.0 -> TurnType.LEFT
                                turnAngle < -120.0 -> TurnType.SHARP_LEFT
                                turnAngle in 35.0..60.0 -> TurnType.SLIGHT_RIGHT
                                turnAngle in 60.0..120.0 -> TurnType.RIGHT
                                turnAngle > 120.0 -> TurnType.SHARP_RIGHT
                                else -> TurnType.STRAIGHT
                            }

                            val instruction = "${turnTypeToSpeech(turnType)} onto ${nextEdge.roadName}, continue for ${currentSegmentDist.roundToInt()} meters"
                            maneuvers.add(
                                Maneuver(
                                    turnType = turnType,
                                    instruction = instruction,
                                    roadName = currentRoadName,
                                    distanceMeters = currentSegmentDist,
                                    bearingDegrees = b1,
                                    waypoint = nodeB
                                )
                            )

                            currentRoadName = nextEdge.roadName
                            currentSegmentDist = 0.0
                        }
                    }
                }
            }

            // Final arrival maneuver
            currentSegmentDist += destConnectorDist
            val lastBearing = if (points.size >= 2) {
                computeBearingDegrees(
                    points[points.size - 2].lat, points[points.size - 2].lon,
                    points.last().lat, points.last().lon
                )
            } else 0.0

            maneuvers.add(
                Maneuver(
                    turnType = TurnType.ARRIVE,
                    instruction = "Arrive at ${destination.name}",
                    roadName = destination.name,
                    distanceMeters = currentSegmentDist,
                    bearingDegrees = lastBearing,
                    waypoint = points.last()
                )
            )
        }

        return NavigationRoute(
            destination = destination,
            totalDistanceMeters = totalDist,
            maneuvers = maneuvers,
            polylinePoints = points
        )
    }

    companion object {
        const val DEFAULT_MAX_SNAP_DISTANCE_METERS = 80.0
        private const val EARTH_RADIUS_METERS = 6371000.0

        fun loadFromStream(inputStream: InputStream): MapRoutingEngine {
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)

            // POIs
            val poisList = mutableListOf<MapPOI>()
            val poisArray = root.optJSONArray("pois")
            if (poisArray != null) {
                for (i in 0 until poisArray.length()) {
                    val obj = poisArray.getJSONObject(i)
                    poisList.add(
                        MapPOI(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            category = obj.optString("category", "general"),
                            lat = obj.getDouble("lat"),
                            lon = obj.getDouble("lon"),
                            description = obj.optString("description", ""),
                            nearestNodeId = obj.optLong("nearestNodeId", 0L),
                            distanceToNodeMeters = obj.optDouble("distanceToNodeMeters", 0.0)
                        )
                    )
                }
            }

            // Nodes
            val nodeMap = mutableMapOf<Long, MapPoint>()
            val nodesArray = root.getJSONArray("nodes")
            for (i in 0 until nodesArray.length()) {
                val n = nodesArray.getJSONObject(i)
                nodeMap[n.getLong("id")] = MapPoint(n.getDouble("lat"), n.getDouble("lon"))
            }

            // Edges / Adjacency
            val adjMap = mutableMapOf<Long, MutableList<GraphEdge>>()
            val edgesArray = root.getJSONArray("edges")
            for (i in 0 until edgesArray.length()) {
                val e = edgesArray.getJSONObject(i)
                val from = e.getLong("from")
                val to = e.getLong("to")
                val dist = e.getDouble("distanceMeters")
                val road = e.optString("roadName", "Walkway")

                adjMap.computeIfAbsent(from) { mutableListOf() }
                    .add(GraphEdge(to, dist, road))
            }

            return MapRoutingEngine(poisList, nodeMap, adjMap)
        }

        fun computeDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2.0) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2.0)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }

        fun computeBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val phi1 = Math.toRadians(lat1)
            val phi2 = Math.toRadians(lat2)
            val deltaLambda = Math.toRadians(lon2 - lon1)
            val y = sin(deltaLambda) * cos(phi2)
            val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
            val initialBearing = Math.toDegrees(atan2(y, x))
            return (initialBearing + 360.0) % 360.0
        }

        fun normalizeAngleDelta(angle: Double): Double {
            var a = angle % 360.0
            if (a > 180.0) a -= 360.0
            if (a < -180.0) a += 360.0
            return a
        }

        fun bearingToCardinal(bearing: Double): String = when (bearing) {
            in 22.5..67.5 -> "North-East"
            in 67.5..112.5 -> "East"
            in 112.5..157.5 -> "South-East"
            in 157.5..202.5 -> "South"
            in 202.5..247.5 -> "South-West"
            in 247.5..292.5 -> "West"
            in 292.5..337.5 -> "North-West"
            else -> "North"
        }

        fun turnTypeToSpeech(turn: TurnType): String = when (turn) {
            TurnType.SLIGHT_LEFT -> "Turn slight left"
            TurnType.LEFT -> "Turn left"
            TurnType.SHARP_LEFT -> "Make a sharp left turn"
            TurnType.SLIGHT_RIGHT -> "Turn slight right"
            TurnType.RIGHT -> "Turn right"
            TurnType.SHARP_RIGHT -> "Make a sharp right turn"
            TurnType.STRAIGHT -> "Continue straight"
            TurnType.START -> "Start walking"
            TurnType.ARRIVE -> "Arrived at destination"
        }
    }
}
