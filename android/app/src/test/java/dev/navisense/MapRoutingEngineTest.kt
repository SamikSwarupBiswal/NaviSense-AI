package dev.navisense

import dev.navisense.map.MapRoutingEngine
import dev.navisense.map.TurnType
import dev.navisense.map.toWalkingRoute
import dev.navisense.navigation.maps.models.ManeuverType
import dev.navisense.navigation.maps.models.WalkingRoute
import dev.navisense.navigation.maps.models.WalkingStep
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

    @Test
    fun testCampusPoisRoutable() {
        val testPoiIds = listOf("poi_academic_block_1", "poi_admin_block", "poi_food_court", "poi_sports_complex")
        val startLat = 12.8407
        val startLon = 80.1534

        for (poiId in testPoiIds) {
            val poi = routingEngine.getPoi(poiId)
            assertNotNull("POI $poiId must exist in map asset", poi)
            val route = routingEngine.planRoute(startLat, startLon, poiId)
            assertNotNull("Route to $poiId should be found", route)
            assertTrue("Route to $poiId should have distance > 0", route!!.totalDistanceMeters > 0)
        }
    }

    @Test
    fun testToWalkingRouteConversion() {
        val startLat = 12.8407
        val startLon = 80.1534
        val route = routingEngine.planRoute(startLat, startLon, "poi_academic_block_1")
        assertNotNull(route)

        val walkingRoute = route!!.toWalkingRoute()
        assertEquals("Academic Block 1 (AB1)", walkingRoute.destinationName)
        assertTrue("WalkingRoute total distance > 0", walkingRoute.totalDistanceMeters > 0)
        assertTrue("WalkingRoute total duration > 0", walkingRoute.totalDurationSeconds > 0)
        assertTrue("Must contain steps", walkingRoute.steps.isNotEmpty())
        assertTrue("Overview polyline must contain points", walkingRoute.overviewPolyline.isNotEmpty())

        val lastStep = walkingRoute.steps.last()
        assertEquals(ManeuverType.ARRIVE, lastStep.maneuver)
        assertTrue(lastStep.instruction.contains("Academic Block 1"))
    }

    @Test
    fun testSnappingWithin250Meters() {
        // Point slightly off-path (~100m from nearest walkway)
        val offPathLat = 12.8410
        val offPathLon = 80.1520

        val route = routingEngine.planRoute(offPathLat, offPathLon, "poi_food_court", maxSnapDistanceMeters = 250.0)
        assertNotNull("Should snap successfully within 250m", route)
        assertTrue("Distance should be computed", route!!.totalDistanceMeters > 0)
    }
}
