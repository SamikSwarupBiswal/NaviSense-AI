package dev.navisense.inference

import dev.navisense.camera.CoordinateTransformer
import dev.navisense.camera.FrameQualityChecker
import dev.navisense.contracts.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Interface allowing pluggable backend inference (TFLite interpreter, NCNN, or test fixture).
 */
fun interface InferenceBackend {
    fun runInference(
        framePixels: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        modelWidth: Int,
        modelHeight: Int
    ): List<RawDetection>
}

/**
 * Raw detection emitted by the model in letterboxed pixel coordinates.
 */
data class RawDetection(
    val classId: Int,
    val confidence: Float,
    val boxX1: Float,
    val boxY1: Float,
    val boxX2: Float,
    val boxY2: Float
)

/**
 * Thread-safe ModelRunner implementation for YOLO detection on Android.
 */
class YoloModelRunner(
    private val backend: InferenceBackend? = null
) : ModelRunner {

    private val loaded = AtomicBoolean(false)
    private var currentMetadata: ModelMetadata? = null

    override val isLoaded: Boolean get() = loaded.get()
    override val metadata: ModelMetadata? get() = currentMetadata

    @Synchronized
    override fun load(metadata: ModelMetadata): Boolean {
        loaded.set(false)
        currentMetadata = null
        currentMetadata = metadata
        loaded.set(true)
        return true
    }

    override fun detect(
        framePixels: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        rotationDegrees: Int,
        frameId: Long,
        captureMonotonicMs: Long,
        deliveryMonotonicMs: Long,
        sessionGeneration: Long,
        geometryVersion: Int
    ): MobilePerceptionEvent {
        val meta = currentMetadata
        if (!loaded.get() || meta == null) {
            return MobilePerceptionEvent(
                sessionGeneration = sessionGeneration,
                mode = AppVisionMode.OFF,
                frameId = frameId,
                captureMonotonicMs = captureMonotonicMs,
                deliveryMonotonicMs = deliveryMonotonicMs,
                geometryVersion = geometryVersion,
                modelIdentity = "none",
                qualityStatus = FrameQualityStatus.UNUSABLE,
                detections = emptyList(),
                errorMessage = "Model is not loaded"
            )
        }

        // 1. Image Quality Check per PRD §17.2
        val qualityReport = FrameQualityChecker.evaluate(framePixels)
        if (qualityReport.status == FrameQualityStatus.UNUSABLE) {
            return MobilePerceptionEvent(
                sessionGeneration = sessionGeneration,
                mode = meta.mode,
                frameId = frameId,
                captureMonotonicMs = captureMonotonicMs,
                deliveryMonotonicMs = deliveryMonotonicMs,
                geometryVersion = geometryVersion,
                modelIdentity = meta.modelIdentity,
                qualityStatus = FrameQualityStatus.UNUSABLE,
                detections = emptyList(),
                errorMessage = qualityReport.reason
            )
        }

        // 2. Rotate the sensor-oriented CameraX pixels upright before inference.
        // Training images are upright; rotating only the output boxes makes the
        // detector see sideways people and severely damages class confidence.
        val uprightFrame = orientFrame(framePixels, frameWidth, frameHeight, rotationDegrees)

        // 3. Run backend inference (or return empty if no backend configured)
        val rawDetections = backend?.runInference(
            framePixels = uprightFrame.pixels,
            frameWidth = uprightFrame.width,
            frameHeight = uprightFrame.height,
            modelWidth = meta.inputWidth,
            modelHeight = meta.inputHeight
        ) ?: emptyList()

        // 4. The backend now sees upright pixels, so only undo letterboxing.
        val transformer = CoordinateTransformer(
            frameWidth = uprightFrame.width,
            frameHeight = uprightFrame.height,
            modelWidth = meta.inputWidth,
            modelHeight = meta.inputHeight,
            rotationDegrees = 0
        )

        val detections = mutableListOf<DetectedObject>()
        for (raw in rawDetections) {
            if (raw.classId !in meta.classLabels.indices) continue
            if (!raw.confidence.isFinite() || raw.confidence !in meta.confidenceThreshold..1f) continue
            if (!raw.boxX1.isFinite() || !raw.boxY1.isFinite() || !raw.boxX2.isFinite() || !raw.boxY2.isFinite()) continue

            val uprightRect = transformer.toNormalizedUpright(
                modelX1 = raw.boxX1,
                modelY1 = raw.boxY1,
                modelX2 = raw.boxX2,
                modelY2 = raw.boxY2
            ) ?: continue

            val label = meta.classLabels[raw.classId]

            detections.add(
                DetectedObject(
                    classId = raw.classId,
                    label = label,
                    confidence = raw.confidence,
                    boundingBox = uprightRect
                )
            )
        }

        return MobilePerceptionEvent(
            sessionGeneration = sessionGeneration,
            mode = meta.mode,
            frameId = frameId,
            captureMonotonicMs = captureMonotonicMs,
            deliveryMonotonicMs = deliveryMonotonicMs,
            geometryVersion = geometryVersion,
            modelIdentity = meta.modelIdentity,
            qualityStatus = qualityReport.status,
            detections = detections
        )
    }

    @Synchronized
    override fun close() {
        loaded.set(false)
        currentMetadata = null
        (backend as? AutoCloseable)?.close()
    }

    private data class OrientedFrame(val pixels: ByteArray, val width: Int, val height: Int)

    private fun orientFrame(pixels: ByteArray, width: Int, height: Int, rotationDegrees: Int): OrientedFrame {
        if (rotationDegrees == 0) return OrientedFrame(pixels, width, height)
        val channels = when (pixels.size) {
            width * height -> 1
            width * height * 3 -> 3
            else -> throw IllegalArgumentException("Pixel buffer does not match frame dimensions")
        }
        val outputWidth = if (rotationDegrees == 90 || rotationDegrees == 270) height else width
        val outputHeight = if (rotationDegrees == 90 || rotationDegrees == 270) width else height
        val output = ByteArray(pixels.size)

        for (sourceY in 0 until height) {
            for (sourceX in 0 until width) {
                val destination = when (rotationDegrees) {
                    90 -> Pair(height - 1 - sourceY, sourceX)
                    180 -> Pair(width - 1 - sourceX, height - 1 - sourceY)
                    270 -> Pair(sourceY, width - 1 - sourceX)
                    else -> throw IllegalArgumentException("Unsupported rotation: $rotationDegrees")
                }
                val sourceIndex = (sourceY * width + sourceX) * channels
                val destinationIndex = (destination.second * outputWidth + destination.first) * channels
                for (channel in 0 until channels) output[destinationIndex + channel] = pixels[sourceIndex + channel]
            }
        }
        return OrientedFrame(output, outputWidth, outputHeight)
    }
}
