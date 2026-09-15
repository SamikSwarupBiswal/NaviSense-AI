package dev.navisense.camera

import dev.navisense.contracts.NormalizedRect
import kotlin.math.max
import kotlin.math.min

/**
 * Handles mapping of bounding boxes from raw letterboxed model space to normalized upright camera coordinates.
 *
 * @property frameWidth Camera image width in pixels before letterboxing.
 * @property frameHeight Camera image height in pixels before letterboxing.
 * @property modelWidth Model input width in pixels (e.g. 320 or 640).
 * @property modelHeight Model input height in pixels (e.g. 320 or 640).
 * @property rotationDegrees Sensor to upright display rotation (0, 90, 180, 270).
 */
class CoordinateTransformer(
    val frameWidth: Int,
    val frameHeight: Int,
    val modelWidth: Int,
    val modelHeight: Int,
    val rotationDegrees: Int = 0
) {
    private val scale: Float
    private val padX: Float
    private val padY: Float

    init {
        require(frameWidth > 0 && frameHeight > 0) { "Frame dimensions must be positive" }
        require(modelWidth > 0 && modelHeight > 0) { "Model dimensions must be positive" }
        require(rotationDegrees in setOf(0, 90, 180, 270)) { "Rotation must be 0, 90, 180, or 270" }

        val scaleX = modelWidth.toFloat() / frameWidth.toFloat()
        val scaleY = modelHeight.toFloat() / frameHeight.toFloat()
        scale = min(scaleX, scaleY)

        val unpaddedWidth = frameWidth * scale
        val unpaddedHeight = frameHeight * scale

        padX = (modelWidth - unpaddedWidth) / 2f
        padY = (modelHeight - unpaddedHeight) / 2f
    }

    /**
     * Un-letterboxes coordinates from model space into original frame pixels.
     */
    fun unletterbox(
        modelX1: Float,
        modelY1: Float,
        modelX2: Float,
        modelY2: Float
    ): FloatArray {
        val x1 = ((modelX1 - padX) / scale).coerceIn(0f, frameWidth.toFloat())
        val y1 = ((modelY1 - padY) / scale).coerceIn(0f, frameHeight.toFloat())
        val x2 = ((modelX2 - padX) / scale).coerceIn(0f, frameWidth.toFloat())
        val y2 = ((modelY2 - padY) / scale).coerceIn(0f, frameHeight.toFloat())

        return floatArrayOf(min(x1, x2), min(y1, y2), max(x1, x2), max(y1, y2))
    }

    /**
     * Transforms raw model bounding box to normalized [0, 1] upright coordinates.
     */
    fun toNormalizedUpright(
        modelX1: Float,
        modelY1: Float,
        modelX2: Float,
        modelY2: Float
    ): NormalizedRect? {
        val unletterboxed = unletterbox(modelX1, modelY1, modelX2, modelY2)
        val fx1 = unletterboxed[0]
        val fy1 = unletterboxed[1]
        val fx2 = unletterboxed[2]
        val fy2 = unletterboxed[3]

        if (fx2 <= fx1 || fy2 <= fy1) {
            return null
        }

        // Normalize to [0, 1] relative to sensor frame
        val nx1 = fx1 / frameWidth.toFloat()
        val ny1 = fy1 / frameHeight.toFloat()
        val nx2 = fx2 / frameWidth.toFloat()
        val ny2 = fy2 / frameHeight.toFloat()

        // Apply upright display rotation
        val upright = when (rotationDegrees) {
            0 -> NormalizedRect(left = nx1, top = ny1, right = nx2, bottom = ny2)
            90 -> NormalizedRect(
                left = 1.0f - ny2,
                top = nx1,
                right = 1.0f - ny1,
                bottom = nx2
            )
            180 -> NormalizedRect(
                left = 1.0f - nx2,
                top = 1.0f - ny2,
                right = 1.0f - nx1,
                bottom = 1.0f - ny1
            )
            270 -> NormalizedRect(
                left = ny1,
                top = 1.0f - nx2,
                right = ny2,
                bottom = 1.0f - nx1
            )
            else -> NormalizedRect(left = nx1, top = ny1, right = nx2, bottom = ny2)
        }

        val clamped = upright.clamped()
        return if (clamped.area > 0f) clamped else null
    }
}
