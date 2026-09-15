package dev.navisense.navigation

import dev.navisense.contracts.*
import kotlin.math.abs

/** Samik-owned deterministic ultrasonic + YOLO fusion engine. */
class RiskEngine(
    val corridor: WalkingCorridor = WalkingCorridor(),
    val clock: IClock = SystemMonotonicClock()
) : IRiskEngine {
    private val visionTracks = FusionVisionTrackStore()
    private var activeConnectionId: Long? = null
    private var lastSensorSequence: Long? = null
    private var lastSensorUptime: Long? = null
    private var lastSensorEvent: SensorEvent? = null
    private var lastUsableSensorEvent: SensorEvent? = null
    private var lastPerceptionEvent: MobilePerceptionEvent? = null
    private var lastUsableCameraDeliveryMs: Long? = null
    private var lastVisionSessionGeneration: Long? = null
    private var lastVisionFrameId: Long? = null
    private var lastVisionCaptureMs: Long? = null
    private var lastVisionModelIdentity: String? = null
    private var lastVisionGeometryVersion: Int? = null
    private var heldSensorRisk = RiskLevel.NONE
    private var sensorReleaseStartMs: Long? = null
    private var sensorLossStartMs: Long? = null
    private var heldVisionRisk = RiskLevel.NONE
    private var visionReleaseStartMs: Long? = null
    private var clearanceStartMs: Long? = null
    private var previousCombinedRisk = RiskLevel.NONE
    private var tentativeCorridorCandidate = false
    private var associatedObjectLabel: String? = null
    private var approachingHazard = false
    private var expansionRate: Float? = null

    @Synchronized
    override fun onSensorEvent(event: SensorEvent): RiskEvaluationResult {
        val now = event.receiptMonotonicMs
        if (!acceptSensorOrdering(event)) return buildResult(now)
        lastSensorEvent = event
        val valid = event.wireRecord.version == 1 && event.wireRecord.isValid &&
            event.wireRecord.distanceCm in 2..400
        val critical = valid && event.wireRecord.distanceCm <= 50
        val normallyUsable = valid && event.sensorHealth == SensorHealth.STREAMING
        if (normallyUsable || critical) {
            lastUsableSensorEvent = event
            sensorLossStartMs = null
            updateSensorRisk(rawSensorRisk(event.wireRecord.distanceCm), event, now)
        } else markSensorUnavailable(now)
        return buildResult(now)
    }

    @Synchronized
    override fun onPerceptionEvent(event: PerceptionFrameEvent): RiskEvaluationResult {
        val now = event.deliveryMonotonicMs
        if (!acceptVisionOrdering(event)) return buildResult(now)
        lastPerceptionEvent = event
        val usable = event.mode == AppVisionMode.MOBILITY &&
            event.qualityStatus == FrameQualityStatus.USABLE && event.errorMessage == null &&
            event.captureMonotonicMs >= 0L && event.deliveryMonotonicMs >= event.captureMonotonicMs &&
            event.deliveryMonotonicMs - event.captureMonotonicMs <= CAMERA_CAPTURE_FRESH_MS
        if (!usable) {
            tentativeCorridorCandidate = false
            clearanceStartMs = null
            return buildResult(now)
        }
        lastUsableCameraDeliveryMs = now
        tentativeCorridorCandidate = event.detections.any {
            it.confidence >= MIN_VISION_CONFIDENCE && validBox(it.boundingBox) &&
                corridor.isCorridorObstacle(it.boundingBox)
        }
        if (tentativeCorridorCandidate) clearanceStartMs = null

        val tracks = visionTracks.update(
            event.frameId, event.captureMonotonicMs, event.geometryVersion,
            event.detections.filter { it.confidence >= MIN_VISION_CONFIDENCE && validBox(it.boundingBox) }
        )
        var rawVisionRisk = RiskLevel.NONE
        var strongestTrack: FusionVisionTrackStore.Track? = null
        var strongestGrowth: Float? = null
        val persistentCorridorTracks = mutableListOf<FusionVisionTrackStore.Track>()
        tracks.forEach { track ->
            if (!track.isPersistent(event.captureMonotonicMs) ||
                !corridor.isCorridorObstacle(track.current.box)) return@forEach
            persistentCorridorTracks += track
            val growth = track.areaGrowth(event.captureMonotonicMs)
            val isApproaching = growth != null && growth >= MIN_AREA_GROWTH
            val box = track.current.box
            val nearBottom = box.bottom >= NEAR_BOTTOM && box.area >= NEAR_AREA
            val risk = when {
                isApproaching && nearBottom -> RiskLevel.STOP
                isApproaching || nearBottom -> RiskLevel.SLOW
                else -> RiskLevel.AWARENESS
            }
            if (risk.severity > rawVisionRisk.severity) {
                rawVisionRisk = risk
                strongestTrack = track
                strongestGrowth = growth
            }
        }
        approachingHazard = strongestGrowth != null && strongestGrowth >= MIN_AREA_GROWTH
        expansionRate = strongestTrack?.expansionRatePerSecond(event.captureMonotonicMs)
        associatedObjectLabel = associateLabel(persistentCorridorTracks, event.captureMonotonicMs)
        updateVisionRisk(rawVisionRisk, now)
        return buildResult(now)
    }

    @Synchronized
    override fun onWatchdogTick(currentTimeMonotonicMs: Long): RiskEvaluationResult {
        val sensorAge = lastSensorEvent?.let { currentTimeMonotonicMs - it.receiptMonotonicMs }
        if (sensorAge == null || sensorAge > SENSOR_FRESH_MS) markSensorUnavailable(currentTimeMonotonicMs)
        val cameraAge = lastUsableCameraDeliveryMs?.let { currentTimeMonotonicMs - it }
        if (cameraAge == null || cameraAge > CAMERA_DELIVERY_DEADLINE_MS) {
            heldVisionRisk = RiskLevel.NONE
            visionReleaseStartMs = null
            tentativeCorridorCandidate = false
            associatedObjectLabel = null
            approachingHazard = false
            expansionRate = null
            clearanceStartMs = null
        }
        return buildResult(currentTimeMonotonicMs)
    }

    @Synchronized
    override fun reset() {
        activeConnectionId = null; lastSensorSequence = null; lastSensorUptime = null
        lastSensorEvent = null; lastUsableSensorEvent = null; lastPerceptionEvent = null
        lastUsableCameraDeliveryMs = null; lastVisionSessionGeneration = null
        lastVisionFrameId = null; lastVisionCaptureMs = null; lastVisionModelIdentity = null
        lastVisionGeometryVersion = null; heldSensorRisk = RiskLevel.NONE
        sensorReleaseStartMs = null; sensorLossStartMs = null; heldVisionRisk = RiskLevel.NONE
        visionReleaseStartMs = null; clearanceStartMs = null; previousCombinedRisk = RiskLevel.NONE
        tentativeCorridorCandidate = false; associatedObjectLabel = null
        approachingHazard = false; expansionRate = null; visionTracks.reset()
    }

    private fun acceptSensorOrdering(event: SensorEvent): Boolean {
        if (activeConnectionId != event.connectionId) {
            activeConnectionId = event.connectionId
            lastSensorSequence = null; lastSensorUptime = null; lastSensorEvent = null
            lastUsableSensorEvent = null; heldSensorRisk = RiskLevel.NONE
            sensorReleaseStartMs = null; sensorLossStartMs = null; clearanceStartMs = null
        }
        val previousSequence = lastSensorSequence
        val previousUptime = lastSensorUptime
        if (previousSequence != null && previousUptime != null) {
            val sequenceDelta = unsignedDelta(event.wireRecord.sequenceNumber, previousSequence)
            val uptimeDelta = unsignedDelta(event.wireRecord.deviceUptimeMs, previousUptime)
            if (sequenceDelta == 0L || sequenceDelta >= HALF_UINT32 || uptimeDelta >= HALF_UINT32) return false
        }
        lastSensorSequence = event.wireRecord.sequenceNumber
        lastSensorUptime = event.wireRecord.deviceUptimeMs
        return true
    }

    private fun acceptVisionOrdering(event: MobilePerceptionEvent): Boolean {
        val sessionChanged = lastVisionSessionGeneration != event.sessionGeneration
        val modelChanged = lastVisionModelIdentity != null && lastVisionModelIdentity != event.modelIdentity
        val geometryChanged = lastVisionGeometryVersion != null && lastVisionGeometryVersion != event.geometryVersion
        if (sessionChanged || modelChanged || geometryChanged) {
            visionTracks.reset(); heldVisionRisk = RiskLevel.NONE; visionReleaseStartMs = null
            clearanceStartMs = null; lastVisionFrameId = null; lastVisionCaptureMs = null
        }
        if (!sessionChanged) {
            if (lastVisionFrameId != null && event.frameId <= lastVisionFrameId!!) return false
            if (lastVisionCaptureMs != null && event.captureMonotonicMs <= lastVisionCaptureMs!!) return false
        }
        lastVisionSessionGeneration = event.sessionGeneration
        lastVisionModelIdentity = event.modelIdentity
        lastVisionGeometryVersion = event.geometryVersion
        lastVisionFrameId = event.frameId
        lastVisionCaptureMs = event.captureMonotonicMs
        return true
    }

    private fun updateSensorRisk(raw: RiskLevel, event: SensorEvent, now: Long) {
        if (raw.severity > heldSensorRisk.severity) {
            heldSensorRisk = raw; sensorReleaseStartMs = null; return
        }
        if (raw.severity == heldSensorRisk.severity) { sensorReleaseStartMs = null; return }
        val releaseDistance = when (heldSensorRisk) {
            RiskLevel.STOP -> 65; RiskLevel.SLOW -> 115
            RiskLevel.AWARENESS -> 165; RiskLevel.NONE -> 0
        }
        if (event.wireRecord.distanceCm > releaseDistance) {
            val started = sensorReleaseStartMs
            if (started == null) sensorReleaseStartMs = now
            else if (now - started >= RELEASE_HOLD_MS) {
                heldSensorRisk = raw; sensorReleaseStartMs = null
            }
        } else sensorReleaseStartMs = null
    }

    private fun markSensorUnavailable(now: Long) {
        lastUsableSensorEvent = null; sensorReleaseStartMs = null; clearanceStartMs = null
        if (heldSensorRisk == RiskLevel.NONE) return
        val lossStart = sensorLossStartMs
        if (lossStart == null) sensorLossStartMs = now
        else if (now - lossStart > SENSOR_LOSS_HOLD_MS) {
            heldSensorRisk = RiskLevel.NONE; sensorLossStartMs = null
        }
    }

    private fun updateVisionRisk(raw: RiskLevel, now: Long) {
        if (raw.severity > heldVisionRisk.severity) {
            heldVisionRisk = raw; visionReleaseStartMs = null
        } else if (raw.severity < heldVisionRisk.severity) {
            val started = visionReleaseStartMs
            if (started == null) visionReleaseStartMs = now
            else if (now - started >= RELEASE_HOLD_MS) {
                heldVisionRisk = raw; visionReleaseStartMs = null
            }
        } else visionReleaseStartMs = null
    }

    private fun associateLabel(tracks: List<FusionVisionTrackStore.Track>, cameraCaptureMs: Long): String? {
        if (tracks.size != 1) return null
        val sensor = lastUsableSensorEvent ?: return null
        if (abs(sensor.receiptMonotonicMs - cameraCaptureMs) > ASSOCIATION_WINDOW_MS) return null
        return tracks.single().label
    }

    private fun buildResult(now: Long): RiskEvaluationResult {
        val combined = RiskLevel.max(heldSensorRisk, heldVisionRisk)
        val source = when {
            heldSensorRisk.severity >= heldVisionRisk.severity && heldSensorRisk != RiskLevel.NONE -> RiskSource.SENSOR
            heldVisionRisk != RiskLevel.NONE -> RiskSource.VISION
            else -> null
        }
        val path = evaluatePathStatus(combined, now)
        val escalated = combined.severity > previousCombinedRisk.severity
        previousCombinedRisk = combined
        return RiskEvaluationResult(combined, heldSensorRisk, heldVisionRisk, path, source,
            associatedObjectLabel, escalated, now, approachingHazard, expansionRate)
    }

    private fun evaluatePathStatus(combined: RiskLevel, now: Long): PathStatus {
        if (combined != RiskLevel.NONE) { clearanceStartMs = null; return PathStatus.BLOCKED }
        val sensor = lastUsableSensorEvent
        val sensorClear = sensor != null && sensor.sensorHealth == SensorHealth.STREAMING &&
            sensor.wireRecord.distanceCm > 150 && now - sensor.receiptMonotonicMs <= SENSOR_FRESH_MS
        val cameraClear = lastPerceptionEvent?.let {
            it.mode == AppVisionMode.MOBILITY && it.qualityStatus == FrameQualityStatus.USABLE &&
                it.errorMessage == null && lastUsableCameraDeliveryMs != null &&
                now - lastUsableCameraDeliveryMs!! <= CAMERA_DELIVERY_DEADLINE_MS
        } == true
        val noHolds = sensorReleaseStartMs == null && visionReleaseStartMs == null && sensorLossStartMs == null
        if (sensorClear && cameraClear && !tentativeCorridorCandidate && noHolds) {
            val started = clearanceStartMs
            if (started == null) clearanceStartMs = now
            else if (now - started >= CLEAR_HOLD_MS) return PathStatus.CLEAR_OBSERVED
        } else clearanceStartMs = null
        return PathStatus.UNKNOWN
    }

    private fun rawSensorRisk(distanceCm: Int) = when (distanceCm) {
        in 2..50 -> RiskLevel.STOP; in 51..100 -> RiskLevel.SLOW
        in 101..150 -> RiskLevel.AWARENESS; else -> RiskLevel.NONE
    }
    private fun validBox(box: NormalizedRect) = box.left.isFinite() && box.top.isFinite() &&
        box.right.isFinite() && box.bottom.isFinite() && box.left >= 0f && box.top >= 0f &&
        box.right <= 1f && box.bottom <= 1f && box.area > 0f
    private fun unsignedDelta(current: Long, previous: Long) = (current - previous).mod(UINT32)

    private companion object {
        const val UINT32 = 1L shl 32; const val HALF_UINT32 = 1L shl 31
        const val SENSOR_FRESH_MS = 300L; const val CAMERA_CAPTURE_FRESH_MS = 500L
        const val CAMERA_DELIVERY_DEADLINE_MS = 1_000L; const val ASSOCIATION_WINDOW_MS = 200L
        const val RELEASE_HOLD_MS = 1_000L; const val SENSOR_LOSS_HOLD_MS = 1_000L
        const val CLEAR_HOLD_MS = 1_000L; const val MIN_VISION_CONFIDENCE = 0.40f
        const val MIN_AREA_GROWTH = 0.25f; const val NEAR_BOTTOM = 0.85f
        const val NEAR_AREA = 0.20f
    }
}
