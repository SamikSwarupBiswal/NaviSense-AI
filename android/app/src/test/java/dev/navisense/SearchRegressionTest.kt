package dev.navisense

import dev.navisense.contracts.*
import dev.navisense.search.TargetSearchEngine
import org.junit.Assert.*
import org.junit.Test

class SearchRegressionTest {
    private val detection = DetectedObject(0, "keys", .9f, NormalizedRect(.1f,.2f,.3f,.4f))
    private fun frame(id: Long, detections: List<DetectedObject> = listOf(detection)) =
        MobilePerceptionEvent(1, AppVisionMode.LOCATE_SEARCH, id, 1000 + id * 100, 1000 + id * 100, 1, "locate", FrameQualityStatus.USABLE, detections)
    private fun engine() = TargetSearchEngine().apply { startSearch("keys", 1, 1000) }
    @Test fun repeatedFrameDoesNotConfirm() {
        val engine = engine()
        engine.processFrame(frame(1))
        repeat(5) { assertNull(engine.processFrame(frame(1))) }
        assertEquals(SearchStatus.SEARCHING, engine.processFrame(frame(2))?.status)
        assertEquals(SearchStatus.CONFIRMED, engine.processFrame(frame(3))?.status)
    }
    @Test fun severalBoxesInOneFrameDoNotConfirm() {
        val engine = engine()
        engine.processFrame(frame(1, emptyList()))
        engine.processFrame(frame(2, emptyList()))
        assertEquals(SearchStatus.SEARCHING, engine.processFrame(frame(3, List(3) { detection }))?.status)
    }
    @Test fun staleFutureModeAndModelAreRejected() {
        val engine = engine()
        engine.processFrame(frame(1))
        val candidate = frame(2)
        assertNull(engine.processFrame(candidate.copy(deliveryMonotonicMs = 1801)))
        assertNull(engine.processFrame(candidate.copy(captureMonotonicMs = 1201)))
        assertNull(engine.processFrame(candidate.copy(mode = AppVisionMode.MOBILITY)))
        assertNull(engine.processFrame(candidate.copy(modelIdentity = "other")))
        assertEquals(SearchStatus.SEARCHING, engine.processFrame(candidate)?.status)
    }
    @Test fun geometryAndQualityInvalidateHistory() {
        val engine = engine()
        engine.processFrame(frame(1))
        engine.processFrame(frame(2))
        assertEquals(SearchStatus.SEARCHING, engine.processFrame(frame(3).copy(geometryVersion = 2))?.status)
        engine.processFrame(frame(4).copy(geometryVersion = 2, qualityStatus = FrameQualityStatus.UNUSABLE))
        assertEquals(SearchStatus.SEARCHING, engine.processFrame(frame(5).copy(geometryVersion = 2))?.status)
    }
    @Test fun searchCanContinueAfterSingleTimeout() {
        val engine = engine()
        assertEquals(SearchStatus.TIMEOUT, engine.processFrame(frame(150))?.status)
        assertEquals(SearchStatus.SEARCHING, engine.processFrame(frame(151))?.status)
        engine.processFrame(frame(152))
        assertEquals(SearchStatus.CONFIRMED, engine.processFrame(frame(153))?.status)
    }
    @Test fun independentTargetsStaySeparate() {
        val engine = engine()
        val other = detection.copy(boundingBox = NormalizedRect(.7f,.2f,.9f,.4f))
        engine.processFrame(frame(1, listOf(detection, other)))
        engine.processFrame(frame(2, listOf(detection, other)))
        assertEquals(SearchStatus.MULTIPLE_CANDIDATES, engine.processFrame(frame(3, listOf(detection, other)))?.status)
    }
}
