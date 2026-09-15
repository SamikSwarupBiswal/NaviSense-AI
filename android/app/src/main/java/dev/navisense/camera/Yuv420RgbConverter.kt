package dev.navisense.camera

import java.nio.ByteBuffer
import kotlin.math.max

/** A YUV plane view that keeps CameraX-specific types out of the conversion core. */
data class YuvPlane(
    val buffer: ByteBuffer,
    val rowStride: Int,
    val pixelStride: Int
)

/** Converts a cropped YUV_420_888 image to packed RGB bytes in sensor orientation. */
object Yuv420RgbConverter {
    fun convert(
        imageWidth: Int,
        imageHeight: Int,
        cropLeft: Int,
        cropTop: Int,
        cropWidth: Int,
        cropHeight: Int,
        yPlane: YuvPlane,
        uPlane: YuvPlane,
        vPlane: YuvPlane
    ): ByteArray {
        require(imageWidth > 0 && imageHeight > 0)
        require(cropWidth > 0 && cropHeight > 0)
        require(cropLeft >= 0 && cropTop >= 0)
        require(cropLeft + cropWidth <= imageWidth && cropTop + cropHeight <= imageHeight)
        require(yPlane.rowStride > 0 && yPlane.pixelStride > 0)
        require(uPlane.rowStride > 0 && uPlane.pixelStride > 0)
        require(vPlane.rowStride > 0 && vPlane.pixelStride > 0)

        val pixelCount = Math.multiplyExact(cropWidth, cropHeight)
        val rgb = ByteArray(Math.multiplyExact(pixelCount, 3))
        val yBuffer = yPlane.buffer.duplicate()
        val uBuffer = uPlane.buffer.duplicate()
        val vBuffer = vPlane.buffer.duplicate()
        val yBase = yBuffer.position()
        val uBase = uBuffer.position()
        val vBase = vBuffer.position()

        val yArray = if (yBuffer.hasArray()) yBuffer.array() else ByteArray(yBuffer.remaining()).also { yBuffer.get(it) }
        val yOffset = if (yBuffer.hasArray()) yBuffer.arrayOffset() else -yBase

        val uArray = if (uBuffer.hasArray()) uBuffer.array() else ByteArray(uBuffer.remaining()).also { uBuffer.get(it) }
        val uOffset = if (uBuffer.hasArray()) uBuffer.arrayOffset() else -uBase

        val vArray = if (vBuffer.hasArray()) vBuffer.array() else ByteArray(vBuffer.remaining()).also { vBuffer.get(it) }
        val vOffset = if (vBuffer.hasArray()) vBuffer.arrayOffset() else -vBase

        var outputIndex = 0
        for (outputY in 0 until cropHeight) {
            val sourceY = cropTop + outputY
            for (outputX in 0 until cropWidth) {
                val sourceX = cropLeft + outputX
                val yIndex = yBase + sourceY * yPlane.rowStride + sourceX * yPlane.pixelStride
                val chromaX = sourceX / 2
                val chromaY = sourceY / 2
                val uIndex = uBase + chromaY * uPlane.rowStride + chromaX * uPlane.pixelStride
                val vIndex = vBase + chromaY * vPlane.rowStride + chromaX * vPlane.pixelStride

                require(yIndex in yBase until yPlane.buffer.limit()) { "Y plane is shorter than its declared strides" }
                require(uIndex in uBase until uPlane.buffer.limit()) { "U plane is shorter than its declared strides" }
                require(vIndex in vBase until vPlane.buffer.limit()) { "V plane is shorter than its declared strides" }

                val y = max(0, (yArray[yOffset + yIndex].toInt() and 0xFF) - 16)
                val u = (uArray[uOffset + uIndex].toInt() and 0xFF) - 128
                val v = (vArray[vOffset + vIndex].toInt() and 0xFF) - 128

                rgb[outputIndex++] = clampToByte((1192 * y + 1634 * v) shr 10)
                rgb[outputIndex++] = clampToByte((1192 * y - 400 * u - 833 * v) shr 10)
                rgb[outputIndex++] = clampToByte((1192 * y + 2066 * u) shr 10)
            }
        }
        return rgb
    }

    private fun clampToByte(value: Int): Byte = value.coerceIn(0, 255).toByte()
}
