package dev.navisense

import dev.navisense.inference.LetterboxPreprocessor
import dev.navisense.camera.CoordinateTransformer
import org.junit.Assert.*
import org.junit.Test

class LetterboxTest {
    @Test fun landscapePaddingMatchesInverseCoordinates() {
        val tensor = LetterboxPreprocessor.prepare(ByteArray(8) { 255.toByte() }, 4, 2, 4, 4)
        for (channel in 0..2) {
            for (x in 0..3) {
                assertEquals(114f/255f, tensor.get(channel*16+x), .00001f)
                assertEquals(1f, tensor.get(channel*16+4+x), .00001f)
                assertEquals(114f/255f, tensor.get(channel*16+12+x), .00001f)
            }
        }
        val box = CoordinateTransformer(4,2,4,4).toNormalizedUpright(0f,1f,4f,3f)!!
        assertEquals(1f, box.area, .00001f)
    }
    @Test fun portraitRgbPreservesChannelsAndPadding() {
        val pixels = ByteArray(24) { if (it % 3 == 0) 255.toByte() else 0 }
        val tensor = LetterboxPreprocessor.prepare(pixels, 2,4,4,4)
        assertEquals(114f/255f, tensor.get(0), .00001f)
        assertEquals(1f, tensor.get(1), .00001f)
        assertEquals(0f, tensor.get(17), .00001f)
        assertEquals(0f, tensor.get(33), .00001f)
    }

    @Test fun rotation90SamplesUprightCoordinatesDirectly() {
        // 4 wide, 2 high. Set top-left pixel (fx=0, fy=0) to 255
        val pixels = ByteArray(8) { 0 }
        pixels[0 * 4 + 0] = 255.toByte()
        // Rotated 90 deg -> uprightWidth=2, uprightHeight=4.
        // In 4x4 model: padX = (4 - 2)/2 = 1, padY = 0.
        // Source (0,0) lands at upright (x'=1, y'=0), so mx = 1 + 1 = 2, my = 0
        val tensor = LetterboxPreprocessor.prepare(pixels, 4, 2, 4, 4, rotationDegrees = 90)
        // Check padding at mx=0, my=0 -> 114/255
        assertEquals(114f/255f, tensor.get(0 * 4 + 0), .00001f)
        // Check mx=2, my=0 -> 1.0f (255/255)
        assertEquals(1f, tensor.get(0 * 4 + 2), .00001f)
        // Check mx=1, my=0 -> 0.0f
        assertEquals(0f, tensor.get(0 * 4 + 1), .00001f)
    }

    @Test fun rotation180SamplesUprightCoordinatesDirectly() {
        // 4 wide, 2 high. Set top-left pixel (fx=0, fy=0) to 255
        val pixels = ByteArray(8) { 0 }
        pixels[0 * 4 + 0] = 255.toByte()
        // 180 deg -> uprightWidth=4, uprightHeight=2.
        // In 4x4 model: padX = 0, padY = (4 - 2)/2 = 1.
        // Source (0,0) lands at upright (x'=3, y'=1), so mx = 3, my = 1 + 1 = 2
        val tensor = LetterboxPreprocessor.prepare(pixels, 4, 2, 4, 4, rotationDegrees = 180)
        assertEquals(1f, tensor.get(2 * 4 + 3), .00001f)
        assertEquals(0f, tensor.get(1 * 4 + 0), .00001f)
    }

    @Test fun rotation270SamplesUprightCoordinatesDirectly() {
        // 4 wide, 2 high. Set top-left pixel (fx=0, fy=0) to 255
        val pixels = ByteArray(8) { 0 }
        pixels[0 * 4 + 0] = 255.toByte()
        // 270 deg -> uprightWidth=2, uprightHeight=4.
        // In 4x4 model: padX = 1, padY = 0.
        // Source (0,0) lands at upright (x'=0, y'=3), so mx = 0 + 1 = 1, my = 3
        val tensor = LetterboxPreprocessor.prepare(pixels, 4, 2, 4, 4, rotationDegrees = 270)
        assertEquals(1f, tensor.get(3 * 4 + 1), .00001f)
        assertEquals(0f, tensor.get(0 * 4 + 1), .00001f)
    }
}
