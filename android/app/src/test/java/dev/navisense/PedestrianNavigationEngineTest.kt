package dev.navisense

import dev.navisense.navigation.maps.PedestrianNavigationEngine
import dev.navisense.navigation.maps.models.GeoPoint
import dev.navisense.navigation.maps.models.ManeuverType
import dev.navisense.navigation.maps.models.NavigationEngineStatus
import dev.navisense.navigation.maps.models.NavigationGuidance
import dev.navisense.navigation.maps.models.WalkingRoute
import dev.navisense.navigation.maps.models.WalkingStep
import dev.navisense.voice.AlertPriority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PedestrianNavigationEngineTest {

    private lateinit var clock: FakeClock
    private lateinit var engine: PedestrianNavigationEngine
    private val emittedGuidance = mutableListOf<NavigationGuidance>()
    private val emittedStatuses = mutableListOf<NavigationEngineStatus>()
    private var arrived = false
    private var offRouteDetected = false

    @Before
    fun setUp() {
        clock = FakeClock(1000L)
        engine = PedestrianNavigationEngine(clock = clock)
        emittedGuidance.clear()
        emittedStatuses.clear()
        arrived = false
        offRouteDetected = false

        engine.addListener(object : PedestrianNavigationEngine.NavigationListener {
            override fun onGuidanceGenerated(guidance: NavigationGuidance) {
                emittedGuidance.add(guidance)
            }

            override fun onStatusUpdated(status: NavigationEngineStatus) {
                emittedStatuses.add(status)
            }

            override fun onOffRouteDetected() {
                offRouteDetected = true
            }

            override fun onArrival() {
                arrived = true
            }
        })
    }

    private fun createTestRoute(): WalkingRoute {
        // Step 1: Origin (12.8400, 80.1500) -> Waypoint 1 (12.8403, 80.1500) [North ~33m]
        val p0 = GeoPoint(12.8400, 80.1500)
        val p1 = GeoPoint(12.8403, 80.1500)
        // Step 2: Waypoint 1 -> Waypoint 2 (12.8403, 80.1504) [East ~43m]
        val p2 = GeoPoint(12.8403, 80.1504)

        val step1 = WalkingStep(
            instruction = "Walk straight along Corridor A",
            maneuver = ManeuverType.DEPART,
            distanceMeters = 33,
            durationSeconds = 25,
            startLocation = p0,
            endLocation = p1,
            polylinePoints = listOf(p0, p1)
        )

        val step2 = WalkingStep(
            instruction = "Turn right onto East Path",
            maneuver = ManeuverType.RIGHT,
            distanceMeters = 43,
            durationSeconds = 30,
            startLocation = p1,
            endLocation = p2,
            polylinePoints = listOf(p1, p2)
        )

        return WalkingRoute(
            destinationName = "Library",
            totalDistanceMeters = 76,
            totalDurationSeconds = 55,
            steps = listOf(step1, step2),
            overviewPolyline = listOf(p0, p1, p2)
        )
    }

    @Test
    fun testRelativeBearingCalculation() {
        // User facing North (0°), target bearing East (90°) -> 90°
        assertEquals(90f, engine.calculateRelativeAngle(90f, 0f), 0.1f)
        // User facing East (90°), target bearing North (0°) -> 270° (Turn Left)
        assertEquals(270f, engine.calculateRelativeAngle(0f, 90f), 0.1f)
        // User facing North (10°), target bearing North-West (350°) -> 340°
        assertEquals(340f, engine.calculateRelativeAngle(350f, 10f), 0.1f)
    }

    @Test
    fun testRelativeTurnPhrasing() {
        // Facing North (0°), target bearing North (5°) -> Continue straight
        assertEquals("Continue straight", engine.calculateRelativeTurnPhrase(5f, 0f))
        // Facing North (0°), target bearing East (90°) -> Turn right
        assertEquals("Turn right", engine.calculateRelativeTurnPhrase(90f, 0f))
        // Facing North (0°), target bearing South-East (135°) -> Turn sharp right
        assertEquals("Turn sharp right", engine.calculateRelativeTurnPhrase(135f, 0f))
        // Facing North (0°), target bearing South (180°) -> Make a U-turn
        assertEquals("Make a U-turn", engine.calculateRelativeTurnPhrase(180f, 0f))
        // Facing North (0°), target bearing West (270°) -> Turn left
        assertEquals("Turn left", engine.calculateRelativeTurnPhrase(270f, 0f))
        // Facing North (0°), target bearing North-West (320°) -> Bear slight left
        assertEquals("Bear slight left", engine.calculateRelativeTurnPhrase(320f, 0f))
    }

    @Test
    fun testRouteLifecycleAndGuidanceProgression() {
        val route = createTestRoute()

        // 1. Start route
        engine.startRoute(route)
        assertEquals(1, emittedGuidance.size)
        assertTrue(emittedGuidance[0].phrase.contains("Starting walking navigation to Library"))
        assertEquals(AlertPriority.DIRECTIONAL, emittedGuidance[0].priority)

        // 2. User moves closer to Step 1 end (15 meters away) -> Upcoming alert triggered
        clock.advanceBy(2000L)
        val approachingP1 = GeoPoint(12.84018, 80.1500) // ~13m from p1
        engine.onLocationUpdated(approachingP1)

        val upcomingGuidance = emittedGuidance.find { it.priority == AlertPriority.AWARENESS }
        assertTrue("Upcoming alert was emitted", upcomingGuidance != null)
        assertTrue(upcomingGuidance!!.phrase.contains("In 10 meters") || upcomingGuidance.phrase.contains("In 15 meters"))

        // 3. User reaches within 5 meters of Step 1 end -> Actionable turn cue triggered
        clock.advanceBy(2000L)
        val closeToP1 = GeoPoint(12.84026, 80.1500) // ~4.4m from p1
        engine.onLocationUpdated(closeToP1)

        val actionableGuidance = emittedGuidance.find { it.isActionableCue }
        assertTrue("Actionable turn cue was emitted", actionableGuidance != null)

        // 4. User reaches waypoint p1 (< 4m) -> Advance to Step 2
        clock.advanceBy(2000L)
        val atP1 = GeoPoint(12.84029, 80.1500) // ~1.1m from p1
        engine.onLocationUpdated(atP1)
        assertEquals(1, emittedStatuses.last().currentStepIndex)

        // 5. User reaches final destination p2 (< 5m) -> Arrival announced
        clock.advanceBy(5000L)
        val atDestination = GeoPoint(12.8403, 80.15039) // ~1m from p2
        engine.onLocationUpdated(atDestination)

        assertTrue("Arrival callback triggered", arrived)
        val arrivalGuidance = emittedGuidance.last()
        assertTrue("Arrival phrase spoken", arrivalGuidance.phrase.contains("arrived at your destination"))
        assertEquals(AlertPriority.DIRECTIONAL, arrivalGuidance.priority)
    }

    @Test
    fun testOffRouteDetection() {
        val route = createTestRoute()
        engine.startRoute(route)

        // User walks far away from route (e.g. 50 meters West)
        val offRoutePoint = GeoPoint(12.8400, 80.1490) // ~108m from route

        clock.advanceBy(1000L)
        engine.onLocationUpdated(offRoutePoint)
        clock.advanceBy(2000L)
        engine.onLocationUpdated(offRoutePoint)
        clock.advanceBy(12000L)
        engine.onLocationUpdated(offRoutePoint)

        assertTrue("Off-route detected after 3 consecutive updates", offRouteDetected)
        val offRouteMsg = emittedGuidance.find { it.phrase.contains("off route", ignoreCase = true) }
        assertTrue("Spoken off route notification emitted", offRouteMsg != null)
        assertEquals(AlertPriority.AWARENESS, offRouteMsg!!.priority)
    }

    @Test
    fun testGoogleMapsStyleAnnouncements() {
        val p0 = GeoPoint(12.8400, 80.1500)
        val p1 = GeoPoint(12.8409, 80.1500) // ~100m North
        val p2 = GeoPoint(12.8409, 80.1509) // ~100m East

        val step1 = WalkingStep(
            instruction = "Head forward on Vandalur Road",
            maneuver = ManeuverType.DEPART,
            distanceMeters = 100,
            durationSeconds = 75,
            startLocation = p0,
            endLocation = p1,
            streetName = "Vandalur Road",
            polylinePoints = listOf(p0, p1)
        )

        val step2 = WalkingStep(
            instruction = "Turn right onto GST Road",
            maneuver = ManeuverType.RIGHT,
            distanceMeters = 100,
            durationSeconds = 75,
            startLocation = p1,
            endLocation = p2,
            streetName = "GST Road",
            polylinePoints = listOf(p1, p2)
        )

        val route = WalkingRoute(
            destinationName = "Vandalur Zoo",
            totalDistanceMeters = 200,
            totalDurationSeconds = 150,
            steps = listOf(step1, step2),
            overviewPolyline = listOf(p0, p1, p2)
        )

        engine.startRoute(route)
        // 1. Initial Start announcement
        val startMsg = emittedGuidance.first()
        assertTrue("Contains 'We are walking on Vandalur Road'", startMsg.phrase.contains("We are walking on Vandalur Road"))

        // 2. Approach 50 meters from turn (p1 is at lat 12.8409; user at lat 12.84045 is ~50m from p1)
        clock.advanceBy(2000L)
        val at50m = GeoPoint(12.84045, 80.1500)
        engine.onLocationUpdated(at50m)

        val alert50 = emittedGuidance.find { it.phrase.contains("50 meters") }
        assertTrue("Spoken 'In 50 meters, turn right onto GST Road' emitted", alert50 != null)
        assertTrue(alert50!!.phrase.contains("turn right onto GST Road"))

        // 3. User turns onto GST Road (advance past p1)
        clock.advanceBy(3000L)
        val atStep2 = GeoPoint(12.8409, 80.15002) // ~2m into step 2
        engine.onLocationUpdated(atStep2)

        val walkingGst = emittedGuidance.find { it.phrase.contains("We are walking on GST Road") }
        assertTrue("Spoken 'We are walking on GST Road' emitted", walkingGst != null)
    }
}
