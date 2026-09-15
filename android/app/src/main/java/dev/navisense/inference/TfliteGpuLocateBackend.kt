package dev.navisense.inference

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

/**
 * High-performance hardware-accelerated TFLite GPU inference backend for NaviSense Locate (keys, wallet).
 *
 * Designed to satisfy PRD §20 real-time requirements (>= 4 FPS, p95 <= 250 ms pipeline latency,
 * 3 matching confirmed frames within 1000 ms).
 * Fails visibly if GPU acceleration is unavailable (strictly no silent CPU fallback).
 */
class TfliteGpuLocateBackend(
    private val modelFile: File,
    private val numClasses: Int = 2,
    private val confThreshold: Float = 0.25f,
    private val iouThreshold: Float = 0.45f,
    private val requireGpu: Boolean = true
) : InferenceBackend, AutoCloseable {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    // Pre-allocated direct NIO buffers (zero per-frame GC allocations)
    private val numChannels: Int = 4 + numClasses
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(1 * 640 * 640 * 3 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val inputFloatBuffer: FloatBuffer = inputBuffer.asFloatBuffer()

    private val outputBuffer: ByteBuffer = ByteBuffer.allocateDirect(1 * numChannels * 8400 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val outputFloatBuffer: FloatBuffer = outputBuffer.asFloatBuffer()

    // Output array cache for reading out tensor data without reallocations
    private val outputArray = FloatArray(1 * numChannels * 8400)

    var completedForwardPasses: Int = 0
        private set

    init {
        loadModel()
    }

    private fun loadModel() {
        require(modelFile.exists() && modelFile.canRead()) {
            "Locate model file does not exist or cannot be read: ${modelFile.absolutePath}"
        }

        val mappedBuffer = FileInputStream(modelFile).channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, 0, modelFile.length())
        }

        val options = Interpreter.Options()
        if (requireGpu) {
            try {
                val delegateOptions = GpuDelegate.Options().apply {
                    setPrecisionLossAllowed(true) // FP16 computation on GPU
                    setQuantizedModelsAllowed(false)
                }
                val delegate = GpuDelegate(delegateOptions)
                options.addDelegate(delegate)
                gpuDelegate = delegate
                Log.i(TAG, "TFLite GpuDelegate initialized successfully for ${modelFile.name}")
            } catch (failure: Throwable) {
                gpuDelegate?.close()
                gpuDelegate = null
                throw IllegalStateException(
                    "TFLite GPU Delegate initialization failed: ${failure.message}. " +
                        "PRD §20 requires hardware GPU acceleration for real-time confirmation; silent CPU fallback is forbidden.",
                    failure
                )
            }
        }
        options.setNumThreads(4)

        try {
            val interp = Interpreter(mappedBuffer, options)
            interp.allocateTensors()
            interpreter = interp
            Log.i(
                TAG,
                "TFLite model loaded: input=${interp.getInputTensor(0).shape().contentToString()} " +
                    "output=${interp.getOutputTensor(0).shape().contentToString()}"
            )
        } catch (failure: Throwable) {
            gpuDelegate?.close()
            gpuDelegate = null
            throw IllegalStateException("Failed to create TFLite Interpreter: ${failure.message}", failure)
        }

        // Run warmup iterations to compile OpenCL / Vulkan shaders before the first camera frame
        warmup(5)
    }

    private fun warmup(iterations: Int) {
        val interp = interpreter ?: return
        inputBuffer.clear()
        while (inputBuffer.hasRemaining()) {
            inputBuffer.put(0.toByte())
        }
        inputBuffer.rewind()
        for (i in 0 until iterations) {
            outputBuffer.clear()
            interp.run(inputBuffer, outputBuffer)
        }
        Log.i(TAG, "Completed $iterations GPU warmup passes successfully")
    }

    override fun runInference(
        framePixels: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        modelWidth: Int,
        modelHeight: Int
    ): List<RawDetection> = runInference(framePixels, frameWidth, frameHeight, modelWidth, modelHeight, 0)

    override fun runInference(
        framePixels: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        modelWidth: Int,
        modelHeight: Int,
        rotationDegrees: Int
    ): List<RawDetection> {
        val interp = checkNotNull(interpreter) { "Model is closed" }

        val t0 = System.nanoTime()

        // 1. Direct letterbox into pre-allocated FloatBuffer (NHWC format)
        writeLetterboxNhwc(
            framePixels = framePixels,
            frameWidth = frameWidth,
            frameHeight = frameHeight,
            modelWidth = modelWidth,
            modelHeight = modelHeight,
            rotationDegrees = rotationDegrees
        )
        val tPreprocess = System.nanoTime()

        // 2. Hardware-accelerated GPU forward pass
        inputBuffer.rewind()
        outputBuffer.rewind()
        interp.run(inputBuffer, outputBuffer)
        completedForwardPasses++
        val tInference = System.nanoTime()

        // 3. Read output tensor into array and decode detections
        outputFloatBuffer.rewind()
        outputFloatBuffer.get(outputArray)

        val candidates = mutableListOf<RawDetection>()
        val numAnchors = 8400
        var maximumScore = -Float.MAX_VALUE

        for (i in 0 until numAnchors) {
            var bestClass = -1
            var bestScore = -Float.MAX_VALUE

            for (c in 0 until numClasses) {
                val score = outputArray[(4 + c) * numAnchors + i]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }
            if (bestScore > maximumScore) maximumScore = bestScore

            if (bestScore >= confThreshold && bestClass >= 0) {
                val cx = outputArray[0 * numAnchors + i]
                val cy = outputArray[1 * numAnchors + i]
                val w = outputArray[2 * numAnchors + i]
                val h = outputArray[3 * numAnchors + i]

                val x1 = (cx - w / 2.0f).coerceIn(0f, modelWidth.toFloat())
                val y1 = (cy - h / 2.0f).coerceIn(0f, modelHeight.toFloat())
                val x2 = (cx + w / 2.0f).coerceIn(0f, modelWidth.toFloat())
                val y2 = (cy + h / 2.0f).coerceIn(0f, modelHeight.toFloat())

                candidates.add(RawDetection(bestClass, bestScore, x1, y1, x2, y2))
            }
        }

        val detections = applyNms(candidates, iouThreshold)
        val tTotal = System.nanoTime()

        val prepMs = (tPreprocess - t0) / 1_000_000L
        val inferMs = (tInference - tPreprocess) / 1_000_000L
        val totalMs = (tTotal - t0) / 1_000_000L

        if (completedForwardPasses % 15 == 0 || detections.isNotEmpty()) {
            Log.d(
                TAG,
                "GPU pass=$completedForwardPasses prep=${prepMs}ms infer=${inferMs}ms " +
                    "total=${totalMs}ms maxScore=$maximumScore candidates=${candidates.size} nms=${detections.size}"
            )
        }

        return detections
    }

    // Fast coordinate and padding lookup cache
    private val inputFloatArray = FloatArray(1 * 640 * 640 * 3)

    private fun writeLetterboxNhwc(
        framePixels: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        modelWidth: Int,
        modelHeight: Int,
        rotationDegrees: Int
    ) {
        val uprightWidth = if (rotationDegrees == 90 || rotationDegrees == 270) frameHeight else frameWidth
        val uprightHeight = if (rotationDegrees == 90 || rotationDegrees == 270) frameWidth else frameHeight

        val scale = min(modelWidth.toFloat() / uprightWidth, modelHeight.toFloat() / uprightHeight)
        val padX = (modelWidth - uprightWidth * scale) / 2f
        val padY = (modelHeight - uprightHeight * scale) / 2f

        val isGrayscale = framePixels.size == frameWidth * frameHeight
        val isRgba = framePixels.size == frameWidth * frameHeight * 4

        var outIdx = 0

        // Precompute X coordinates for this width
        val uxArray = IntArray(modelWidth)
        val xPadArray = BooleanArray(modelWidth)
        for (mx in 0 until modelWidth) {
            val uprightX = (mx + 0.5f - padX) / scale
            xPadArray[mx] = uprightX < 0f || uprightX >= uprightWidth
            uxArray[mx] = uprightX.toInt()
        }

        for (my in 0 until modelHeight) {
            val uprightY = (my + 0.5f - padY) / scale
            val uy = uprightY.toInt()
            val isRowPadding = uprightY < 0f || uprightY >= uprightHeight

            for (mx in 0 until modelWidth) {
                if (isRowPadding || xPadArray[mx]) {
                    inputFloatArray[outIdx++] = PAD_COLOR_NORM
                    inputFloatArray[outIdx++] = PAD_COLOR_NORM
                    inputFloatArray[outIdx++] = PAD_COLOR_NORM
                    continue
                }

                val ux = uxArray[mx]
                val fx = when (rotationDegrees) {
                    0 -> ux.coerceIn(0, frameWidth - 1)
                    90 -> uy.coerceIn(0, frameWidth - 1)
                    180 -> (frameWidth - 1 - ux).coerceIn(0, frameWidth - 1)
                    270 -> (frameWidth - 1 - uy).coerceIn(0, frameWidth - 1)
                    else -> ux.coerceIn(0, frameWidth - 1)
                }
                val fy = when (rotationDegrees) {
                    0 -> uy.coerceIn(0, frameHeight - 1)
                    90 -> (frameHeight - 1 - ux).coerceIn(0, frameHeight - 1)
                    180 -> (frameHeight - 1 - uy).coerceIn(0, frameHeight - 1)
                    270 -> ux.coerceIn(0, frameHeight - 1)
                    else -> uy.coerceIn(0, frameHeight - 1)
                }

                if (isGrayscale) {
                    val gray = BYTE_TO_FLOAT[framePixels[fy * frameWidth + fx].toInt() and 0xFF]
                    inputFloatArray[outIdx++] = gray
                    inputFloatArray[outIdx++] = gray
                    inputFloatArray[outIdx++] = gray
                } else if (isRgba) {
                    val srcIdx = (fy * frameWidth + fx) * 4
                    inputFloatArray[outIdx++] = BYTE_TO_FLOAT[framePixels[srcIdx].toInt() and 0xFF]
                    inputFloatArray[outIdx++] = BYTE_TO_FLOAT[framePixels[srcIdx + 1].toInt() and 0xFF]
                    inputFloatArray[outIdx++] = BYTE_TO_FLOAT[framePixels[srcIdx + 2].toInt() and 0xFF]
                } else {
                    val srcIdx = (fy * frameWidth + fx) * 3
                    inputFloatArray[outIdx++] = BYTE_TO_FLOAT[framePixels[srcIdx].toInt() and 0xFF]
                    inputFloatArray[outIdx++] = BYTE_TO_FLOAT[framePixels[srcIdx + 1].toInt() and 0xFF]
                    inputFloatArray[outIdx++] = BYTE_TO_FLOAT[framePixels[srcIdx + 2].toInt() and 0xFF]
                }
            }
        }
        inputFloatBuffer.clear()
        inputFloatBuffer.put(inputFloatArray)
        inputBuffer.rewind()
    }

    private fun applyNms(boxes: List<RawDetection>, iouThresh: Float): List<RawDetection> {
        if (boxes.isEmpty()) return emptyList()
        val sorted = boxes.sortedByDescending { it.confidence }
        val selected = mutableListOf<RawDetection>()

        for (box in sorted) {
            var keep = true
            for (chosen in selected) {
                if (chosen.classId == box.classId && calculateIoU(box, chosen) > iouThresh) {
                    keep = false
                    break
                }
            }
            if (keep) {
                selected.add(box)
                if (selected.size >= 50) break
            }
        }
        return selected
    }

    private fun calculateIoU(a: RawDetection, b: RawDetection): Float {
        val interX1 = max(a.boxX1, b.boxX1)
        val interY1 = max(a.boxY1, b.boxY1)
        val interX2 = min(a.boxX2, b.boxX2)
        val interY2 = min(a.boxY2, b.boxY2)

        val interW = max(0f, interX2 - interX1)
        val interH = max(0f, interY2 - interY1)
        val interArea = interW * interH

        val areaA = max(0f, a.boxX2 - a.boxX1) * max(0f, a.boxY2 - a.boxY1)
        val areaB = max(0f, b.boxX2 - b.boxX1) * max(0f, b.boxY2 - b.boxY1)
        val unionArea = areaA + areaB - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    override fun close() {
        try {
            interpreter?.close()
        } finally {
            interpreter = null
            gpuDelegate?.close()
            gpuDelegate = null
        }
    }

    companion object {
        private const val TAG = "NaviSenseTfliteGpu"
        private const val PAD_COLOR_NORM = 114f / 255f

        // Precomputed lookup table for byte-to-normalized-float conversion
        private val BYTE_TO_FLOAT = FloatArray(256) { it / 255.0f }

        @Synchronized
        fun copyAssetToCache(context: Context, assetName: String): String {
            val file = File(context.cacheDir, assetName)
            file.parentFile?.mkdirs()
            val atomic = android.util.AtomicFile(file)
            val output = atomic.startWrite()
            try {
                context.assets.open(assetName).use { it.copyTo(output) }
                atomic.finishWrite(output)
            } catch (failure: Throwable) {
                atomic.failWrite(output)
                throw failure
            }
            return file.absolutePath
        }
    }
}
