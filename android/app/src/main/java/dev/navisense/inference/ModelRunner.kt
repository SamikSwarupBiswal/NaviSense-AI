package dev.navisense.inference

import dev.navisense.contracts.AppVisionMode
import dev.navisense.contracts.MobilePerceptionEvent

/**
 * Metadata defining a model's operational configuration and provenance.
 */
data class ModelMetadata(
    val modelIdentity: String,
    val mode: AppVisionMode,
    val inputWidth: Int,
    val inputHeight: Int,
    val classLabels: List<String>,
    val confidenceThreshold: Float = 0.40f,
    val nmsIouThreshold: Float = 0.45f,
    val modelHashSha256: String = ""
)

/**
 * Common interface for Android model execution.
 * Only one active model may run at any time (PRD §8.3 & §20).
 */
interface ModelRunner {
    val isLoaded: Boolean
    val metadata: ModelMetadata?

    /**
     * Loads the model artifact.
     * Must complete within 5.0 seconds per PRD §13.5.
     */
    fun load(metadata: ModelMetadata): Boolean

    /**
     * Executes inference on the provided frame.
     */
    fun detect(
        framePixels: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        rotationDegrees: Int,
        frameId: Long,
        captureMonotonicMs: Long,
        deliveryMonotonicMs: Long,
        sessionGeneration: Long,
        geometryVersion: Int
    ): MobilePerceptionEvent

    /**
     * Releases model and tensor resources.
     */
    fun close()
}
