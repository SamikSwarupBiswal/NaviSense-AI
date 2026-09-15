package dev.navisense.camera

import dev.navisense.contracts.FrameQualityStatus
import kotlin.math.sqrt

/**
 * Validates frame image quality per PRD §17.2:
 * Rejects frames whose downsampled grayscale mean is < 10 or standard deviation is < 5 on a 0..255 scale.
 */
object FrameQualityChecker {

    const val MIN_MEAN_THRESHOLD = 10.0
    const val MIN_STDDEV_THRESHOLD = 5.0

    data class QualityReport(
        val mean: Double,
        val stdDev: Double,
        val status: FrameQualityStatus,
        val reason: String? = null
    )

    /**
     * Evaluates a downsampled grayscale pixel array (values 0..255).
     */
    fun evaluate(pixels: IntArray): QualityReport {
        if (pixels.isEmpty()) {
            return QualityReport(
                mean = 0.0,
                stdDev = 0.0,
                status = FrameQualityStatus.UNUSABLE,
                reason = "Empty pixel buffer"
            )
        }

        var sum = 0.0
        for (p in pixels) {
            sum += p
        }
        val mean = sum / pixels.size

        var varianceSum = 0.0
        for (p in pixels) {
            val diff = p - mean
            varianceSum += diff * diff
        }
        val stdDev = sqrt(varianceSum / pixels.size)

        return when {
            mean < MIN_MEAN_THRESHOLD -> QualityReport(
                mean = mean,
                stdDev = stdDev,
                status = FrameQualityStatus.UNUSABLE,
                reason = "Frame too dark/covered (mean=$mean < $MIN_MEAN_THRESHOLD)"
            )
            stdDev < MIN_STDDEV_THRESHOLD -> QualityReport(
                mean = mean,
                stdDev = stdDev,
                status = FrameQualityStatus.UNUSABLE,
                reason = "Frame featureless/occluded (stdDev=$stdDev < $MIN_STDDEV_THRESHOLD)"
            )
            else -> QualityReport(
                mean = mean,
                stdDev = stdDev,
                status = FrameQualityStatus.USABLE,
                reason = null
            )
        }
    }

    /**
     * Evaluates a byte array where bytes represent unsigned 0..255 grayscale values.
     */
    fun evaluate(bytes: ByteArray): QualityReport {
        val pixels = IntArray(bytes.size) { i -> bytes[i].toInt() and 0xFF }
        return evaluate(pixels)
    }
}
