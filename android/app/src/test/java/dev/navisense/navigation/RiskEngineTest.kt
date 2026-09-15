package dev.navisense.navigation

import dev.navisense.contracts.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RiskEngineTest {

    private lateinit var riskEngine: RiskEngine

    @Before
    fun setup() {
        riskEngine = RiskEngine()
    }

    private fun createSensorEvent(
        distanceCm: Int,
        timestampMs: Long,
        isValid: Boolean = true
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
            sensorHealth = SensorHealth.STREAMING
        )
    }

    private fun createPerceptionEvent(
        detections: List<DetectedObject>,
        timestampMs: Long,
        quality: FrameQualityStatus = FrameQualityStatus.USABLE
    ): PerceptionFrameEvent {
        return MobilePerceptionEvent(
            sessionGeneration = 1L,
            mode = AppVisionMode.MOBILITY,
            frameId = 1L,
            captureMonotonicMs = timestampMs,
            deliveryMonotonicMs = timestampMs,
            geometryVersion = 1,
            modelIdentity = "mobility_model_v1",
            qualityStatus = quality,
            detections = detections
        )
    }

    // =========================================================================
    // 1. Ultrasonic Proximity & Hold/Release Tests (PRD §17.1, §17.3)
    // =========================================================================

    @Test
    fun testUltrasonicStopImmediateEscalation() {
        val event = createSensorEvent(distanceCm = 45, timestampMs = 1000L)
        val result = riskEngine.onSensorEvent(event)

        assertEquals(RiskLevel.STOP, result.combinedRisk)
        assertEquals(RiskLevel.STOP, result.sensorRisk)
        assertEquals(PathStatus.BLOCKED, result.pathStatus)
        assertEquals(RiskSource.SENSOR, result.primaryHazardSource)
        assertTrue(result.isEscalation)
    }

    @Test
    fun testUltrasonicDeescalationRequiresOneSecondHoldAndMargin() {
        // Step 1: Initial emergency STOP at 40 cm
        riskEngine.onSensorEvent(createSensorEvent(distanceCm = 40, timestampMs = 1000L))

        // Step 2: Distance increases to 70 cm (> 65 cm required for STOP release) at t=1500 ms (release timer starts)
        val heldResult = riskEngine.onSensorEvent(createSensorEvent(distanceCm = 70, timestampMs = 1500L))
        assertEquals("Must hold STOP for at least 1.0s", RiskLevel.STOP, heldResult.sensorRisk)

        // Still held at t=2000 ms (only 500 ms elapsed since release timer started at t=1500 ms)
        val stillHeld = riskEngine.onSensorEvent(createSensorEvent(distanceCm = 70, timestampMs = 2000L))
        assertEquals("Must continue holding STOP", RiskLevel.STOP, stillHeld.sensorRisk)

        // Step 3: At t=2501 ms (> 1000 ms elapsed since t=1500 ms) with safe distance 70 cm, release steps down to SLOW (51..100 cm)
        val releasedResult = riskEngine.onSensorEvent(createSensorEvent(distanceCm = 70, timestampMs = 2501L))
        assertEquals("Must step down to SLOW after hold expires", RiskLevel.SLOW, releasedResult.sensorRisk)
    }

    // =========================================================================
    // 2. Optical Expansion & Looming Detection Tests (PRD §17.1)
    // =========================================================================

    @Test
    fun testLoomingCollisionTriggersApproachingHazardWarning() {
        // Establish track qualification (3 distinct frames with confidence >= 0.40)
        // Box centered in corridor (x: 0.40 to 0.60, y: 0.40 to 0.60, area = 0.04)
        val boxT0 = NormalizedRect(0.40f, 0.40f, 0.60f, 0.60f)
        val det1 = DetectedObject(classId = 0, label = "person", confidence = 0.90f, boundingBox = boxT0)

        riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(det1), timestampMs = 100L))
        riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(det1), timestampMs = 200L))
        riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(det1), timestampMs = 300L))

        // At t=600 ms (500 ms after baseline t=100 ms), box expands rapidly:
        // x: 0.35 to 0.65, y: 0.35 to 0.65, area = 0.30 * 0.30 = 0.09
        // deltaArea = 0.09 - 0.04 = 0.05, prevArea = 0.04, dt = 0.5s -> expansionRate = 0.05 / 0.02 = 2.50 s^-1 >= 0.50 s^-1
        val boxT1 = NormalizedRect(0.35f, 0.35f, 0.65f, 0.65f)
        val detLooming = DetectedObject(classId = 0, label = "person", confidence = 0.90f, boundingBox = boxT1)

        val result = riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(detLooming), timestampMs = 600L))

        assertTrue("Should detect approaching collision hazard", result.isApproachingHazard)
        assertNotNull(result.expansionRate)
        assertTrue(result.expansionRate!! >= 0.50f)
        assertEquals(RiskLevel.SLOW, result.visionRisk)
        assertEquals("person approaching", result.associatedObjectLabel)
        assertEquals(PathStatus.BLOCKED, result.pathStatus)
    }

    @Test
    fun testNearBottomLoomingTriggersEmergencyStop() {
        // Track near bottom of frame: bottom >= 0.85 and area >= 0.20
        // Baseline at t=100 ms: x: 0.30 to 0.70 (0.40), y: 0.40 to 0.90 (0.50) -> area = 0.20, bottom = 0.90
        val boxT0 = NormalizedRect(0.30f, 0.40f, 0.70f, 0.90f)
        val det1 = DetectedObject(classId = 0, label = "person", confidence = 0.90f, boundingBox = boxT0)

        riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(det1), timestampMs = 100L))
        riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(det1), timestampMs = 200L))
        riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(det1), timestampMs = 300L))

        // Frame at t=600 ms (500 ms later): expands by 50%
        // x: 0.25 to 0.75 (0.50), y: 0.35 to 0.95 (0.60) -> area = 0.30, bottom = 0.95 >= 0.85
        // deltaArea = 0.30 - 0.20 = 0.10, prevArea = 0.20, dt = 0.5s -> expansionRate = 1.0 s^-1 >= 0.50 s^-1
        val boxT1 = NormalizedRect(0.25f, 0.35f, 0.75f, 0.95f)
        val detLoomingNear = DetectedObject(classId = 0, label = "person", confidence = 0.90f, boundingBox = boxT1)

        val result = riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(detLoomingNear), timestampMs = 600L))

        // PRD §17.1 Rule 7: Near corridor track growing >= 25% over 0.5s triggers STOP
        assertEquals(RiskLevel.STOP, result.visionRisk)
        assertEquals(RiskLevel.STOP, result.combinedRisk)
        assertTrue(result.isApproachingHazard)
        assertEquals("person approaching", result.associatedObjectLabel)
    }

    @Test
    fun testRecedingTrackDoesNotTriggerLooming() {
        // Baseline at t=100 ms: area = 0.25 * 0.25 = 0.0625
        val boxT0 = NormalizedRect(0.375f, 0.375f, 0.625f, 0.625f)
        val det1 = DetectedObject(classId = 0, label = "person", confidence = 0.90f, boundingBox = boxT0)

        riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(det1), timestampMs = 100L))
        riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(det1), timestampMs = 200L))
        riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(det1), timestampMs = 300L))

        // Frame at t=600 ms: shrinks to 0.10 * 0.10 = 0.01 (person walking away)
        val boxT1 = NormalizedRect(0.45f, 0.45f, 0.55f, 0.55f)
        val detReceding = DetectedObject(classId = 0, label = "person", confidence = 0.90f, boundingBox = boxT1)

        val result = riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(detReceding), timestampMs = 600L))

        assertFalse("Receding object must not trigger looming", result.isApproachingHazard)
        // Qualified persistent corridor track without near/growth triggers AWARENESS
        assertEquals(RiskLevel.AWARENESS, result.visionRisk)
    }

    // =========================================================================
    // 3. Multi-Source Fusion & Path Clearance Tests (PRD §17.1, §17.2)
    // =========================================================================

    @Test
    fun testMultiSourceFusionSensorDominatesOverVision() {
        // Sensor says STOP (35 cm)
        riskEngine.onSensorEvent(createSensorEvent(distanceCm = 35, timestampMs = 1000L))

        // Vision sees stationary obstacle (AWARENESS)
        val box = NormalizedRect(0.4f, 0.4f, 0.6f, 0.6f)
        val det = DetectedObject(classId = 1, label = "chair", confidence = 0.85f, boundingBox = box)
        val result = riskEngine.onPerceptionEvent(createPerceptionEvent(listOf(det), timestampMs = 1000L))

        assertEquals(RiskLevel.STOP, result.combinedRisk)
        assertEquals(RiskLevel.STOP, result.sensorRisk)
        assertEquals(RiskSource.SENSOR, result.primaryHazardSource)
    }

    @Test
    fun testPathClearObservedOnlyAfterOneSecondContinuousClearance() {
        // Step 1: Fresh sensor reading at 200 cm (> 150 cm)
        riskEngine.onSensorEvent(createSensorEvent(distanceCm = 200, timestampMs = 1000L))

        // Step 2: Fresh usable camera frame with no corridor detections at t=1000 ms
        val result1 = riskEngine.onPerceptionEvent(createPerceptionEvent(emptyList(), timestampMs = 1000L))
        assertEquals("Path clearance requires 1.0s continuous observation", PathStatus.UNKNOWN, result1.pathStatus)

        // Step 3: Continues clear at t=1500 ms (500 ms elapsed)
        riskEngine.onSensorEvent(createSensorEvent(distanceCm = 200, timestampMs = 1500L))
        val result2 = riskEngine.onPerceptionEvent(createPerceptionEvent(emptyList(), timestampMs = 1500L))
        assertEquals(PathStatus.UNKNOWN, result2.pathStatus)

        // Step 4: Reaches t=2001 ms (> 1000 ms elapsed)
        riskEngine.onSensorEvent(createSensorEvent(distanceCm = 200, timestampMs = 2001L))
        val result3 = riskEngine.onPerceptionEvent(createPerceptionEvent(emptyList(), timestampMs = 2001L))
        assertEquals(PathStatus.CLEAR_OBSERVED, result3.pathStatus)
    }

    @Test
    fun testWatchdogResetsHeldHazardsOnSustainedStaleness() {
        // Emergency STOP at t=1000 ms
        riskEngine.onSensorEvent(createSensorEvent(distanceCm = 30, timestampMs = 1000L))

        // Watchdog at t=1600 ms (> 500 ms since last sensor reading)
        val watchdogResult = riskEngine.onWatchdogTick(currentTimeMonotonicMs = 1600L)

        assertEquals(RiskLevel.NONE, watchdogResult.sensorRisk)
        assertEquals(RiskLevel.NONE, watchdogResult.combinedRisk)
        assertEquals("Stale sensor cannot confirm clearance", PathStatus.UNKNOWN, watchdogResult.pathStatus)
    }
}
