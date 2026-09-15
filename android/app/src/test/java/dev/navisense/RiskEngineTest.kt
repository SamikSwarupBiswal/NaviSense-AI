package dev.navisense

import dev.navisense.contracts.AppVisionMode
import dev.navisense.contracts.DetectedObject
import dev.navisense.contracts.FrameQualityStatus
import dev.navisense.contracts.MobilePerceptionEvent
import dev.navisense.contracts.NormalizedRect
import dev.navisense.contracts.PathStatus
import dev.navisense.contracts.SensorEvent
import dev.navisense.contracts.SensorHealth
import dev.navisense.contracts.SensorWireRecord
import dev.navisense.navigation.RiskEngine
import dev.navisense.navigation.RiskLevel
import dev.navisense.navigation.RiskSource
import dev.navisense.navigation.WalkingCorridor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Deterministic AC-10 Replay and Unit Tests for RiskEngine.
 */
class RiskEngineTest {

    private lateinit var clock: FakeClock
    private lateinit var corridor: WalkingCorridor
    private lateinit var riskEngine: RiskEngine

    @Before
    fun setUp() {
        clock = FakeClock(1000L)
        corridor = WalkingCorridor()
        riskEngine = RiskEngine(clock = clock, corridor = corridor)
    }

    private fun createSensorEvent(
        distanceCm: Int,
        isValid: Boolean = true,
        timestampMs: Long = clock.nowMonotonicMs()
    ): SensorEvent {
        return SensorEvent(
            connectionId = 1L,
            receiptMonotonicMs = timestampMs,
            wireRecord = SensorWireRecord(
                version = 1,
                sequenceNumber = 100L,
                deviceUptimeMs = timestampMs,
                distanceCm = distanceCm,
                isValid = isValid
            ),
            sensorHealth = if (isValid) SensorHealth.STREAMING else SensorHealth.ERROR
        )
    }

    private fun createPerceptionEvent(
        detections: List<DetectedObject>,
        quality: FrameQualityStatus = FrameQualityStatus.USABLE,
        deliveryMs: Long = clock.nowMonotonicMs()
    ): MobilePerceptionEvent {
        return MobilePerceptionEvent(
            sessionGeneration = 1L,
            mode = AppVisionMode.MOBILITY,
            frameId = 10L,
            captureMonotonicMs = deliveryMs - 30L,
            deliveryMonotonicMs = deliveryMs,
            geometryVersion = 1,
            modelIdentity = "yolo_v8n_mobility",
            qualityStatus = quality,
            detections = detections
        )
    }

    @Test
    fun testUltrasonicBoundaryDistances() {
        // <= 50 cm -> STOP
        val r49 = riskEngine.onSensorEvent(createSensorEvent(49))
        assertEquals(RiskLevel.STOP, r49.combinedRisk)
        assertEquals(PathStatus.BLOCKED, r49.pathStatus)
        assertEquals(RiskSource.SENSOR, r49.primaryHazardSource)

        riskEngine.reset()
        val r50 = riskEngine.onSensorEvent(createSensorEvent(50))
        assertEquals(RiskLevel.STOP, r50.combinedRisk)

        // 51..100 cm -> SLOW
        riskEngine.reset()
        val r51 = riskEngine.onSensorEvent(createSensorEvent(51))
        assertEquals(RiskLevel.SLOW, r51.combinedRisk)
        assertEquals(PathStatus.BLOCKED, r51.pathStatus)

        riskEngine.reset()
        val r100 = riskEngine.onSensorEvent(createSensorEvent(100))
        assertEquals(RiskLevel.SLOW, r100.combinedRisk)

        // 101..150 cm -> AWARENESS
        riskEngine.reset()
        val r101 = riskEngine.onSensorEvent(createSensorEvent(101))
        assertEquals(RiskLevel.AWARENESS, r101.combinedRisk)

        riskEngine.reset()
        val r150 = riskEngine.onSensorEvent(createSensorEvent(150))
        assertEquals(RiskLevel.AWARENESS, r150.combinedRisk)

        // > 150 cm -> NONE
        riskEngine.reset()
        val r151 = riskEngine.onSensorEvent(createSensorEvent(151))
        assertEquals(RiskLevel.NONE, r151.combinedRisk)

        // Invalid distance (-1) -> raw NONE, does not clear path
        riskEngine.reset()
        val rInvalid = riskEngine.onSensorEvent(createSensorEvent(-1, isValid = false))
        assertEquals(RiskLevel.NONE, rInvalid.combinedRisk)
        assertEquals(PathStatus.UNKNOWN, rInvalid.pathStatus)
    }

    @Test
    fun testUltrasonicStopTakesPrecedenceOverVision() {
        // Camera sees clear path, sensor detects obstacle at 40 cm
        clock.set(1000L)
        riskEngine.onPerceptionEvent(createPerceptionEvent(emptyList(), deliveryMs = 1000L))
        val result = riskEngine.onSensorEvent(createSensorEvent(40, timestampMs = 1000L))

        assertEquals(RiskLevel.STOP, result.combinedRisk)
        assertEquals(RiskSource.SENSOR, result.primaryHazardSource)
        assertEquals(PathStatus.BLOCKED, result.pathStatus)
    }

    @Test
    fun testStopHoldTimerDuration() {
        clock.set(1000L)
        // Trigger STOP at 1000 ms
        val r1 = riskEngine.onSensorEvent(createSensorEvent(40, timestampMs = 1000L))
        assertEquals(RiskLevel.STOP, r1.combinedRisk)

        // Advance to 1500 ms (sensor now clear at 200 cm), hold must still enforce STOP
        clock.set(1500L)
        val r2 = riskEngine.onSensorEvent(createSensorEvent(200, timestampMs = 1500L))
        assertEquals("Hold timer must keep STOP active within 1000ms", RiskLevel.STOP, r2.combinedRisk)

        // At 1999 ms, hold still active
        clock.set(1999L)
        val r3 = riskEngine.onWatchdogTick(1999L)
        assertEquals("At 1999ms hold must still be active", RiskLevel.STOP, r3.combinedRisk)

        // At 2001 ms, hold has expired
        clock.set(2001L)
        val r4 = riskEngine.onWatchdogTick(2001L)
        assertEquals("At 2001ms hold has expired, risk returns to NONE", RiskLevel.NONE, r4.combinedRisk)
    }

    @Test
    fun testSlowHoldTimerDuration() {
        clock.set(1000L)
        val r1 = riskEngine.onSensorEvent(createSensorEvent(80, timestampMs = 1000L))
        assertEquals(RiskLevel.SLOW, r1.combinedRisk)

        // Sensor clear at 1300 ms, hold (500 ms) must maintain SLOW
        clock.set(1300L)
        val r2 = riskEngine.onSensorEvent(createSensorEvent(200, timestampMs = 1300L))
        assertEquals(RiskLevel.SLOW, r2.combinedRisk)

        // At 1501 ms, SLOW hold expires
        clock.set(1501L)
        val r3 = riskEngine.onWatchdogTick(1501L)
        assertEquals(RiskLevel.NONE, r3.combinedRisk)
    }

    @Test
    fun testVisionCorridorObstacleClassification() {
        clock.set(1000L)
        // 1. Obstacle outside corridor (e.g. far left [0.05, 0.20])
        val outsideObj = DetectedObject(
            classId = 0,
            label = "chair",
            confidence = 0.85f,
            boundingBox = NormalizedRect(0.05f, 0.40f, 0.20f, 0.70f)
        )
        val rOutside = riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(outsideObj), deliveryMs = 1000L))
        assertEquals(RiskLevel.NONE, rOutside.visionRisk)

        // 2. Obstacle inside corridor with bottom >= 0.70f -> STOP
        val stopObj = DetectedObject(
            classId = 0,
            label = "box",
            confidence = 0.80f,
            boundingBox = NormalizedRect(0.35f, 0.40f, 0.65f, 0.75f)
        )
        riskEngine.reset()
        val rStop = riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(stopObj), deliveryMs = 1000L))
        assertEquals(RiskLevel.STOP, rStop.visionRisk)
        assertEquals(RiskSource.VISION, rStop.primaryHazardSource)

        // 3. Obstacle inside corridor with bottom in [0.50, 0.70) -> SLOW
        val slowObj = DetectedObject(
            classId = 0,
            label = "cone",
            confidence = 0.75f,
            boundingBox = NormalizedRect(0.40f, 0.35f, 0.60f, 0.60f)
        )
        riskEngine.reset()
        val rSlow = riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(slowObj), deliveryMs = 1000L))
        assertEquals(RiskLevel.SLOW, rSlow.visionRisk)

        // 4. Obstacle inside corridor with bottom < 0.50f -> AWARENESS
        val awareObj = DetectedObject(
            classId = 0,
            label = "pole",
            confidence = 0.70f,
            boundingBox = NormalizedRect(0.45f, 0.30f, 0.55f, 0.45f)
        )
        riskEngine.reset()
        val rAware = riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(awareObj), deliveryMs = 1000L))
        assertEquals(RiskLevel.AWARENESS, rAware.visionRisk)
    }

    @Test
    fun testSensorCameraSyncLabelAssociation() {
        clock.set(1000L)
        val chair = DetectedObject(
            classId = 0,
            label = "chair",
            confidence = 0.85f,
            boundingBox = NormalizedRect(0.35f, 0.40f, 0.65f, 0.65f) // corridor obstacle
        )

        // Camera event at 1050 ms
        riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(chair), deliveryMs = 1050L))
        // Sensor event at 1000 ms (delta = 50 ms <= 200 ms)
        val result = riskEngine.onSensorEvent(createSensorEvent(70, timestampMs = 1000L))

        assertEquals(RiskLevel.SLOW, result.combinedRisk)
        assertEquals("chair", result.associatedObjectLabel)

        // If delta > 200 ms, label is not associated
        riskEngine.reset()
        riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(chair), deliveryMs = 1300L))
        val rDesync = riskEngine.onSensorEvent(createSensorEvent(70, timestampMs = 1000L))
        assertNull("Label must be null when sensor and camera are desynchronized (> 200ms)", rDesync.associatedObjectLabel)
    }

    @Test
    fun testClearObservedRequiresContinuousDistanceAndUsableVision() {
        // 1. Phone-only vision (no sensor) must NEVER produce CLEAR_OBSERVED
        clock.set(1000L)
        val rVisionOnly = riskEngine.onPerceptionEvent(createPerceptionEvent(emptyList(), deliveryMs = 1000L))
        assertEquals(PathStatus.UNKNOWN, rVisionOnly.pathStatus)

        // 2. Sensor active at 200 cm, but for only 500 ms (< 1.0s required)
        clock.set(1000L)
        riskEngine.onSensorEvent(createSensorEvent(200, timestampMs = 1000L))
        clock.set(1500L)
        val rPartial = riskEngine.onSensorEvent(createSensorEvent(200, timestampMs = 1500L))
        assertEquals(PathStatus.UNKNOWN, rPartial.pathStatus)

        // 3. Sensor streaming at 200 cm with gaps <= 200 ms (non-stale), vision usable and clear -> reaches CLEAR_OBSERVED after 1000 ms
        clock.set(1600L)
        riskEngine.onSensorEvent(createSensorEvent(200, timestampMs = 1600L))
        clock.set(1800L)
        riskEngine.onSensorEvent(createSensorEvent(200, timestampMs = 1800L))
        clock.set(2000L)
        riskEngine.onPerceptionEvent(createPerceptionEvent(emptyList(), deliveryMs = 2000L))
        val rClear = riskEngine.onSensorEvent(createSensorEvent(200, timestampMs = 2000L))
        assertEquals("After 1000ms continuous clear with active sensor and usable camera, path must be CLEAR_OBSERVED", PathStatus.CLEAR_OBSERVED, rClear.pathStatus)

        // 4. If camera becomes UNUSABLE, path reverts to UNKNOWN
        val rDegraded = riskEngine.onPerceptionEvent(
            createPerceptionEvent(emptyList(), quality = FrameQualityStatus.UNUSABLE, deliveryMs = 2050L)
        )
        assertEquals(PathStatus.UNKNOWN, rDegraded.pathStatus)
    }

    @Test
    fun testWatchdogDetectsStaleness() {
        clock.set(1000L)
        // Sensor at 40 cm -> STOP
        riskEngine.onSensorEvent(createSensorEvent(40, timestampMs = 1000L))

        // Let 1500 ms pass without new sensor packets (hold expires at 2000 ms, sensor stale at 1301 ms)
        clock.set(2500L)
        val rWatchdog = riskEngine.onWatchdogTick(2500L)

        assertEquals("Sensor staleness must reset sensor risk after timeout", RiskLevel.NONE, rWatchdog.sensorRisk)
        assertEquals(PathStatus.UNKNOWN, rWatchdog.pathStatus)
    }

    @Test
    fun testTrackChurnCannotEraseStop() {
        clock.set(1000L)
        // Frame 1: trackId 1 at bottom 0.75f (STOP)
        val obj1 = DetectedObject(0, "box", 0.9f, NormalizedRect(0.35f, 0.40f, 0.65f, 0.75f), trackId = 1L)
        val r1 = riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(obj1), deliveryMs = 1000L))
        assertEquals(RiskLevel.STOP, r1.combinedRisk)

        // Frame 2 at 1050 ms: track churn, trackId 1 lost, trackId 2 appears with same obstacle
        clock.set(1050L)
        val obj2 = DetectedObject(0, "box", 0.9f, NormalizedRect(0.35f, 0.40f, 0.65f, 0.75f), trackId = 2L)
        val r2 = riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(obj2), deliveryMs = 1050L))
        assertEquals("Track-ID churn must not erase STOP", RiskLevel.STOP, r2.combinedRisk)
    }

    @Test
    fun testEscalationFlagBehavior() {
        clock.set(1000L)
        // NONE -> SLOW: escalation
        val r1 = riskEngine.onSensorEvent(createSensorEvent(80, timestampMs = 1000L))
        assertTrue("NONE -> SLOW must be marked as escalation", r1.isEscalation)

        // SLOW -> SLOW: no escalation
        clock.set(1100L)
        val r2 = riskEngine.onSensorEvent(createSensorEvent(85, timestampMs = 1100L))
        assertFalse("Same severity must not be escalation", r2.isEscalation)

        // SLOW -> STOP: escalation
        clock.set(1200L)
        val r3 = riskEngine.onSensorEvent(createSensorEvent(40, timestampMs = 1200L))
        assertTrue("SLOW -> STOP must be marked as escalation", r3.isEscalation)
    }
}
