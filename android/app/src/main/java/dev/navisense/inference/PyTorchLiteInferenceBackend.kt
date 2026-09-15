package dev.navisense.inference

import android.content.Context
import android.util.Log
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * PyTorch Mobile Lite inference backend for NaviSense YOLO models.
 * Implements InferenceBackend for YoloModelRunner with anchor-free head decoding and NMS.
 */
class PyTorchLiteInferenceBackend(
    private val modelPath: String,
    private val numClasses: Int,
    private val confThreshold: Float = 0.25f,
    private val iouThreshold: Float = 0.45f
) : InferenceBackend, AutoCloseable {

    private var module: Module? = null
    var completedForwardPasses: Int = 0
        private set

    init {
        module = LiteModuleLoader.load(modelPath)
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
        val mod = checkNotNull(module) { "Model is closed" }

        val floatBuffer = LetterboxPreprocessor.prepare(
            framePixels, frameWidth, frameHeight, modelWidth, modelHeight, rotationDegrees
        )

        val inputTensor = Tensor.fromBlob(floatBuffer, longArrayOf(1, 3, modelHeight.toLong(), modelWidth.toLong()))
        val outputTensor = mod.forward(IValue.from(inputTensor)).toTensor()
        completedForwardPasses++
        val outData = outputTensor.dataAsFloatArray
        val shape = outputTensor.shape()

        check(shape.size == 3 && shape[0] == 1L && shape[1] == (4 + numClasses).toLong() && shape[2] == 8400L) { "Incompatible model output: ${shape.contentToString()}" }

        val numChannels = shape[1].toInt()
        val numAnchors = shape[2].toInt()
        val candidates = mutableListOf<RawDetection>()
        var maximumScore = -Float.MAX_VALUE

        for (i in 0 until numAnchors) {
            var bestClass = -1
            var bestScore = -Float.MAX_VALUE

            for (c in 0 until numClasses) {
                val score = outData[(4 + c) * numAnchors + i]
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }
            if (bestScore > maximumScore) maximumScore = bestScore

            if (bestScore >= confThreshold && bestClass >= 0) {
                val cx = outData[0 * numAnchors + i]
                val cy = outData[1 * numAnchors + i]
                val w = outData[2 * numAnchors + i]
                val h = outData[3 * numAnchors + i]

                val x1 = (cx - w / 2.0f).coerceIn(0f, modelWidth.toFloat())
                val y1 = (cy - h / 2.0f).coerceIn(0f, modelHeight.toFloat())
                val x2 = (cx + w / 2.0f).coerceIn(0f, modelWidth.toFloat())
                val y2 = (cy + h / 2.0f).coerceIn(0f, modelHeight.toFloat())

                candidates.add(RawDetection(bestClass, bestScore, x1, y1, x2, y2))
            }
        }

        if (completedForwardPasses % 15 == 0) {
            Log.d(TAG, "forward=$completedForwardPasses maxScore=$maximumScore candidates=${candidates.size}")
        }

        return applyNms(candidates, iouThreshold)
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
        module?.destroy()
        module = null
    }

    companion object {
        private const val TAG = "NaviSenseYoloBackend"
        @Synchronized
        fun copyAssetToCache(context: Context, assetName: String): String {
            val file = File(context.cacheDir, assetName)
            file.parentFile?.mkdirs()
            // AtomicFile preserves the previous complete artifact if copying fails.
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
