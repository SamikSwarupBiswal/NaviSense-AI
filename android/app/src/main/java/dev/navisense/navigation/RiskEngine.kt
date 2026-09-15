package dev.navisense.navigation

import dev.navisense.contracts.FrameQualityStatus
import dev.navisense.contracts.IClock
import dev.navisense.contracts.PathStatus
import dev.navisense.contracts.PerceptionFrameEvent
import dev.navisense.contracts.SensorEvent
import dev.navisense.contracts.SystemMonotonicClock
import kotlin.math.abs
import kotlin.math.max

/**
 * Deterministic multi-source risk engine implementing PRD Section 17.
 *
 * Core responsibilities:
 * 1. Independent sensor and vision severity reducers with highest-severity combination.
 * 2. Ultrasonic proximity (<= 50 cm) produces an immediate STOP (< 100 ms) strictly independent of vision inference.
 * 3. Hold and clearance timers: 1000 ms hold on STOP, 500 ms hold on SLOW; track-ID churn cannot erase a STOP.
 * 4. CLEAR_OBSERVED requirements: continuous distance > 150 cm for >= 1.0s, usable fresh camera frame,
 *    no corridor obstacle >= 0.40 confidence, and no held hazard. Phone-only vision mode has no CLEAR_OBSERVED state.
 * 5. 50 ms watchdog: detects sensor dropouts (> 300 ms) and camera dropouts (> 500 ms), transitioning path status to UNKNOWN.
 * 6. Supports deterministic replay testing via injected [IClock].
 */
class RiskEngine(
    private val clock: IClock = SystemMonotonicClock(),
    private val corridor: WalkingCorridor = WalkingCorridor()
) : IRiskEngine {

    companion object {
        const val STOP_HOLD_DURATION_MS = 1000L
        const val SLOW_HOLD_DURATION_MS = 500L
        const val SENSOR_STALENESS_THRESHOLD_MS = 300L
        const val VISION_STALENESS_THRESHOLD_MS = 500L
        const val SENSOR_CAMERA_SYNC_THRESHOLD_MS = 200L
        const val CONTINUOUS_CLEAR_REQUIRED_MS = 1000L

        // Ultrasonic Distance Thresholds (cm)
        const val ULTRASONIC_STOP_THRESHOLD_CM = 50
        const val ULTRASONIC_SLOW_THRESHOLD_CM = 100
        const val ULTRASONIC_AWARENESS_THRESHOLD_CM = 150

        // Vision Corridor Obstacle Bottom Bounding Box Thresholds (Normalized [0.0, 1.0])
        const val VISION_STOP_BOTTOM_THRESHOLD = 0.70f
        const val VISION_SLOW_BOTTOM_THRESHOLD = 0.50f
        const val MIN_OBSTACLE_CONFIDENCE = 0.40f
    }

    // Sensor State
    private var lastSensorReceiptMs: Long = 0L
    private var rawSensorRisk: RiskLevel = RiskLevel.NONE
    private var lastSensorValidDistanceCm: Int? = null
    private var continuousSensorClearStartMs: Long? = null

    // Vision State
    private var lastPerceptionDeliveryMs: Long = 0L
    private var rawVisionRisk: RiskLevel = RiskLevel.NONE
    private var lastFrameQuality: FrameQualityStatus = FrameQualityStatus.UNUSABLE
    private var activeCorridorLabels: List<String> = emptyList()

    // Hold Timers & Sources
    private var stopHoldUntilMs: Long = 0L
    private var slowHoldUntilMs: Long = 0L
    private var lastStopSource: RiskSource? = null
    private var lastSlowSource: RiskSource? = null

    // Tracking
    private var lastEvaluatedRisk: RiskLevel = RiskLevel.NONE

    override fun onSensorEvent(event: SensorEvent): RiskEvaluationResult {
        val now = event.receiptMonotonicMs
        lastSensorReceiptMs = now

        val wire = event.wireRecord
        if (wire.isValid && wire.distanceCm in 2..400) {
            val dist = wire.distanceCm
            lastSensorValidDistanceCm = dist

            rawSensorRisk = when {
                dist <= ULTRASONIC_STOP_THRESHOLD_CM -> RiskLevel.STOP
                dist <= ULTRASONIC_SLOW_THRESHOLD_CM -> RiskLevel.SLOW
                dist <= ULTRASONIC_AWARENESS_THRESHOLD_CM -> RiskLevel.AWARENESS
                else -> RiskLevel.NONE
            }

            if (dist > ULTRASONIC_AWARENESS_THRESHOLD_CM) {
                if (continuousSensorClearStartMs == null) {
                    continuousSensorClearStartMs = now
                }
            } else {
                continuousSensorClearStartMs = null
            }
        } else {
            // Invalid reading does NOT indicate clear
            rawSensorRisk = RiskLevel.NONE
            lastSensorValidDistanceCm = null
            continuousSensorClearStartMs = null
        }

        if (rawSensorRisk == RiskLevel.STOP) {
            stopHoldUntilMs = max(stopHoldUntilMs, now + STOP_HOLD_DURATION_MS)
            lastStopSource = RiskSource.SENSOR
        } else if (rawSensorRisk == RiskLevel.SLOW) {
            slowHoldUntilMs = max(slowHoldUntilMs, now + SLOW_HOLD_DURATION_MS)
            lastSlowSource = RiskSource.SENSOR
        }

        return evaluate(now)
    }

    override fun onPerceptionEvent(event: PerceptionFrameEvent): RiskEvaluationResult {
        val now = event.deliveryMonotonicMs
        lastPerceptionDeliveryMs = now
        lastFrameQuality = event.qualityStatus

        if (event.qualityStatus == FrameQualityStatus.UNUSABLE) {
            rawVisionRisk = RiskLevel.NONE
            activeCorridorLabels = emptyList()
        } else {
            val corridorObstacles = event.detections.filter { detection ->
                detection.confidence >= MIN_OBSTACLE_CONFIDENCE && corridor.isCorridorObstacle(detection.boundingBox)
            }

            activeCorridorLabels = corridorObstacles.map { it.label }

            rawVisionRisk = if (corridorObstacles.isEmpty()) {
                RiskLevel.NONE
            } else {
                var maxRisk = RiskLevel.AWARENESS
                for (obs in corridorObstacles) {
                    val bottom = obs.boundingBox.bottom
                    val risk = when {
                        bottom >= VISION_STOP_BOTTOM_THRESHOLD -> RiskLevel.STOP
                        bottom >= VISION_SLOW_BOTTOM_THRESHOLD -> RiskLevel.SLOW
                        else -> RiskLevel.AWARENESS
                    }
                    maxRisk = RiskLevel.max(maxRisk, risk)
                }
                maxRisk
            }
        }

        if (rawVisionRisk == RiskLevel.STOP) {
            stopHoldUntilMs = max(stopHoldUntilMs, now + STOP_HOLD_DURATION_MS)
            lastStopSource = RiskSource.VISION
        } else if (rawVisionRisk == RiskLevel.SLOW) {
            slowHoldUntilMs = max(slowHoldUntilMs, now + SLOW_HOLD_DURATION_MS)
            lastSlowSource = RiskSource.VISION
        }

        return evaluate(now)
    }

    override fun onWatchdogTick(currentTimeMonotonicMs: Long): RiskEvaluationResult {
        return evaluate(currentTimeMonotonicMs)
    }

    override fun reset() {
        lastSensorReceiptMs = 0L
        rawSensorRisk = RiskLevel.NONE
        lastSensorValidDistanceCm = null
        continuousSensorClearStartMs = null

        lastPerceptionDeliveryMs = 0L
        rawVisionRisk = RiskLevel.NONE
        lastFrameQuality = FrameQualityStatus.UNUSABLE
        activeCorridorLabels = emptyList()

        stopHoldUntilMs = 0L
        slowHoldUntilMs = 0L
        lastStopSource = null
        lastSlowSource = null

        lastEvaluatedRisk = RiskLevel.NONE
    }

    private fun evaluate(now: Long): RiskEvaluationResult {
        // Staleness checks
        val sensorStale = (lastSensorReceiptMs == 0L) || ((now - lastSensorReceiptMs) > SENSOR_STALENESS_THRESHOLD_MS)
        val visionStale = (lastPerceptionDeliveryMs == 0L) || ((now - lastPerceptionDeliveryMs) > VISION_STALENESS_THRESHOLD_MS)

        val activeSensorRisk = if (sensorStale) RiskLevel.NONE else rawSensorRisk
        val activeVisionRisk = if (visionStale) RiskLevel.NONE else rawVisionRisk

        if (sensorStale) {
            continuousSensorClearStartMs = null
        }

        val instantaneousRisk = RiskLevel.max(activeSensorRisk, activeVisionRisk)

        // Apply holds
        val isStopHeld = now < stopHoldUntilMs
        val isSlowHeld = now < slowHoldUntilMs

        val combinedRisk = when {
            isStopHeld || instantaneousRisk == RiskLevel.STOP -> RiskLevel.STOP
            isSlowHeld || instantaneousRisk == RiskLevel.SLOW -> RiskLevel.SLOW
            else -> instantaneousRisk
        }

        // Determine primary hazard source
        val primarySource: RiskSource? = when (combinedRisk) {
            RiskLevel.NONE -> null
            RiskLevel.STOP -> {
                if (activeSensorRisk == RiskLevel.STOP) {
                    RiskSource.SENSOR
                } else if (activeVisionRisk == RiskLevel.STOP) {
                    RiskSource.VISION
                } else {
                    lastStopSource ?: RiskSource.SENSOR
                }
            }
            RiskLevel.SLOW -> {
                if (activeSensorRisk == RiskLevel.SLOW) {
                    RiskSource.SENSOR
                } else if (activeVisionRisk == RiskLevel.SLOW) {
                    RiskSource.VISION
                } else {
                    lastSlowSource ?: RiskSource.SENSOR
                }
            }
            RiskLevel.AWARENESS -> {
                if (activeSensorRisk.severity >= activeVisionRisk.severity) {
                    RiskSource.SENSOR
                } else {
                    RiskSource.VISION
                }
            }
        }

        // Determine associated visual label if sensor and camera are synchronized <= 200 ms and exactly 1 label
        val isSyncValid = lastSensorReceiptMs > 0L && lastPerceptionDeliveryMs > 0L &&
                abs(lastSensorReceiptMs - lastPerceptionDeliveryMs) <= SENSOR_CAMERA_SYNC_THRESHOLD_MS
        val associatedLabel = if (isSyncValid && activeCorridorLabels.size == 1 && combinedRisk != RiskLevel.NONE) {
            activeCorridorLabels.first()
        } else {
            null
        }

        // Evaluate PathStatus
        val pathStatus: PathStatus = when {
            combinedRisk == RiskLevel.STOP || combinedRisk == RiskLevel.SLOW -> {
                PathStatus.BLOCKED
            }
            // Requirements for CLEAR_OBSERVED:
            // 1. Sensor is active and not stale
            // 2. Sensor distance > 150 cm continuously for >= 1000 ms
            // 3. Vision is active, not stale, and quality is USABLE
            // 4. No corridor obstacles >= 0.40 confidence
            // 5. No active hold timers
            !sensorStale &&
                    lastSensorValidDistanceCm != null &&
                    lastSensorValidDistanceCm!! > ULTRASONIC_AWARENESS_THRESHOLD_CM &&
                    continuousSensorClearStartMs != null &&
                    (now - continuousSensorClearStartMs!!) >= CONTINUOUS_CLEAR_REQUIRED_MS &&
                    !visionStale &&
                    lastFrameQuality == FrameQualityStatus.USABLE &&
                    activeCorridorLabels.isEmpty() &&
                    !isStopHeld &&
                    !isSlowHeld -> {
                PathStatus.CLEAR_OBSERVED
            }
            else -> {
                PathStatus.UNKNOWN
            }
        }

        val isEscalation = combinedRisk.severity > lastEvaluatedRisk.severity
        lastEvaluatedRisk = combinedRisk

        return RiskEvaluationResult(
            combinedRisk = combinedRisk,
            sensorRisk = activeSensorRisk,
            visionRisk = activeVisionRisk,
            pathStatus = pathStatus,
            primaryHazardSource = primarySource,
            associatedObjectLabel = associatedLabel,
            isEscalation = isEscalation,
            timestampMonotonicMs = now
        )
    }
}
