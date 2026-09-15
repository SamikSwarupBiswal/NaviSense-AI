package dev.navisense

import dev.navisense.app.SessionCoordinator
import dev.navisense.contracts.*
import dev.navisense.navigation.*
import dev.navisense.voice.SpeechArbiter
import org.junit.Assert.*
import org.junit.Test

class FusionIntegrationTest {
    @Test
    fun reducerEmitsImmutableStateAndRejectsDuplicateSensorInput() {
        val engine = RiskEngine()
        val event = sensorEvent(40, 1_000L)
        val first = engine.reduce(FusionInput.Sensor(event))
        val duplicate = engine.reduce(FusionInput.Sensor(event.copy(
            wireRecord = event.wireRecord.copy(distanceCm = 200)
        )))

        assertTrue(first.accepted)
        assertEquals(RiskLevel.STOP, first.state.combinedRisk)
        assertEquals(EvidenceAvailability.AVAILABLE, first.state.sensorAvailability)
        assertFalse(duplicate.accepted)
        assertEquals(RiskLevel.STOP, duplicate.state.combinedRisk)
    }

    @Test
    fun coordinatorAnnouncesUnknownAfterHazardRelease() {
        val fixture = coordinatorFixture(1_000L)
        fixture.coordinator.startMobility()
        fixture.player.currentlySpeaking = false

        fixture.clock.set(1_100L)
        fixture.coordinator.onSensorEvent(sensorEvent(40, 1_100L))
        assertEquals("STOP.", fixture.player.lastSpokenText)
        fixture.player.currentlySpeaking = false

        fixture.clock.set(1_500L)
        fixture.coordinator.onSensorEvent(sensorEvent(200, 1_500L))
        fixture.clock.set(2_501L)
        fixture.coordinator.onSensorEvent(sensorEvent(200, 2_501L))

        assertEquals(PathStatus.UNKNOWN, fixture.coordinator.currentPathStatus)
        assertEquals("Cannot confirm the path is clear.", fixture.player.lastSpokenText)
    }

    @Test
    fun finalSearchIgnoresLocateVisionRiskButRetainsUltrasonicStop() {
        val fixture = coordinatorFixture(1_000L)
        val token = fixture.coordinator.startNearbySearch("keys")
        fixture.player.currentlySpeaking = false

        val locateFrame = perceptionEvent(
            generation = token.generation,
            mode = AppVisionMode.LOCATE_SEARCH,
            timestampMs = 1_100L,
            detections = listOf(
                DetectedObject(0, "keys", 0.95f, NormalizedRect(0.2f, 0.2f, 0.8f, 0.95f), 1L)
            )
        )
        fixture.clock.set(1_100L)
        fixture.coordinator.onPerceptionEvent(locateFrame)
        assertEquals(PathStatus.UNKNOWN, fixture.coordinator.currentPathStatus)

        fixture.clock.set(1_200L)
        fixture.coordinator.onSensorEvent(sensorEvent(40, 1_200L))
        assertEquals("STOP.", fixture.player.lastSpokenText)
        assertEquals(dev.navisense.contracts.AppMode.FINAL_SEARCH, fixture.coordinator.currentMode)
    }

    @Test
    fun continuousDualSourceEvidenceTransitionsToClearOnce() {
        val fixture = coordinatorFixture(1_000L)
        val token = fixture.coordinator.startMobility()
        fixture.player.currentlySpeaking = false

        listOf(1_000L, 1_500L, 2_001L).forEach { timestamp ->
            fixture.clock.set(timestamp)
            fixture.coordinator.onSensorEvent(sensorEvent(200, timestamp))
            fixture.coordinator.onPerceptionEvent(
                perceptionEvent(token.generation, AppVisionMode.MOBILITY, timestamp, emptyList())
            )
            fixture.player.currentlySpeaking = false
        }

        assertEquals(PathStatus.CLEAR_OBSERVED, fixture.coordinator.currentPathStatus)
        assertEquals("No obstacle detected ahead.", fixture.player.lastSpokenText)
    }

    private data class Fixture(
        val clock: FakeClock,
        val player: FakeTextToSpeechPlayer,
        val coordinator: SessionCoordinator
    )

    private fun coordinatorFixture(startMs: Long): Fixture {
        val clock = FakeClock(startMs)
        val player = FakeTextToSpeechPlayer()
        val speech = SpeechArbiter(player, clock)
        val generation = SessionGeneration()
        return Fixture(clock, player, SessionCoordinator(generation, clock, RiskEngine(clock = clock), speech))
    }

    private fun sensorEvent(distanceCm: Int, timestampMs: Long) = SensorEvent(
        connectionId = 1L,
        receiptMonotonicMs = timestampMs,
        wireRecord = SensorWireRecord(1, timestampMs, timestampMs, distanceCm, true),
        sensorHealth = SensorHealth.STREAMING
    )

    private fun perceptionEvent(
        generation: Long,
        mode: AppVisionMode,
        timestampMs: Long,
        detections: List<DetectedObject>
    ) = MobilePerceptionEvent(
        sessionGeneration = generation,
        mode = mode,
        frameId = timestampMs,
        captureMonotonicMs = timestampMs,
        deliveryMonotonicMs = timestampMs,
        geometryVersion = 1,
        modelIdentity = if (mode == AppVisionMode.MOBILITY) "mobility" else "locate",
        qualityStatus = FrameQualityStatus.USABLE,
        detections = detections
    )
}
