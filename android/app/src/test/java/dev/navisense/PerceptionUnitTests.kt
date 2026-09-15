package dev.navisense

import dev.navisense.camera.CoordinateTransformer
import dev.navisense.camera.FrameQualityChecker
import dev.navisense.contracts.*
import dev.navisense.inference.InferenceBackend
import dev.navisense.inference.ModelMetadata
import dev.navisense.inference.RawDetection
import dev.navisense.inference.YoloModelRunner
import dev.navisense.search.TargetSearchEngine
import dev.navisense.tracking.VisualTracker
import org.junit.Assert.*
import org.junit.Test

class PerceptionUnitTests {

    // =========================================================================
    // 1. NormalizedRect & Geometry Tests
    // =========================================================================

    @Test
    fun testNormalizedRectIoU() {
        val rect1 = NormalizedRect(0.0f, 0.0f, 0.5f, 0.5f) // area = 0.25
        val rect2 = NormalizedRect(0.25f, 0.0f, 0.75f, 0.5f) // area = 0.25, intersection = 0.25 * 0.5 = 0.125
        // union = 0.25 + 0.25 - 0.125 = 0.375. IoU = 0.125 / 0.375 = 1/3 ~ 0.333
        val iou = rect1.calculateIoU(rect2)
        assertEquals(0.3333f, iou, 0.001f)

        // Completely disjoint
        val rect3 = NormalizedRect(0.6f, 0.6f, 1.0f, 1.0f)
        assertEquals(0.0f, rect1.calculateIoU(rect3), 0.0001f)

        // Exact match
        assertEquals(1.0f, rect1.calculateIoU(rect1), 0.0001f)
    }

    @Test
    fun testNormalizedRectClamped() {
        val outOfBounds = NormalizedRect(-0.2f, 0.1f, 1.5f, 0.9f)
        val clamped = outOfBounds.clamped()
        assertEquals(0.0f, clamped.left, 0.001f)
        assertEquals(0.1f, clamped.top, 0.001f)
        assertEquals(1.0f, clamped.right, 0.001f)
        assertEquals(0.9f, clamped.bottom, 0.001f)
    }

    // =========================================================================
    // 2. CoordinateTransformer Tests
    // =========================================================================

    @Test
    fun testCoordinateTransformerUnletterboxAndUpright() {
        // Frame: 1280x720, Model: 320x320
        // scale = 320/1280 = 0.25
        // unpaddedWidth = 1280 * 0.25 = 320 -> padX = 0
        // unpaddedHeight = 720 * 0.25 = 180 -> padY = (320 - 180)/2 = 70
        val transformer = CoordinateTransformer(
            frameWidth = 1280,
            frameHeight = 720,
            modelWidth = 320,
            modelHeight = 320,
            rotationDegrees = 0
        )

        // Bounding box centered in model space: x in [80, 240], y in [70, 250] (unpadded height is 180)
        val upright = transformer.toNormalizedUpright(
            modelX1 = 80f,
            modelY1 = 70f,
            modelX2 = 240f,
            modelY2 = 250f
        )
        assertNotNull(upright)
        // Expected frame coordinates:
        // x1 = 80 / 0.25 = 320 -> norm = 320 / 1280 = 0.25
        // x2 = 240 / 0.25 = 960 -> norm = 960 / 1280 = 0.75
        // y1 = (70 - 70) / 0.25 = 0 -> norm = 0.0
        // y2 = (250 - 70) / 0.25 = 720 -> norm = 1.0
        assertEquals(0.25f, upright!!.left, 0.01f)
        assertEquals(0.75f, upright.right, 0.01f)
        assertEquals(0.0f, upright.top, 0.01f)
        assertEquals(1.0f, upright.bottom, 0.01f)
    }

    // =========================================================================
    // 3. FrameQualityChecker Tests (PRD §17.2)
    // =========================================================================

    @Test
    fun testFrameQualityCheckerPitchDarkRejection() {
        // Mean < 10 must be UNUSABLE
        val darkFrame = IntArray(100) { 5 }
        val report = FrameQualityChecker.evaluate(darkFrame)
        assertEquals(FrameQualityStatus.UNUSABLE, report.status)
        assertTrue(report.reason?.contains("dark") == true)
    }

    @Test
    fun testFrameQualityCheckerFeaturelessRejection() {
        // Uniform gray: mean = 120 (>= 10), but stdDev = 0 (< 5) must be UNUSABLE
        val featurelessFrame = IntArray(100) { 120 }
        val report = FrameQualityChecker.evaluate(featurelessFrame)
        assertEquals(FrameQualityStatus.UNUSABLE, report.status)
        assertTrue(report.reason?.contains("featureless") == true)
    }

    @Test
    fun testFrameQualityCheckerValidFrame() {
        // Normal contrast: alternating 50 and 150 -> mean = 100, stdDev = 50 -> USABLE
        val goodFrame = IntArray(100) { i -> if (i % 2 == 0) 50 else 150 }
        val report = FrameQualityChecker.evaluate(goodFrame)
        assertEquals(FrameQualityStatus.USABLE, report.status)
        assertNull(report.reason)
    }

    // =========================================================================
    // 4. VisualTracker Tests (PRD §17.1)
    // =========================================================================

    @Test
    fun testVisualTrackerOneToOneMatchingAndPersistence() {
        val tracker = VisualTracker(matchIouThreshold = 0.30f, trackExpiryMs = 500L)

        // Frame 1 at t=100ms
        val det1 = listOf(
            DetectedObject(classId = 0, label = "chair", confidence = 0.85f, boundingBox = NormalizedRect(0.4f, 0.4f, 0.6f, 0.6f))
        )
        val tracked1 = tracker.update(det1, currentMonotonicMs = 100L, geometryVersion = 1)
        assertEquals(1, tracked1.size)
        val trackId = tracked1.first().trackId
        assertNotNull(trackId)

        // Frame 2 at t=200ms with slight motion
        val det2 = listOf(
            DetectedObject(classId = 0, label = "chair", confidence = 0.82f, boundingBox = NormalizedRect(0.42f, 0.41f, 0.62f, 0.61f))
        )
        val tracked2 = tracker.update(det2, currentMonotonicMs = 200L, geometryVersion = 1)
        assertEquals(1, tracked2.size)
        assertEquals(trackId, tracked2.first().trackId)

        // Frame 3 at t=300ms
        val det3 = listOf(
            DetectedObject(classId = 0, label = "chair", confidence = 0.80f, boundingBox = NormalizedRect(0.43f, 0.42f, 0.63f, 0.62f))
        )
        tracker.update(det3, currentMonotonicMs = 300L, geometryVersion = 1)

        // After 3 frames >= 0.40 within 1s, track must be qualified
        val trackObj = tracker.getTrack(trackId!!)
        assertNotNull(trackObj)
        assertTrue(trackObj!!.isQualified(currentMonotonicMs = 300L))
    }

    @Test
    fun testVisualTrackerExpiryAfter500Ms() {
        val tracker = VisualTracker(matchIouThreshold = 0.30f, trackExpiryMs = 500L)

        val det1 = listOf(
            DetectedObject(classId = 0, label = "chair", confidence = 0.85f, boundingBox = NormalizedRect(0.4f, 0.4f, 0.6f, 0.6f))
        )
        tracker.update(det1, currentMonotonicMs = 100L, geometryVersion = 1)
        assertEquals(1, tracker.currentActiveTracks.size)

        // Advance time by 600 ms (> 500 ms expiry) with no detections
        tracker.update(emptyList(), currentMonotonicMs = 701L, geometryVersion = 1)
        assertEquals(0, tracker.currentActiveTracks.size)
    }

    @Test
    fun testVisualTrackerAreaGrowthCalculation() {
        val tracker = VisualTracker(matchIouThreshold = 0.30f)

        // Baseline at t=100ms: area = 0.2 * 0.2 = 0.04
        val det1 = listOf(
            DetectedObject(classId = 0, label = "person", confidence = 0.9f, boundingBox = NormalizedRect(0.4f, 0.4f, 0.6f, 0.6f))
        )
        val tracked1 = tracker.update(det1, currentMonotonicMs = 100L, geometryVersion = 1)
        val trackId = tracked1.first().trackId!!

        // Frame at t=600ms (500ms later): area = 0.25 * 0.25 = 0.0625 -> growth = (0.0625 / 0.04) - 1 = 56.25%
        val det2 = listOf(
            DetectedObject(classId = 0, label = "person", confidence = 0.9f, boundingBox = NormalizedRect(0.375f, 0.375f, 0.625f, 0.625f))
        )
        tracker.update(det2, currentMonotonicMs = 600L, geometryVersion = 1)

        val track = tracker.getTrack(trackId)
        assertNotNull(track)
        val growth = track!!.computeAreaGrowth(currentMonotonicMs = 600L)
        assertNotNull(growth)
        assertEquals(0.5625f, growth!!, 0.01f)
        assertTrue(growth >= 0.25f) // Triggers PRD §17.1 growth rule
    }

    // =========================================================================
    // 5. TargetSearchEngine Tests (PRD §20)
    // =========================================================================

    @Test
    fun testTargetSearchConfirmationAndDirection() {
        val engine = TargetSearchEngine()
        val sessionGen = 42L
        engine.startSearch(targetClass = "keys", sessionGeneration = sessionGen, startMonotonicMs = 1000L)

        // Candidate on the left (x center = 0.15 < 1/3)
        val keysBox = NormalizedRect(0.1f, 0.4f, 0.2f, 0.6f) // center = 0.15
        val detection = DetectedObject(classId = 1, label = "keys", confidence = 0.85f, boundingBox = keysBox)

        // Frame 1
        val event1 = MobilePerceptionEvent(
            sessionGeneration = sessionGen,
            mode = AppVisionMode.LOCATE_SEARCH,
            frameId = 1L,
            captureMonotonicMs = 1100L,
            deliveryMonotonicMs = 1100L,
            geometryVersion = 1,
            modelIdentity = "locate_v1",
            qualityStatus = FrameQualityStatus.USABLE,
            detections = listOf(detection)
        )
        val res1 = engine.processFrame(event1)
        assertEquals(SearchStatus.SEARCHING, res1?.status)

        // Frame 2
        val event2 = event1.copy(frameId = 2L, captureMonotonicMs = 1200L, deliveryMonotonicMs = 1200L)
        val res2 = engine.processFrame(event2)
        assertEquals(SearchStatus.SEARCHING, res2?.status)

        // Frame 3 (3 of 5 confirmed!)
        val event3 = event1.copy(frameId = 3L, captureMonotonicMs = 1300L, deliveryMonotonicMs = 1300L)
        val res3 = engine.processFrame(event3)
        assertEquals(SearchStatus.CONFIRMED, res3?.status)
        assertEquals(TargetDirection.LEFT, res3?.direction)
        assertEquals(1, res3?.candidateCount)
    }

    @Test
    fun testTargetSearchTimeoutAfter15Seconds() {
        val engine = TargetSearchEngine()
        val sessionGen = 10L
        engine.startSearch(targetClass = "wallet", sessionGeneration = sessionGen, startMonotonicMs = 1000L)

        // Send frame at t=16500ms (> 15000ms after start)
        val emptyEvent = MobilePerceptionEvent(
            sessionGeneration = sessionGen,
            mode = AppVisionMode.LOCATE_SEARCH,
            frameId = 99L,
            captureMonotonicMs = 16500L,
            deliveryMonotonicMs = 16500L,
            geometryVersion = 1,
            modelIdentity = "locate_v1",
            qualityStatus = FrameQualityStatus.USABLE,
            detections = emptyList()
        )
        val timeoutResult = engine.processFrame(emptyEvent)
        assertNotNull(timeoutResult)
        assertEquals(SearchStatus.TIMEOUT, timeoutResult!!.status)
    }

    @Test
    fun testTargetSearchSessionGenerationInvalidation() {
        val engine = TargetSearchEngine()
        engine.startSearch(targetClass = "keys", sessionGeneration = 5L, startMonotonicMs = 1000L)

        // Frame from old cancelled session generation 4L
        val oldEvent = MobilePerceptionEvent(
            sessionGeneration = 4L,
            mode = AppVisionMode.LOCATE_SEARCH,
            frameId = 1L,
            captureMonotonicMs = 1100L,
            deliveryMonotonicMs = 1100L,
            geometryVersion = 1,
            modelIdentity = "locate_v1",
            qualityStatus = FrameQualityStatus.USABLE,
            detections = listOf(
                DetectedObject(classId = 1, label = "keys", confidence = 0.9f, boundingBox = NormalizedRect(0.1f, 0.1f, 0.2f, 0.2f))
            )
        )
        val res = engine.processFrame(oldEvent)
        assertNull("Events from mismatched session generations must be ignored", res)
    }

    // =========================================================================
    // 6. YoloModelRunner Integration Tests
    // =========================================================================

    @Test
    fun testYoloModelRunnerExecution() {
        val mockBackend = InferenceBackend { _, _, _, _, _ ->
            listOf(
                RawDetection(classId = 0, confidence = 0.88f, boxX1 = 50f, boxY1 = 50f, boxX2 = 150f, boxY2 = 150f)
            )
        }

        val runner = YoloModelRunner(mockBackend)
        val metadata = ModelMetadata(
            modelIdentity = "mobility_smoke_v0",
            mode = AppVisionMode.MOBILITY,
            inputWidth = 320,
            inputHeight = 320,
            classLabels = listOf("chair", "person"),
            confidenceThreshold = 0.40f
        )
        runner.load(metadata)
        assertTrue(runner.isLoaded)

        // Frame: 100 pixels of normal contrast (not dark/featureless)
        val normalPixels = ByteArray(100) { i -> if (i % 2 == 0) 40 else 160.toByte() }

        val event = runner.detect(
            framePixels = normalPixels,
            frameWidth = 640,
            frameHeight = 480,
            rotationDegrees = 0,
            frameId = 1L,
            captureMonotonicMs = 1000L,
            deliveryMonotonicMs = 1020L,
            sessionGeneration = 1L,
            geometryVersion = 1
        )

        assertEquals(AppVisionMode.MOBILITY, event.mode)
        assertEquals(FrameQualityStatus.USABLE, event.qualityStatus)
        assertEquals(1, event.detections.size)
        assertEquals("chair", event.detections.first().label)
        assertEquals(0.88f, event.detections.first().confidence, 0.001f)
    }
}
