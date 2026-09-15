package dev.navisense.contracts

import kotlin.math.max
import kotlin.math.min

/**
 * Represents a bounding box in normalized [0.0, 1.0] coordinate space
 * relative to the upright, unletterboxed display orientation.
 */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(!left.isNaN() && !top.isNaN() && !right.isNaN() && !bottom.isNaN()) {
            "NormalizedRect coordinates must not be NaN"
        }
    }

    val width: Float get() = max(0f, right - left)
    val height: Float get() = max(0f, bottom - top)
    val area: Float get() = width * height
    val centerX: Float get() = left + width / 2.0f
    val centerY: Float get() = top + height / 2.0f

    /**
     * Calculates Intersection over Union (IoU) with another bounding box.
     */
    fun calculateIoU(other: NormalizedRect): Float {
        val interLeft = max(this.left, other.left)
        val interTop = max(this.top, other.top)
        val interRight = min(this.right, other.right)
        val interBottom = min(this.bottom, other.bottom)

        val interWidth = max(0f, interRight - interLeft)
        val interHeight = max(0f, interBottom - interTop)
        val interArea = interWidth * interHeight

        if (interArea <= 0f) return 0f

        val unionArea = this.area + other.area - interArea
        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    /**
     * Clamps box coordinates strictly into the [0.0, 1.0] interval.
     */
    fun clamped(): NormalizedRect {
        val cLeft = left.coerceIn(0f, 1f)
        val cTop = top.coerceIn(0f, 1f)
        val cRight = right.coerceIn(0f, 1f)
        val cBottom = bottom.coerceIn(0f, 1f)
        return NormalizedRect(
            left = min(cLeft, cRight),
            top = min(cTop, cBottom),
            right = max(cLeft, cRight),
            bottom = max(cTop, cBottom)
        )
    }
}

/**
 * Single detected object emitted by perception layer.
 */
data class DetectedObject(
    val classId: Int,
    val label: String,
    val confidence: Float,
    val boundingBox: NormalizedRect,
    val trackId: Long? = null
)

/**
 * Quality assessment of a captured frame based on PRD §17.2.
 */
enum class FrameQualityStatus {
    USABLE,
    DEGRADED,
    UNUSABLE
}

/**
 * Active vision system mode.
 */
enum class AppVisionMode {
    MOBILITY,
    LOCATE_SEARCH,
    OFF
}

/**
 * Handoff H2: Mobile perception event passed from Samik (Perception) to Rishav (Risk Engine).
 * Must not contain speech or direct risk decisions.
 */
data class MobilePerceptionEvent(
    val sessionGeneration: Long,
    val mode: AppVisionMode,
    val frameId: Long,
    val captureMonotonicMs: Long,
    val deliveryMonotonicMs: Long,
    val geometryVersion: Int,
    val modelIdentity: String,
    val qualityStatus: FrameQualityStatus,
    val detections: List<DetectedObject>,
    val errorMessage: String? = null
)
