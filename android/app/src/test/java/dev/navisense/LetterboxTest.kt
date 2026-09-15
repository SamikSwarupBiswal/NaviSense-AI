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
}
