package dev.navisense.inference

import java.nio.FloatBuffer
import kotlin.math.min

/** RGB/grayscale to CHW float tensor with the same continuous letterbox geometry as CoordinateTransformer. */
internal object LetterboxPreprocessor {
    fun prepare(framePixels: ByteArray, frameWidth: Int, frameHeight: Int, modelWidth: Int, modelHeight: Int): FloatBuffer {
        val numPixels = modelWidth * modelHeight
        val floatBuffer = java.nio.ByteBuffer.allocateDirect(3 * numPixels * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()

        val isGrayscale = framePixels.size == frameWidth * frameHeight
        require(frameWidth > 0 && frameHeight > 0 && modelWidth > 0 && modelHeight > 0)
        require(isGrayscale || framePixels.size == frameWidth * frameHeight * 3)
        val scale = min(modelWidth.toFloat() / frameWidth, modelHeight.toFloat() / frameHeight)
        val padX = (modelWidth - frameWidth * scale) / 2f
        val padY = (modelHeight - frameHeight * scale) / 2f

        val rChannel = FloatArray(numPixels)
        val gChannel = FloatArray(numPixels)
        val bChannel = FloatArray(numPixels)

        for (my in 0 until modelHeight) {
            val sourceY = (my + 0.5f - padY) / scale
            val fy = sourceY.toInt().coerceIn(0, frameHeight - 1)
            for (mx in 0 until modelWidth) {
                val sourceX = (mx + 0.5f - padX) / scale
                val fx = sourceX.toInt().coerceIn(0, frameWidth - 1)
                val pixelIndex = my * modelWidth + mx
                if (sourceX < 0 || sourceX >= frameWidth || sourceY < 0 || sourceY >= frameHeight) {
                    rChannel[pixelIndex] = 114f / 255f
                    gChannel[pixelIndex] = 114f / 255f
                    bChannel[pixelIndex] = 114f / 255f
                    continue
                }
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

        return floatBuffer
    }
}
