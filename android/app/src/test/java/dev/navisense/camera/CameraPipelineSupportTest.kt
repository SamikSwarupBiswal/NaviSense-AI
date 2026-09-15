package dev.navisense.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class CameraPipelineSupportTest {
    @Test
    fun timestampMapperAnchorsClockDomainsAndPreservesFrameAge() {
        val mapper = CameraTimestampMapper()
        val first = mapper.map(10_000_000_000L, 50_000_000_000L)
        assertEquals(CameraTimestampMapper.Mapping.Usable(50_000L), first)

        val second = mapper.map(10_100_000_000L, 50_120_000_000L)
        assertEquals(CameraTimestampMapper.Mapping.Usable(50_100L), second)
    }

    @Test
    fun timestampMapperRejectsDuplicatesStaleFramesAndFutureMapping() {
        val duplicateMapper = CameraTimestampMapper()
        duplicateMapper.map(1_000_000_000L, 2_000_000_000L)
        assertTrue(duplicateMapper.map(1_000_000_000L, 2_010_000_000L) is CameraTimestampMapper.Mapping.Rejected)

        val staleMapper = CameraTimestampMapper()
        staleMapper.map(1_000_000_000L, 2_000_000_000L)
        val stale = staleMapper.map(1_100_000_000L, 2_700_000_000L)
        assertTrue(stale is CameraTimestampMapper.Mapping.Rejected)
        assertTrue((stale as CameraTimestampMapper.Mapping.Rejected).reason.contains("stale"))
        val recovered = staleMapper.map(1_200_000_000L, 2_800_000_000L)
        assertEquals(CameraTimestampMapper.Mapping.Usable(2_800L), recovered)

        val futureMapper = CameraTimestampMapper()
        futureMapper.map(1_000_000_000L, 2_000_000_000L)
        val future = futureMapper.map(1_100_000_000L, 2_050_000_000L)
        assertTrue(future is CameraTimestampMapper.Mapping.Rejected)
        assertTrue((future as CameraTimestampMapper.Mapping.Rejected).reason.contains("future"))
    }

    @Test
    fun yuvConverterProducesPackedRgbWithNeutralChroma() {
        val rgb = Yuv420RgbConverter.convert(
            imageWidth = 2,
            imageHeight = 2,
            cropLeft = 0,
            cropTop = 0,
            cropWidth = 2,
            cropHeight = 2,
            yPlane = plane(byteArrayOf(16, 235.toByte(), 81, 145.toByte()), rowStride = 2, pixelStride = 1),
            uPlane = plane(byteArrayOf(128.toByte()), rowStride = 1, pixelStride = 1),
            vPlane = plane(byteArrayOf(128.toByte()), rowStride = 1, pixelStride = 1)
        )

        assertArrayEquals(
            byteArrayOf(
                0, 0, 0,
                254.toByte(), 254.toByte(), 254.toByte(),
                75, 75, 75,
                150.toByte(), 150.toByte(), 150.toByte()
            ),
            rgb
        )
    }

    @Test
    fun yuvConverterHonorsCropAndPlaneStrides() {
        val y = byteArrayOf(
            16, 16, 16, 16, 0, 0,
            16, 81, 145.toByte(), 16, 0, 0,
            16, 235.toByte(), 41, 16, 0, 0,
            16, 16, 16, 16, 0, 0
        )
        val chroma = byteArrayOf(128.toByte(), 0, 128.toByte(), 0, 128.toByte(), 0, 128.toByte(), 0)
        val rgb = Yuv420RgbConverter.convert(
            imageWidth = 4,
            imageHeight = 4,
            cropLeft = 1,
            cropTop = 1,
            cropWidth = 2,
            cropHeight = 2,
            yPlane = plane(y, rowStride = 6, pixelStride = 1),
            uPlane = plane(chroma, rowStride = 4, pixelStride = 2),
            vPlane = plane(chroma, rowStride = 4, pixelStride = 2)
        )

        assertArrayEquals(
            byteArrayOf(
                75, 75, 75,
                150.toByte(), 150.toByte(), 150.toByte(),
                254.toByte(), 254.toByte(), 254.toByte(),
                29, 29, 29
            ),
            rgb
        )
    }

    private fun plane(bytes: ByteArray, rowStride: Int, pixelStride: Int) = YuvPlane(
        buffer = ByteBuffer.wrap(bytes),
        rowStride = rowStride,
        pixelStride = pixelStride
    )
}
