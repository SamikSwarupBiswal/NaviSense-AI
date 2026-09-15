package dev.navisense.navigation

import dev.navisense.contracts.NormalizedBoundingBox
import kotlin.math.max
import kotlin.math.min

/**
 * Walking corridor geometric evaluator adhering to PRD Section 16 & 17.1.
 *
 * Default corridor bounds in upright rear-camera frame:
 * x in [0.30, 0.70] (central 40% horizontal span)
 * y in [0.30, 1.00] (forward walking surface)
 *
 * An obstacle box is in the corridor if:
 * (intersection area / box area) >= 0.20
 */
class WalkingCorridor(
    val left: Float = DEFAULT_LEFT,
    val top: Float = DEFAULT_TOP,
    val right: Float = DEFAULT_RIGHT,
    val bottom: Float = DEFAULT_BOTTOM,
    val minOverlapRatio: Float = DEFAULT_MIN_OVERLAP_RATIO
) {
    companion object {
        const val DEFAULT_LEFT = 0.30f
        const val DEFAULT_TOP = 0.30f
        const val DEFAULT_RIGHT = 0.70f
        const val DEFAULT_BOTTOM = 1.00f
        const val DEFAULT_MIN_OVERLAP_RATIO = 0.20f
    }

    init {
        require(left < right) { "Corridor left must be < right" }
        require(top < bottom) { "Corridor top must be < bottom" }
    }

    /**
     * Calculates the intersection area between the corridor and the bounding box.
     */
    fun calculateIntersectionArea(box: NormalizedBoundingBox): Float {
        val interLeft = max(left, box.left)
        val interTop = max(top, box.top)
        val interRight = min(right, box.right)
        val interBottom = min(bottom, box.bottom)

        if (interLeft >= interRight || interTop >= interBottom) {
            return 0.0f
        }
        return (interRight - interLeft) * (interBottom - interTop)
    }

    /**
     * Determines whether the given bounding box sufficiently intersects the walking corridor.
     */
    fun isCorridorObstacle(box: NormalizedBoundingBox): Boolean {
        if (box.area <= 0.0f) return false
        val intersection = calculateIntersectionArea(box)
        val overlapRatio = intersection / box.area
        return overlapRatio >= minOverlapRatio
    }
}
