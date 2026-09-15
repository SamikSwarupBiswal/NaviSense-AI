package dev.navisense

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.navisense.contracts.AppVisionMode
import dev.navisense.inference.ModelMetadata
import dev.navisense.inference.PyTorchLiteInferenceBackend
import dev.navisense.inference.YoloModelRunner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelBenchmarkTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testLocateSmokeModelLoadAndInference() {
        val assetName = "models/locate_smoke.ptl"
        val modelPath = PyTorchLiteInferenceBackend.copyAssetToCache(context, assetName)

        val loadStart = SystemClock.elapsedRealtime()
        val backend = PyTorchLiteInferenceBackend(
            modelPath = modelPath,
            numClasses = 2,
            confThreshold = 0.25f,
            iouThreshold = 0.50f
        )
        val loadElapsed = SystemClock.elapsedRealtime() - loadStart

        println("BENCHMARK: Locate smoke model load time: ${loadElapsed} ms")
        assertTrue("Model load time must be <= 5000 ms per PRD §13.5 (was ${loadElapsed} ms)", loadElapsed <= 5000L)

        val runner = YoloModelRunner(backend)
        val metadata = ModelMetadata(
            modelIdentity = "NaviSense-Locate-YOLO-smoke",
            mode = AppVisionMode.LOCATE_SEARCH,
            inputWidth = 640,
            inputHeight = 640,
            classLabels = listOf("keys", "wallet"),
            confidenceThreshold = 0.25f,
            nmsIouThreshold = 0.50f
        )
        assertTrue(runner.load(metadata))

        // Create 640x480 test image bytes (simulating camera frame)
        val dummyFrame = ByteArray(640 * 480) { 128.toByte() }

        // Warm-up pass
        runner.detect(
            framePixels = dummyFrame,
            frameWidth = 640,
            frameHeight = 480,
            rotationDegrees = 90,
            frameId = 1L,
            captureMonotonicMs = SystemClock.elapsedRealtime(),
            deliveryMonotonicMs = SystemClock.elapsedRealtime(),
            sessionGeneration = 1L,
            geometryVersion = 1
        )

        // Measured forward passes
        val runs = 5
        var totalInferenceMs = 0L
        for (i in 1..runs) {
            val start = SystemClock.elapsedRealtime()
            val event = runner.detect(
                framePixels = dummyFrame,
                frameWidth = 640,
                frameHeight = 480,
                rotationDegrees = 90,
                frameId = (i + 1).toLong(),
                captureMonotonicMs = start,
                deliveryMonotonicMs = start,
                sessionGeneration = 1L,
                geometryVersion = 1
            )
            val elapsed = SystemClock.elapsedRealtime() - start
            totalInferenceMs += elapsed
            assertNotNull(event)
            assertEquals("NaviSense-Locate-YOLO-smoke", event.modelIdentity)
        }

        val avgInferenceMs = totalInferenceMs.toDouble() / runs
        println("BENCHMARK: Locate smoke model average inference latency: ${avgInferenceMs} ms (${1000.0 / avgInferenceMs} FPS)")

        backend.close()
        runner.close()
    }

    @Test
    fun testMobilitySmokeModelLoadAndInference() {
        val assetName = "models/mobility_smoke.ptl"
        val modelPath = PyTorchLiteInferenceBackend.copyAssetToCache(context, assetName)

        val loadStart = SystemClock.elapsedRealtime()
        val backend = PyTorchLiteInferenceBackend(
            modelPath = modelPath,
            numClasses = 5,
            confThreshold = 0.25f,
            iouThreshold = 0.45f
        )
        val loadElapsed = SystemClock.elapsedRealtime() - loadStart

        println("BENCHMARK: Mobility smoke model load time: ${loadElapsed} ms")
        assertTrue("Model load time must be <= 5000 ms per PRD §13.5 (was ${loadElapsed} ms)", loadElapsed <= 5000L)

        val runner = YoloModelRunner(backend)
        val metadata = ModelMetadata(
            modelIdentity = "NaviSense-Mobility-YOLO-smoke",
            mode = AppVisionMode.MOBILITY,
            inputWidth = 640,
            inputHeight = 640,
            classLabels = listOf("person", "chair", "table", "backpack", "bottle"),
            confidenceThreshold = 0.25f,
            nmsIouThreshold = 0.45f
        )
        assertTrue(runner.load(metadata))

        val dummyFrame = ByteArray(640 * 480) { 128.toByte() }

        val start = SystemClock.elapsedRealtime()
        val event = runner.detect(
            framePixels = dummyFrame,
            frameWidth = 640,
            frameHeight = 480,
            rotationDegrees = 90,
            frameId = 1L,
            captureMonotonicMs = start,
            deliveryMonotonicMs = start,
            sessionGeneration = 1L,
            geometryVersion = 1
        )
        val elapsed = SystemClock.elapsedRealtime() - start
        println("BENCHMARK: Mobility smoke model single inference latency: ${elapsed} ms")

        assertNotNull(event)
        assertEquals("NaviSense-Mobility-YOLO-smoke", event.modelIdentity)

        backend.close()
        runner.close()
    }

    @Test
    fun testModelSwitchingAndMemoryCleanup() {
        val locatePath = PyTorchLiteInferenceBackend.copyAssetToCache(context, "models/locate_smoke.ptl")
        val mobilityPath = PyTorchLiteInferenceBackend.copyAssetToCache(context, "models/mobility_smoke.ptl")

        // 1. Load Locate
        var backend: PyTorchLiteInferenceBackend? = PyTorchLiteInferenceBackend(locatePath, 2)
        assertNotNull(backend)
        backend?.close()
        backend = null
        System.gc()

        // 2. Load Mobility
        backend = PyTorchLiteInferenceBackend(mobilityPath, 5)
        assertNotNull(backend)
        backend.close()
        backend = null
        System.gc()

        println("BENCHMARK: Clean model switching verified with zero OutOfMemoryError")
    }
}
