package dev.navisense

import dev.navisense.app.SessionCoordinator
import dev.navisense.contracts.AppMode
import dev.navisense.contracts.SensorEvent
import dev.navisense.contracts.SensorHealth
import dev.navisense.contracts.SessionGeneration
import dev.navisense.navigation.RiskEngine
import dev.navisense.navigation.maps.GoogleRoutesService
import dev.navisense.navigation.maps.models.GeoPoint
import dev.navisense.voice.AlertPriority
import dev.navisense.voice.SpeechArbiter
import dev.navisense.voice.SpeechRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NavigationSafetyPreemptionTest {

    private lateinit var clock: FakeClock
    private lateinit var fakeTts: FakeTextToSpeechPlayer
    private lateinit var arbiter: SpeechArbiter
    private lateinit var sessionGen: SessionGeneration
    private lateinit var coordinator: SessionCoordinator
    private val routesService = GoogleRoutesService()

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
    fun testObstaclePreemptsActiveTurnInstructionDuringOutdoorWalking() {
        // 1. User starts outdoor walking navigation
        val origin = GeoPoint(12.8400, 80.1500)
        val destination = GeoPoint(12.8406, 80.1508)
        val route = routesService.createMockWalkingRoute(origin, destination, "Library")

        val token = coordinator.startOutdoorWalking(route)
        assertEquals(AppMode.OUTDOOR_WALKING, coordinator.currentMode)
        // Initial departure speech finishes
        fakeTts.currentlySpeaking = false

        // 2. Navigation issues a turn instruction: "Turn right onto East Path" (Priority 3: DIRECTIONAL)
        clock.advanceBy(3000L)
        val turnRequest = SpeechRequest(
            utteranceId = "turn_1",
            phrase = "Turn right onto East Path",
            priority = AlertPriority.DIRECTIONAL,
            sessionGeneration = token.generation,
            requestMonotonicMs = clock.nowMonotonicMs()
        )
        val turnAccepted = arbiter.speak(turnRequest)
        assertTrue("Turn instruction accepted", turnAccepted)
        assertTrue("TTS is actively speaking turn instruction", fakeTts.isSpeaking())
        assertEquals("Turn right onto East Path", fakeTts.lastSpokenText)

        // 3. Suddenly, an obstacle appears at 0.5 meters (50 cm) (HC-SR04 ultrasonic sensor reading)
        clock.advanceBy(100L) // only 100ms later while turn is still speaking
        val obstacleEvent = SensorEvent(
            connectionId = 1L,
            receiptMonotonicMs = clock.nowMonotonicMs(),
            wireRecord = dev.navisense.contracts.SensorWireRecord(
                version = 1,
                sequenceNumber = 100L,
                deviceUptimeMs = 5000L,
                distanceCm = 50,
                isValid = true
            ),
            sensorHealth = SensorHealth.STREAMING
        )
        coordinator.onSensorEvent(obstacleEvent)

        // 4. Verification: The turn instruction must have been preempted immediately!
        // fakeTts.stop() called, and STOP spoken!
        assertTrue("Emergency STOP preempted TTS playback", fakeTts.stopCount > 0)
        assertEquals("STOP.", fakeTts.lastSpokenText)
    }

    @Test
    fun testImmediateUserStopCancelsNavigationAndSilencesAudio() {
        val origin = GeoPoint(12.8400, 80.1500)
        val destination = GeoPoint(12.8406, 80.1508)
        val route = routesService.createMockWalkingRoute(origin, destination, "Library")

        coordinator.startOutdoorWalking(route)
        assertEquals(AppMode.OUTDOOR_WALKING, coordinator.currentMode)

        // User taps Stop
        val stopToken = coordinator.userStop()
        assertEquals(AppMode.IDLE, coordinator.currentMode)
        assertEquals(AppMode.IDLE, stopToken.mode)
        assertTrue("Audio playback immediately silenced", !fakeTts.isSpeaking())
    }
}
