package dev.navisense.tracking

import dev.navisense.contracts.DetectedObject
import dev.navisense.contracts.NormalizedRect
import kotlin.math.abs

/**
 * Snapshot of a tracked object at a specific point in monotonic time.
 */
data class TrackSnapshot(
    val timestampMonotonicMs: Long,
    val boundingBox: NormalizedRect,
    val confidence: Float
)

/**
 * An active visual track maintained across frames.
 */
data class ActiveTrack(
    val trackId: Long,
    val classId: Int,
    val label: String,
    var lastSeenMonotonicMs: Long,
    val firstSeenMonotonicMs: Long,
    val history: MutableList<TrackSnapshot> = mutableListOf()
) {
    val currentBox: NormalizedRect get() = history.last().boundingBox
    val currentConfidence: Float get() = history.last().confidence

    /**
     * Checks if track is qualified under PRD §17.1:
     * Confidence >= 0.40 in at least 3 distinct frames within windowMs (default 1000 ms).
     */
    fun isQualified(currentMonotonicMs: Long, windowMs: Long = 1000L): Boolean {
        val cutoff = currentMonotonicMs - windowMs
        val qualifyingFrames = history
            .filter { it.timestampMonotonicMs >= cutoff && it.confidence >= 0.40f }
            .distinctBy { it.timestampMonotonicMs }
        return qualifyingFrames.size >= 3
    }

    /**
     * Computes area growth rate over approximately 0.5s (+/- 0.15s) per PRD §17.1:
     * (current_area / previous_area) - 1.0
     */
    fun computeAreaGrowth(
        currentMonotonicMs: Long,
        targetIntervalMs: Long = 500L,
        toleranceMs: Long = 150L
    ): Float? {
        if (history.isEmpty()) return null
        val currentArea = currentBox.area
        if (currentArea <= 0f) return null

        val targetTimestamp = currentMonotonicMs - targetIntervalMs
        val minTimestamp = targetTimestamp - toleranceMs
        val maxTimestamp = targetTimestamp + toleranceMs

        // Find snapshot nearest to target timestamp within tolerance window
        val candidates = history.filter { it.timestampMonotonicMs in minTimestamp..maxTimestamp }
        if (candidates.isEmpty()) return null

        val baseline = candidates.minByOrNull { abs(it.timestampMonotonicMs - targetTimestamp) } ?: return null
        val prevArea = baseline.boundingBox.area
        if (prevArea <= 0f) return null

        return (currentArea / prevArea) - 1.0f
    }

    /**
     * Computes normalized optical expansion rate per second:
     * (ΔArea) / (Area_0 * Δt)
     * where Δt is in seconds.
     * Returns null if insufficient history or non-positive baseline area.
     */
    fun computeExpansionRate(
        currentMonotonicMs: Long,
        targetIntervalMs: Long = 500L,
        toleranceMs: Long = 150L
    ): Float? {
        if (history.isEmpty()) return null
        val currentArea = currentBox.area
        if (currentArea <= 0f) return null

        val targetTimestamp = currentMonotonicMs - targetIntervalMs
        val minTimestamp = targetTimestamp - toleranceMs
        val maxTimestamp = targetTimestamp + toleranceMs

        val candidates = history.filter { it.timestampMonotonicMs in minTimestamp..maxTimestamp }
        if (candidates.isEmpty()) return null

        val baseline = candidates.minByOrNull { abs(it.timestampMonotonicMs - targetTimestamp) } ?: return null
        val prevArea = baseline.boundingBox.area
        if (prevArea <= 0f) return null

        val dtSeconds = (currentMonotonicMs - baseline.timestampMonotonicMs) / 1000.0f
        if (dtSeconds <= 0f) return null

        val deltaArea = currentArea - prevArea
        return deltaArea / (prevArea * dtSeconds)
    }

    /**
     * Determines whether this track exhibits rapid approaching optical expansion
     * (looming collision hazard) above the given threshold rate (default 0.50 s^-1).
     */
    fun isApproaching(
        currentMonotonicMs: Long,
        thresholdRate: Float = 0.50f,
        targetIntervalMs: Long = 500L,
        toleranceMs: Long = 150L
    ): Boolean {
        val rate = computeExpansionRate(currentMonotonicMs, targetIntervalMs, toleranceMs) ?: return false
        return rate >= thresholdRate
    }
}

/**
 * Short-lived visual tracker implementing one-to-one IoU matching per PRD §17.1.
 */
class VisualTracker(
    val matchIouThreshold: Float = 0.30f,
    val trackExpiryMs: Long = 500L
) {
    private var nextTrackId = 1L
    private var lastGeometryVersion: Int? = null
    private val activeTracks = mutableListOf<ActiveTrack>()

    val currentActiveTracks: List<ActiveTrack> get() = activeTracks.toList()

    /**
     * Resets all visual history on camera rotation, resolution, or model change.
     */
    fun reset() {
        activeTracks.clear()
        lastGeometryVersion = null
    }

    /**
     * Updates active tracks with new frame detections.
     */
    fun update(
        detections: List<DetectedObject>,
        currentMonotonicMs: Long,
        geometryVersion: Int
    ): List<DetectedObject> {
        // Reset if geometry version changed
        if (lastGeometryVersion != null && lastGeometryVersion != geometryVersion) {
            reset()
        }
        lastGeometryVersion = geometryVersion

        // 1. Expire stale tracks (no match for > 500 ms)
        activeTracks.removeAll { currentMonotonicMs - it.lastSeenMonotonicMs > trackExpiryMs }

        // 2. Perform greedy 1-to-1 matching based on class and highest IoU >= threshold
        val unmatchedTracks = activeTracks.toMutableList()
        val resultDetections = mutableListOf<DetectedObject>()

        // Sort detections by confidence descending
        val sortedDetections = detections.sortedByDescending { it.confidence }

        for (detection in sortedDetections) {
            var bestTrack: ActiveTrack? = null
            var bestIoU = matchIouThreshold

            for (track in unmatchedTracks) {
                if (track.classId == detection.classId) {
                    val iou = track.currentBox.calculateIoU(detection.boundingBox)
                    if (iou >= bestIoU) {
                        bestIoU = iou
                        bestTrack = track
                    }
                }
            }

            if (bestTrack != null) {
                // Matched existing track
                unmatchedTracks.remove(bestTrack)
                bestTrack.lastSeenMonotonicMs = currentMonotonicMs
                bestTrack.history.add(
                    TrackSnapshot(
                        timestampMonotonicMs = currentMonotonicMs,
                        boundingBox = detection.boundingBox,
                        confidence = detection.confidence
                    )
                )
                // Prune history older than 2000 ms
                bestTrack.history.removeAll { currentMonotonicMs - it.timestampMonotonicMs > 2000L }

                resultDetections.add(detection.copy(trackId = bestTrack.trackId))
            } else {
                // Create new track
                val newTrackId = nextTrackId++
                val newTrack = ActiveTrack(
                    trackId = newTrackId,
                    classId = detection.classId,
                    label = detection.label,
                    lastSeenMonotonicMs = currentMonotonicMs,
                    firstSeenMonotonicMs = currentMonotonicMs
                ).apply {
                    history.add(
                        TrackSnapshot(
                            timestampMonotonicMs = currentMonotonicMs,
                            boundingBox = detection.boundingBox,
                            confidence = detection.confidence
                        )
                    )
                }
                activeTracks.add(newTrack)
                resultDetections.add(detection.copy(trackId = newTrackId))
            }
        }

        return resultDetections
    }

    fun getTrack(trackId: Long): ActiveTrack? {
        return activeTracks.find { it.trackId == trackId }
    }
}
