package dev.navisense.inference

import android.content.Context
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

    init {
        module = LiteModuleLoader.load(modelPath)
    }

    override fun runInference(
        framePixels: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        modelWidth: Int,
        modelHeight: Int
    ): List<RawDetection> {
        val mod = module ?: return emptyList()

        val numPixels = modelWidth * modelHeight
        val floatBuffer = FloatBuffer.allocate(3 * numPixels)

        val isGrayscale = framePixels.size == frameWidth * frameHeight
        val scaleX = frameWidth.toFloat() / modelWidth
        val scaleY = frameHeight.toFloat() / modelHeight

        val rChannel = FloatArray(numPixels)
        val gChannel = FloatArray(numPixels)
        val bChannel = FloatArray(numPixels)

        for (my in 0 until modelHeight) {
            val fy = (my * scaleY).toInt().coerceIn(0, frameHeight - 1)
            for (mx in 0 until modelWidth) {
                val fx = (mx * scaleX).toInt().coerceIn(0, frameWidth - 1)
                val pixelIndex = my * modelWidth + mx
                if (isGrayscale) {
                    val gray = (framePixels[fy * frameWidth + fx].toInt() and 0xFF) / 255.0f
                    rChannel[pixelIndex] = gray
                    gChannel[pixelIndex] = gray
                    bChannel[pixelIndex] = gray
                } else {
                    val srcIdx = (fy * frameWidth + fx) * 3
                    if (srcIdx + 2 < framePixels.size) {
                        rChannel[pixelIndex] = (framePixels[srcIdx].toInt() and 0xFF) / 255.0f
                        gChannel[pixelIndex] = (framePixels[srcIdx + 1].toInt() and 0xFF) / 255.0f
                        bChannel[pixelIndex] = (framePixels[srcIdx + 2].toInt() and 0xFF) / 255.0f
                    }
                }
            }
        }

        floatBuffer.put(rChannel)
        floatBuffer.put(gChannel)
        floatBuffer.put(bChannel)
        floatBuffer.flip()

        val inputTensor = Tensor.fromBlob(floatBuffer, longArrayOf(1, 3, modelHeight.toLong(), modelWidth.toLong()))
        val outputTensor = mod.forward(IValue.from(inputTensor)).toTensor()
        val outData = outputTensor.dataAsFloatArray
        val shape = outputTensor.shape()

        if (shape.size != 3 || shape[0] != 1L || shape[2] != 8400L) {
            return emptyList()
        }

        val numChannels = shape[1].toInt()
        val numAnchors = shape[2].toInt()
        val candidates = mutableListOf<RawDetection>()

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
        module = null
    }

    companion object {
        fun copyAssetToCache(context: Context, assetName: String): String {
            val file = File(context.cacheDir, assetName)
            if (!file.exists() || file.length() == 0L) {
                file.parentFile?.mkdirs()
                context.assets.open(assetName).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return file.absolutePath
        }
    }
}
