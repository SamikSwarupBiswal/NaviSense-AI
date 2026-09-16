package dev.navisense.navigation.maps

import dev.navisense.FakeClock
import dev.navisense.FakeTextToSpeechPlayer
import dev.navisense.app.SessionCoordinator
import dev.navisense.contracts.AppMode
import dev.navisense.contracts.SensorEvent
import dev.navisense.contracts.SensorHealth
import dev.navisense.contracts.SensorWireRecord
import dev.navisense.contracts.SessionGeneration
import dev.navisense.map.MapNavigationCoordinator
import dev.navisense.map.MapPOI
import dev.navisense.map.MapPoint
import dev.navisense.map.MapRoutingEngine
import dev.navisense.map.TurnType
import dev.navisense.navigation.RiskEngine
import dev.navisense.navigation.maps.models.DestinationNotFoundException
import dev.navisense.navigation.maps.models.GeoPoint
import dev.navisense.navigation.maps.models.ManeuverType
import dev.navisense.navigation.maps.models.NavigationEngineStatus
import dev.navisense.navigation.maps.models.RouteNotFoundException
import dev.navisense.navigation.maps.models.WalkingRoute
import dev.navisense.navigation.maps.models.WalkingStep
import dev.navisense.voice.AlertPriority
import dev.navisense.voice.SpeechArbiter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class NavigationDistanceRemediationTest {

    private lateinit var clock: FakeClock
    private lateinit var fakeTts: FakeTextToSpeechPlayer
    private lateinit var arbiter: SpeechArbiter
    private lateinit var sessionGen: SessionGeneration
    private lateinit var coordinator: SessionCoordinator

    @Before
    fun setUp() {
        clock = FakeClock(1000L)
        fakeTts = FakeTextToSpeechPlayer()
        arbiter = SpeechArbiter(ttsPlayer = fakeTts, clock = clock)
        sessionGen = SessionGeneration()
        coordinator = SessionCoordinator(
            sessionGeneration = sessionGen,
            clock = clock,
            riskEngine = RiskEngine(clock = clock),
            speechArbiter = arbiter
        )
    }

    @Test
    fun testN01_lookupReturnsFailureWhenDestinationNotFound_noFallbackCoordinates() = runBlocking {
        // Without API key and without Android context, geocodeDestination must return failure
        val service = GoogleRoutesService(apiKey = null, context = null)
        val result = service.geocodeDestination("NonExistentPlaceXYZ123")

        assertTrue("Expected geocode failure", result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue("Expected DestinationNotFoundException", exception is DestinationNotFoundException)
        // Verify NO fallback coordinate (such as 12.8442, 80.1549) is returned as a success
        assertNull("No coordinate returned on failure", result.getOrNull())
    }

    @Test
    fun testN02_providerFailureReturnsTypedFailure_noMockRouteGenerated() = runBlocking {
        // Without API key, Google Routes is skipped. With a dummy origin/destination,
        // if OSRM is unreachable/empty, computeWalkingRoute must return RouteNotFoundException, NOT a 120m mock route.
        val service = GoogleRoutesService(apiKey = null, context = null)
        // Use coordinates in the middle of the ocean where OSRM returns no route / error
        val origin = GeoPoint(0.0001, 0.0001)
        val destination = GeoPoint(0.0002, 0.0002)

        val result = service.computeWalkingRoute(origin, destination, "Phoenix Mall")
        assertTrue("Routing must fail without valid provider route", result.isFailure)
        assertTrue(result.exceptionOrNull() is RouteNotFoundException)
    }

    @Test
    fun testN03_resolveStreetNameReturnsEmptyStringOnFailure_noVandalurRoad() {
        val service = GoogleRoutesService(apiKey = null, context = null)
        val street = service.resolveStreetName(GeoPoint(12.8406, 80.1534))
        assertEquals("Unresolved street must return empty string, never invented street name", "", street)
    }

    @Test
    fun testN07_outOfCoverageCampusGraphSnapReturnsNull() {
        val minimalMapJson = """
        {
          "mapRegion": "Test Campus",
          "centerLat": 12.8406,
          "centerLon": 80.1535,
          "pois": [
            {
              "id": "poi_library",
              "name": "Library",
              "category": "facility",
              "lat": 12.8402,
              "lon": 80.1542,
              "description": "Library",
              "nearestNodeId": 101,
              "distanceToNodeMeters": 5.0
            }
          ],
          "nodes": [
            {"id": 101, "lat": 12.8402, "lon": 80.1542},
            {"id": 102, "lat": 12.8405, "lon": 80.1542}
          ],
          "edges": [
            {"from": 101, "to": 102, "distanceMeters": 33.3, "roadName": "Campus Walk"}
          ]
        }
        """.trimIndent()

        val engine = MapRoutingEngine.loadFromStream(ByteArrayInputStream(minimalMapJson.toByteArray()))

        // Phoenix Mall Velachery coordinates: 12.9915, 80.2168 (~20 km away)
        val snapResult = engine.findNearestNode(12.9915, 80.2168, maxDistanceMeters = 80.0)
        assertNull("Off-campus coordinate must NOT snap to campus graph", snapResult)

        // Route planning from Phoenix Mall must return null
        val routeResult = engine.planRoute(12.9915, 80.2168, "poi_library", maxSnapDistanceMeters = 80.0)
        assertNull("Route planning from outside campus coverage must return null", routeResult)
    }

    @Test
    fun testN08_originAndDestinationConnectorsIncludedInTotalDistance() {
        val json = """
        {
          "mapRegion": "Test Campus",
          "centerLat": 12.8400,
          "centerLon": 80.1500,
          "pois": [
            {
              "id": "poi_dest",
              "name": "Target POI",
              "category": "facility",
              "lat": 12.8400,
              "lon": 80.1520,
              "description": "Dest",
              "nearestNodeId": 2,
              "distanceToNodeMeters": 10.0
            }
          ],
          "nodes": [
            {"id": 1, "lat": 12.8400, "lon": 80.1505},
            {"id": 2, "lat": 12.8400, "lon": 80.1515}
          ],
          "edges": [
            {"from": 1, "to": 2, "distanceMeters": 100.0, "roadName": "Main Walkway"}
          ]
        }
        """.trimIndent()

        val engine = MapRoutingEngine.loadFromStream(ByteArrayInputStream(json.toByteArray()))
        // Start is at (12.8400, 80.1500), which is ~54 meters from node 1
        val route = engine.planRoute(12.8400, 80.1500, "poi_dest", maxSnapDistanceMeters = 100.0)
        assertNotNull(route)
        val r = route!!

        // Total distance must be strictly greater than just the 100m edge distance because of connectors
        assertTrue("Total distance must include connectors (was: ${r.totalDistanceMeters})", r.totalDistanceMeters > 110.0)
        assertTrue("Polyline must include start coordinate", r.polylinePoints.first().lat == 12.8400 && r.polylinePoints.first().lon == 80.1500)
        assertTrue("Polyline must include destination coordinate", r.polylinePoints.last().lat == 12.8400 && r.polylinePoints.last().lon == 80.1520)
    }

    @Test
    fun testN09_LShapedFixture_alongRouteDistanceVsGeodesicDiagonal() {
        // L-shaped walking path:
        // P0: (0.0, 0.0)
        // P1: (0.0009, 0.0) ~ 100m North
        // P2: (0.0009, 0.0009) ~ 100m East from P1
        val p0 = GeoPoint(0.0, 0.0)
        val p1 = GeoPoint(0.0009, 0.0)
        val p2 = GeoPoint(0.0009, 0.0009)

        val polyline = listOf(p0, p1, p2)
        val alongRouteRemaining = PedestrianProgressCalculator.computeRemainingDistanceGeoPoints(
            current = p0,
            polyline = polyline,
            preferredStartIndex = 0
        )

        val straightLineDistance = p0.distanceTo(p2)

        // Geodesic straight-line is approx 141m
        assertEquals(141.5f, straightLineDistance, 5.0f)

        // Along-route remaining distance MUST be approximately 200m (100m + 100m), NOT ~141m!
        assertEquals(200.0f, alongRouteRemaining, 5.0f)
        assertTrue(
            "Along-route remaining ($alongRouteRemaining) must exceed diagonal geodesic distance ($straightLineDistance)",
            alongRouteRemaining > straightLineDistance + 40f
        )
    }

    @Test
    fun testN10_curvedRouteAlongRouteProgressTracking() {
        // Route with 4 segments (3 bends):
        // (0.0, 0.0) -> (0.0005, 0.0) (~55m) -> (0.0005, 0.0005) (~55m) -> (0.0010, 0.0005) (~55m)
        val p0 = GeoPoint(0.0, 0.0)
        val p1 = GeoPoint(0.0005, 0.0)
        val p2 = GeoPoint(0.0005, 0.0005)
        val p3 = GeoPoint(0.0010, 0.0005)
        val polyline = listOf(p0, p1, p2, p3)

        // Midpoint of segment 1 (between p0 and p1)
        val mid01 = GeoPoint(0.00025, 0.0)
        val remainingFromMid = PedestrianProgressCalculator.computeRemainingDistanceGeoPoints(
            current = mid01,
            polyline = polyline,
            preferredStartIndex = 0
        )

        // Segment lengths are ~55.6m each -> total ~166.7m. From mid01, remaining should be ~139m.
        val totalDist = PedestrianProgressCalculator.computeRemainingDistanceGeoPoints(p0, polyline, 0)
        assertTrue("Total distance ~166m (was $totalDist)", totalDist in 160f..175f)
        assertTrue("Midpoint remaining ~139m (was $remainingFromMid)", remainingFromMid in 130f..145f)
    }

    @Test
    fun testN11_stationaryJitterDoesNotFalselyArrive_andReverseIncreasesDistance() {
        val p0 = GeoPoint(12.8400, 80.1500)
        val p1 = GeoPoint(12.8410, 80.1500) // ~111 meters away
        val polyline = listOf(p0, p1)

        // Initial position
        val dist0 = PedestrianProgressCalculator.computeRemainingDistanceGeoPoints(p0, polyline, 0)

        // Jitter near start (+- 1m)
        val jitterPos = GeoPoint(12.84001, 80.15001)
        val distJitter = PedestrianProgressCalculator.computeRemainingDistanceGeoPoints(jitterPos, polyline, 0)
        assertTrue("Jitter position should still have ~110m remaining", distJitter > 100f)

        // Move backward (away from destination)
        val backwardPos = GeoPoint(12.8395, 80.1500)
        val distBackward = PedestrianProgressCalculator.computeRemainingDistanceGeoPoints(backwardPos, polyline, 0)
        assertTrue("Moving backward must increase remaining distance (was $distBackward vs initial $dist0)", distBackward > dist0)
    }

    @Test
    fun testN13_stopCancelsActiveNavigationAndResetsState() {
        val coordinator = MapNavigationCoordinator(speechArbiter = arbiter, clock = clock)
        val poi = MapPOI("1", "AB1", "academic", 12.840, 80.150, "Desc", 101, 5.0)
        val route = dev.navisense.map.NavigationRoute(
            destination = poi,
            totalDistanceMeters = 200.0,
            maneuvers = emptyList(),
            polylinePoints = listOf(MapPoint(12.840, 80.150), MapPoint(12.842, 80.150))
        )

        var routeUpdatedCalled = false
        coordinator.onRouteUpdated = { r, _, _ ->
            if (r == null) routeUpdatedCalled = true
        }

        coordinator.startNavigation(route, sessionGeneration = 42L)
        clock.advanceBy(3000L)
        fakeTts.currentlySpeaking = false

        coordinator.stopNavigation()

        assertTrue("stopNavigation must clear active route and invoke update callback with null", routeUpdatedCalled)
        assertEquals("Navigation stopped.", fakeTts.lastSpokenText)
    }

    @Test
    fun testN14_emergencySensorStopPreemptsDirectionalSpeech() {
        val origin = GeoPoint(12.8400, 80.1500)
        val destination = GeoPoint(12.8406, 80.1508)
        val route = WalkingRouteFixtureBuilder.createMockWalkingRoute(origin, destination, "Library")

        val token = coordinator.startOutdoorWalking(route)
        fakeTts.currentlySpeaking = false

        // Simulate active turn guidance
        clock.advanceBy(2000L)
        arbiter.speak(
            dev.navisense.voice.SpeechRequest(
                utteranceId = "turn_prompt",
                phrase = "In 20 meters, turn right onto East Path",
                priority = AlertPriority.AWARENESS,
                sessionGeneration = token.generation,
                requestMonotonicMs = clock.nowMonotonicMs()
            )
        )
        assertTrue(fakeTts.isSpeaking())

        // Sensor detects close hazard (distance <= 50 cm)
        val sensorEvent = SensorEvent(
            connectionId = 1L,
            receiptMonotonicMs = clock.nowMonotonicMs(),
            wireRecord = SensorWireRecord(
                version = 1,
                sequenceNumber = 1L,
                deviceUptimeMs = 2000L,
                distanceCm = 45,
                isValid = true
            ),
            sensorHealth = SensorHealth.STREAMING
        )
        coordinator.onSensorEvent(sensorEvent)

        // Emergency STOP must silence turn prompt and say STOP
        assertTrue(fakeTts.stopCount > 0)
        assertEquals("STOP.", fakeTts.lastSpokenText)
    }

    @Test
    fun testPedestrianNavigationEngine_usesAlongRouteRemainingDistance() {
        val engine = PedestrianNavigationEngine(clock = clock)
        var lastStatus: NavigationEngineStatus? = null

        engine.addListener(object : PedestrianNavigationEngine.NavigationListener {
            override fun onGuidanceGenerated(guidance: dev.navisense.navigation.maps.models.NavigationGuidance) {}
            override fun onStatusUpdated(status: NavigationEngineStatus) {
                lastStatus = status
            }
            override fun onOffRouteDetected() {}
            override fun onArrival() {}
        })

        // L-shaped route: P0 (0,0) -> P1 (0.0009, 0) -> P2 (0.0009, 0.0009)
        val p0 = GeoPoint(0.0, 0.0)
        val p1 = GeoPoint(0.0009, 0.0)
        val p2 = GeoPoint(0.0009, 0.0009)

        val step1 = WalkingStep(
            instruction = "Head North",
            maneuver = ManeuverType.DEPART,
            distanceMeters = 100,
            durationSeconds = 60,
            startLocation = p0,
            endLocation = p1,
            streetName = "North Ave",
            polylinePoints = listOf(p0, p1)
        )
        val step2 = WalkingStep(
            instruction = "Turn right onto East St",
            maneuver = ManeuverType.RIGHT,
            distanceMeters = 100,
            durationSeconds = 60,
            startLocation = p1,
            endLocation = p2,
            streetName = "East St",
            polylinePoints = listOf(p1, p2)
        )
        val route = WalkingRoute(
            destinationName = "Corner Store",
            totalDistanceMeters = 200,
            totalDurationSeconds = 120,
            steps = listOf(step1, step2),
            overviewPolyline = listOf(p0, p1, p2)
        )

        engine.startRoute(route)
        engine.onLocationUpdated(p0, accuracyMeters = 3f)

        assertNotNull(lastStatus)
        // totalRemainingDistanceMeters should be ~200m along route, NOT ~141m diagonal
        assertEquals(200f, lastStatus!!.totalRemainingDistanceMeters, 5f)
    }
}
