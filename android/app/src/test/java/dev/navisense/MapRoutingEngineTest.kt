package dev.navisense

import dev.navisense.map.MapRoutingEngine
import dev.navisense.map.TurnType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class MapRoutingEngineTest {

    private lateinit var routingEngine: MapRoutingEngine

    @Before
    fun setUp() {
        val mapFile = File("src/main/assets/maps/vit_chennai_map.json")
        assertTrue("Map asset file must exist", mapFile.exists())
        routingEngine = FileInputStream(mapFile).use {
            MapRoutingEngine.loadFromStream(it)
        }
    }

    @Test
    fun testMapDataLoadedSuccessfully() {
        assertNotNull("Routing engine should not be null", routingEngine)
        assertTrue("Must have POIs loaded", routingEngine.pois.isNotEmpty())
        val library = routingEngine.getPoi("poi_library")
        assertNotNull("Central Library POI should exist", library)
        assertEquals("Central Library", library?.name)
    }

    @Test
    fun testPlanRouteFromMainGateToLibrary() {
        // VIT Chennai Main Gate coordinates
        val startLat = 12.8407
        val startLon = 80.1534

        val route = routingEngine.planRoute(startLat, startLon, "poi_library")
        assertNotNull("Route to Central Library should be found", route)
        assertTrue("Route distance should be greater than 0", route!!.totalDistanceMeters > 0.0)
        assertTrue("Route should contain maneuvers", route.maneuvers.isNotEmpty())

        val lastManeuver = route.maneuvers.last()
        assertEquals(TurnType.ARRIVE, lastManeuver.turnType)
        assertTrue(lastManeuver.instruction.contains("Central Library"))
    }

    @Test
    fun testPlanRouteToAcademicBlock() {
        val startLat = 12.8407
        val startLon = 80.1534

        val route = routingEngine.planRoute(startLat, startLon, "poi_academic_block_1")
        assertNotNull("Route to Academic Block 1 should be found", route)
        assertTrue("Total distance should be positive", route!!.totalDistanceMeters > 10.0)
        assertTrue("Polyline points should be present", route.polylinePoints.size >= 2)
    }

    @Test
    fun testComputeBearingAndDistanceFormulas() {
        // Distance between two known close points
        val d = MapRoutingEngine.computeDistanceMeters(12.8407, 80.1534, 12.8417, 80.1534)
        assertTrue("Distance should be approx 111 meters", d in 100.0..125.0)

        // Bearing heading North
        val bearingNorth = MapRoutingEngine.computeBearingDegrees(12.8400, 80.1500, 12.8500, 80.1500)
        assertEquals(0.0, bearingNorth, 1.0)

        // Bearing heading East
        val bearingEast = MapRoutingEngine.computeBearingDegrees(12.8400, 80.1500, 12.8400, 80.1600)
        assertEquals(90.0, bearingEast, 1.0)
    }
}
