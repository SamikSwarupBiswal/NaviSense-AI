package dev.navisense.inference

import java.nio.FloatBuffer
import kotlin.math.min

/** RGB/grayscale to CHW float tensor with upright coordinate sampling matching CoordinateTransformer. */
internal object LetterboxPreprocessor {
    fun prepare(
        framePixels: ByteArray,
        frameWidth: Int,
        frameHeight: Int,
        modelWidth: Int,
        modelHeight: Int,
        rotationDegrees: Int = 0
    ): FloatBuffer {
        val numPixels = modelWidth * modelHeight
        val floatBuffer = java.nio.ByteBuffer.allocateDirect(3 * numPixels * 4)
            .order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()

        val isGrayscale = framePixels.size == frameWidth * frameHeight
        require(frameWidth > 0 && frameHeight > 0 && modelWidth > 0 && modelHeight > 0)
        require(isGrayscale || framePixels.size == frameWidth * frameHeight * 3)
        require(rotationDegrees in setOf(0, 90, 180, 270))

        val uprightWidth = if (rotationDegrees == 90 || rotationDegrees == 270) frameHeight else frameWidth
        val uprightHeight = if (rotationDegrees == 90 || rotationDegrees == 270) frameWidth else frameHeight

        val scale = min(modelWidth.toFloat() / uprightWidth, modelHeight.toFloat() / uprightHeight)
        val padX = (modelWidth - uprightWidth * scale) / 2f
        val padY = (modelHeight - uprightHeight * scale) / 2f

        val rChannel = FloatArray(numPixels)
        val gChannel = FloatArray(numPixels)
        val bChannel = FloatArray(numPixels)

        for (my in 0 until modelHeight) {
            val uprightY = (my + 0.5f - padY) / scale
            val uy = uprightY.toInt()
            for (mx in 0 until modelWidth) {
                val uprightX = (mx + 0.5f - padX) / scale
                val ux = uprightX.toInt()
                val pixelIndex = my * modelWidth + mx

                if (uprightX < 0f || uprightX >= uprightWidth || uprightY < 0f || uprightY >= uprightHeight) {
                    rChannel[pixelIndex] = 114f / 255f
                    gChannel[pixelIndex] = 114f / 255f
                    bChannel[pixelIndex] = 114f / 255f
                    continue
                }

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
