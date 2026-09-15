package dev.navisense

import android.content.Context
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
    private val testContext: Context get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun testStaleCacheReplacement() {
        val asset = "models/locate_smoke.ptl"
        val path = PyTorchLiteInferenceBackend.copyAssetToCache(context, asset)
        java.io.File(path).writeBytes(byteArrayOf(1, 2, 3))
        PyTorchLiteInferenceBackend.copyAssetToCache(context, asset)
        assertArrayEquals(context.assets.open(asset).use { it.readBytes() }, java.io.File(path).readBytes())
    }

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
        val dummyFrame = ByteArray(640 * 480) { if (it % 2 == 0) 40 else 180.toByte() }

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
            assertEquals(dev.navisense.contracts.FrameQualityStatus.USABLE, event.qualityStatus)
            assertEquals(i + 1, backend.completedForwardPasses)
            assertEquals("NaviSense-Locate-YOLO-smoke", event.modelIdentity)
        }

        val avgInferenceMs = totalInferenceMs.toDouble() / runs
        println("BENCHMARK: Locate synthetic pipeline mean (quality + preprocessing + forward + decode): ${avgInferenceMs} ms; completed forward calls=${backend.completedForwardPasses}")

        backend.close()
        runner.close()
    }

    @Test
    fun testLocateModelDetectsLabeledWalletImageOnDevice() {
        assertLocateFixture("locate_wallet_positive.jpg", "wallet")
    }

    @Test
    fun testLocateModelDetectsLabeledKeysImageOnDevice() {
        assertLocateFixture("locate_keys_positive.jpg", "keys")
    }

    private fun assertLocateFixture(assetName: String, expectedLabel: String) {
        val modelPath = PyTorchLiteInferenceBackend.copyAssetToCache(context, "models/locate_smoke.ptl")
        val backend = PyTorchLiteInferenceBackend(
            modelPath = modelPath,
            numClasses = 2,
            confThreshold = 0.25f,
            iouThreshold = 0.45f
        )
        val runner = YoloModelRunner(backend)
        assertTrue(runner.load(ModelMetadata(
            modelIdentity = "locate-v0.2.0-spandan-85a6d1cf",
            mode = AppVisionMode.LOCATE_SEARCH,
            inputWidth = 640,
            inputHeight = 640,
            classLabels = listOf("keys", "wallet"),
            confidenceThreshold = 0.25f,
            nmsIouThreshold = 0.45f
        )))

        val bitmap = testContext.assets.open(assetName).use(BitmapFactory::decodeStream)
        assertNotNull("Positive $expectedLabel fixture must decode", bitmap)
        val argb = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val rgb = ByteArray(argb.size * 3)
        argb.forEachIndexed { index, color ->
            rgb[index * 3] = ((color shr 16) and 0xFF).toByte()
            rgb[index * 3 + 1] = ((color shr 8) and 0xFF).toByte()
            rgb[index * 3 + 2] = (color and 0xFF).toByte()
        }

        val now = SystemClock.elapsedRealtime()
        val event = runner.detect(
            framePixels = rgb,
            frameWidth = bitmap.width,
            frameHeight = bitmap.height,
            rotationDegrees = 0,
            frameId = 1L,
            captureMonotonicMs = now,
            deliveryMonotonicMs = now,
            sessionGeneration = 1L,
            geometryVersion = 1
        )

        assertEquals(dev.navisense.contracts.FrameQualityStatus.USABLE, event.qualityStatus)
        val detection = event.detections.filter { it.label == expectedLabel }.maxByOrNull { it.confidence }
        assertNotNull("Expected the labeled $expectedLabel fixture to produce a detection", detection)
        assertTrue("$expectedLabel confidence must meet the Search Nearby threshold", detection!!.confidence >= 0.60f)
        assertEquals(1, backend.completedForwardPasses)

        bitmap.recycle()
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

        val dummyFrame = ByteArray(640 * 480) { if (it % 2 == 0) 40 else 180.toByte() }

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
        println("BENCHMARK: Mobility synthetic pipeline single sample (quality + preprocessing + forward + decode): ${elapsed} ms; completed forward calls=${backend.completedForwardPasses}")

        assertNotNull(event)
        assertEquals(dev.navisense.contracts.FrameQualityStatus.USABLE, event.qualityStatus)
        assertEquals(1, backend.completedForwardPasses)
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

        println("BENCHMARK: Sequential model load/close completed; memory leaks were not measured")
    }
}
