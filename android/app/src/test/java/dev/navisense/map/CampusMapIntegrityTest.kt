package dev.navisense.map

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class CampusMapIntegrityTest {

    private lateinit var routingEngine: MapRoutingEngine

    @Before
    fun setUp() {
        val mapFile = File("src/main/assets/maps/vit_chennai_map.json")
        assertTrue("vit_chennai_map.json asset must exist", mapFile.exists())
        routingEngine = FileInputStream(mapFile).use {
            MapRoutingEngine.loadFromStream(it)
        }
    }

    @Test
    fun testAll11PoisLoadedWithValidCoordinates() {
        assertEquals("Campus map must contain 11 canonical POIs", 11, routingEngine.pois.size)

        for (poi in routingEngine.pois) {
            assertTrue("POI ${poi.id} latitude must be in VIT Chennai region", poi.lat in 12.80..12.87)
            assertTrue("POI ${poi.id} longitude must be in VIT Chennai region", poi.lon in 80.14..80.17)
            assertTrue("POI ${poi.id} distance to node must be positive", poi.distanceToNodeMeters > 0.0)
            assertTrue("POI ${poi.id} nearestNodeId must be non-zero", poi.nearestNodeId > 0L)
        }
    }

    @Test
    fun testAll110DirectedPairsRouteSuccessfully() {
        val pois = routingEngine.pois
        var testedPairsCount = 0
        var successfulRoutesCount = 0

        for (origin in pois) {
            for (dest in pois) {
                if (origin.id == dest.id) continue
                testedPairsCount++

                val route = routingEngine.planRoute(origin.lat, origin.lon, dest.id, maxSnapDistanceMeters = 100.0)
                assertNotNull(
                    "Route from ${origin.id} (${origin.name}) to ${dest.id} (${dest.name}) must not be null",
                    route
                )

                if (route != null) {
                    successfulRoutesCount++
                    assertTrue(
                        "Route distance from ${origin.id} to ${dest.id} must be > 0",
                        route.totalDistanceMeters > 0.0
                    )
                    assertTrue(
                        "Route polyline from ${origin.id} to ${dest.id} must have >= 2 points",
                        route.polylinePoints.size >= 2
                    )
                    assertTrue(
                        "Route maneuvers from ${origin.id} to ${dest.id} must not be empty",
                        route.maneuvers.isNotEmpty()
                    )
                    assertEquals(
                        "Final maneuver must be ARRIVE for ${dest.id}",
                        TurnType.ARRIVE,
                        route.maneuvers.last().turnType
                    )
                }
            }
        }

        assertEquals("Total tested pairs must equal 11 * 10 = 110", 110, testedPairsCount)
        assertEquals("All 110 directed pairs must compute a valid connected route", 110, successfulRoutesCount)
    }

    @Test
    fun testSamePoiArrivalRoute() {
        val ab1 = routingEngine.getPoi("poi_academic_block_1")!!
        val route = routingEngine.planRoute(ab1.lat, ab1.lon, ab1.id)
        assertNotNull(route)
        assertEquals(TurnType.ARRIVE, route!!.maneuvers.first().turnType)
        assertTrue(route.maneuvers.first().instruction.contains(ab1.name))
    }
}
