package dev.navisense.navigation

import dev.navisense.contracts.DetectedObject
import dev.navisense.contracts.NormalizedRect
import kotlin.math.abs

/**
 * Samik-owned history store for already-tracked CameraX detections.
 *
 * CameraXAnalyzer is the single authority that assigns track IDs. The fusion
 * engine only retains the minimum history required for persistence and optical
 * expansion calculations; it never performs a second IoU association pass.
 */
internal class FusionVisionTrackStore(
    private val expiryMs: Long = 500L
) {
    data class Sample(
        val frameId: Long,
        val captureMonotonicMs: Long,
        val confidence: Float,
        val box: NormalizedRect
    )

    data class Track(
        val trackId: Long,
        val classId: Int,
        val label: String,
        val samples: MutableList<Sample> = mutableListOf()
    ) {
        val current: Sample get() = samples.last()

        fun isPersistent(nowCaptureMs: Long): Boolean {
            val cutoff = nowCaptureMs - PERSISTENCE_WINDOW_MS
            return samples.asSequence()
                .filter { it.captureMonotonicMs >= cutoff && it.confidence >= MIN_CONFIDENCE }
                .map { it.frameId }
                .distinct()
                .take(REQUIRED_FRAMES)
                .count() >= REQUIRED_FRAMES
        }

        /** Relative median-area growth over approximately 500 ms. */
        fun areaGrowth(nowCaptureMs: Long): Float? {
            if (samples.size < 2) return null
            val currentArea = median(samples.takeLast(MEDIAN_WINDOW).map { it.box.area })
            if (currentArea <= 0f) return null

            val target = nowCaptureMs - GROWTH_INTERVAL_MS
            val baselineCandidates = samples.filter {
                it.captureMonotonicMs in
                    (target - GROWTH_TOLERANCE_MS)..(target + GROWTH_TOLERANCE_MS)
            }
            if (baselineCandidates.isEmpty()) return null

            val baselineAnchor = baselineCandidates.minByOrNull {
                abs(it.captureMonotonicMs - target)
            } ?: return null
            val baselineIndex = samples.indexOf(baselineAnchor)
            val baselineStart = (baselineIndex - MEDIAN_WINDOW + 1).coerceAtLeast(0)
            val baselineArea = median(
                samples.subList(baselineStart, baselineIndex + 1).map { it.box.area }
            )
            if (baselineArea <= 0f) return null
            return (currentArea - baselineArea) / baselineArea
        }

        fun expansionRatePerSecond(nowCaptureMs: Long): Float? {
            val growth = areaGrowth(nowCaptureMs) ?: return null
            val target = nowCaptureMs - GROWTH_INTERVAL_MS
            val baseline = samples.minByOrNull { abs(it.captureMonotonicMs - target) } ?: return null
            val seconds = (nowCaptureMs - baseline.captureMonotonicMs) / 1000f
            return if (seconds > 0f) growth / seconds else null
        }

        private fun median(values: List<Float>): Float {
            val sorted = values.filter { it > 0f && it.isFinite() }.sorted()
            if (sorted.isEmpty()) return 0f
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 1) sorted[middle]
            else (sorted[middle - 1] + sorted[middle]) / 2f
        }
    }

    private val tracks = linkedMapOf<Long, Track>()
    private var geometryVersion: Int? = null

    fun update(
        frameId: Long,
        captureMonotonicMs: Long,
        newGeometryVersion: Int,
        detections: List<DetectedObject>
    ): List<Track> {
        if (geometryVersion != null && geometryVersion != newGeometryVersion) reset()
        geometryVersion = newGeometryVersion
        tracks.entries.removeAll { captureMonotonicMs - it.value.current.captureMonotonicMs > expiryMs }

        detections.forEach { detection ->
            val trackId = detection.trackId ?: return@forEach
            val existing = tracks[trackId]
            val track = if (
                existing == null || existing.classId != detection.classId || existing.label != detection.label
            ) {
                Track(trackId, detection.classId, detection.label).also { tracks[trackId] = it }
            } else existing

            if (track.samples.any { it.frameId == frameId }) return@forEach
            track.samples += Sample(frameId, captureMonotonicMs, detection.confidence, detection.boundingBox)
            track.samples.removeAll { captureMonotonicMs - it.captureMonotonicMs > HISTORY_MS }
        }
        return tracks.values.toList()
    }

    fun reset() {
        tracks.clear()
        geometryVersion = null
    }

    private companion object {
        const val MIN_CONFIDENCE = 0.30f
        const val REQUIRED_FRAMES = 2
        const val PERSISTENCE_WINDOW_MS = 1_500L
        const val HISTORY_MS = 2_000L
        const val GROWTH_INTERVAL_MS = 500L
        const val GROWTH_TOLERANCE_MS = 150L
        const val MEDIAN_WINDOW = 3
    }
}
