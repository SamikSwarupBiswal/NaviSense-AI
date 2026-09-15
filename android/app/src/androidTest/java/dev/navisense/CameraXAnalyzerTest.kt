package dev.navisense

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.navisense.camera.CameraXAnalyzer
import dev.navisense.contracts.AppVisionMode
import dev.navisense.contracts.FrameQualityStatus
import dev.navisense.contracts.MobilePerceptionEvent
import dev.navisense.contracts.SearchConfirmationEvent
import dev.navisense.contracts.SearchEvent
import dev.navisense.contracts.SystemMonotonicClock
import dev.navisense.inference.InferenceBackend
import dev.navisense.inference.ModelMetadata
import dev.navisense.inference.RawDetection
import dev.navisense.inference.YoloModelRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class CameraXAnalyzerTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun cameraProviderInitializesOnDevice() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val provider = providerFuture.get(10, TimeUnit.SECONDS)
        assertNotNull("ProcessCameraProvider must resolve on device", provider)
    }

    @Test
    fun analyzerPipelineProcessesYuvAndEmitsPerceptionEvents() {
        val clock = SystemMonotonicClock()
        val perceptionEvents = CopyOnWriteArrayList<MobilePerceptionEvent>()
        val latch = CountDownLatch(1)

        val runner = YoloModelRunner(
            InferenceBackend { _, _, _, _, _ ->
                listOf(RawDetection(classId = 0, confidence = 0.85f, boxX1 = 50f, boxY1 = 50f, boxX2 = 150f, boxY2 = 150f))
            }
        )
        runner.load(
            ModelMetadata(
                modelIdentity = "camera-test-model",
                mode = AppVisionMode.MOBILITY,
                inputWidth = 320,
                inputHeight = 320,
                classLabels = listOf("obstacle")
            )
        )

        val analyzer = CameraXAnalyzer(
            clock = clock,
            onPerceptionEvent = { event ->
                perceptionEvents.add(event)
                latch.countDown()
            },
            onSearchEvent = {}
        )

        val startResult = analyzer.startSession(
            sessionGeneration = 42L,
            mode = AppVisionMode.MOBILITY,
            runner = runner
        )
        assertEquals(CameraXAnalyzer.StartResult.Started, startResult)

        val fakeImage = createFakeYuvImage(
            width = 640,
            height = 480,
            timestampNanos = 1_000_000_000L
        )

        analyzer.analyze(fakeImage)

        assertTrue("Expected perception event within 5 seconds", latch.await(5, TimeUnit.SECONDS))
        assertTrue("Fake image must be closed by analyzer", fakeImage.isClosed)
        assertEquals(1, perceptionEvents.size)

        val event = perceptionEvents.first()
        assertEquals(42L, event.sessionGeneration)
        assertEquals(AppVisionMode.MOBILITY, event.mode)
        assertEquals("camera-test-model", event.modelIdentity)
        assertEquals(FrameQualityStatus.USABLE, event.qualityStatus)
        assertEquals(1, event.detections.size)
        assertEquals("obstacle", event.detections.first().label)
        assertTrue(event.deliveryMonotonicMs >= event.captureMonotonicMs)

        val metrics = analyzer.metrics()
        assertEquals(1L, metrics.receivedFrames)
        assertEquals(1L, metrics.processedFrames)
        assertEquals(1L, metrics.usableFrames)
        assertEquals(0L, metrics.droppedFrames)

        analyzer.close()
        runner.close()
    }

    @Test
    fun analyzerRoutesLocateSearchAndEmitsSearchEvents() {
        val clock = SystemMonotonicClock()
        val searchEvents = CopyOnWriteArrayList<SearchEvent>()
        val searchLatch = CountDownLatch(1)

        val runner = YoloModelRunner(
            InferenceBackend { _, _, _, _, _ ->
                listOf(RawDetection(classId = 0, confidence = 0.90f, boxX1 = 100f, boxY1 = 100f, boxX2 = 200f, boxY2 = 200f))
            }
        )
        runner.load(
            ModelMetadata(
                modelIdentity = "locate-search-model",
                mode = AppVisionMode.LOCATE_SEARCH,
                inputWidth = 640,
                inputHeight = 640,
                classLabels = listOf("keys")
            )
        )

        val analyzer = CameraXAnalyzer(
            clock = clock,
            onPerceptionEvent = {},
            onSearchEvent = { event ->
                searchEvents.add(event)
                searchLatch.countDown()
            }
        )

        val startResult = analyzer.startSession(
            sessionGeneration = 101L,
            mode = AppVisionMode.LOCATE_SEARCH,
            runner = runner,
            targetClass = "keys"
        )
        assertEquals(CameraXAnalyzer.StartResult.Started, startResult)

        val fakeImage = createFakeYuvImage(
            width = 640,
            height = 480,
            timestampNanos = 2_000_000_000L
        )

        analyzer.analyze(fakeImage)

        assertTrue("Expected search event within 5 seconds", searchLatch.await(5, TimeUnit.SECONDS))
        assertEquals(1, searchEvents.size)
        val searchEvent = searchEvents.first()
        assertTrue(searchEvent is SearchConfirmationEvent)
        val conf = searchEvent as SearchConfirmationEvent
        assertEquals("keys", conf.targetClass)
        assertEquals(101L, conf.sessionGeneration)

        analyzer.close()
        runner.close()
    }

    @Test
    fun analyzerDropsConcurrentFramesWhenBusy() {
        val clock = SystemMonotonicClock()
        val blockInferenceLatch = CountDownLatch(1)
        val inferenceStartedLatch = CountDownLatch(1)

        val slowRunner = YoloModelRunner(
            InferenceBackend { _, _, _, _, _ ->
                inferenceStartedLatch.countDown()
                blockInferenceLatch.await(5, TimeUnit.SECONDS)
                emptyList()
            }
        )
        slowRunner.load(
            ModelMetadata(
                modelIdentity = "slow-model",
                mode = AppVisionMode.MOBILITY,
                inputWidth = 320,
                inputHeight = 320,
                classLabels = listOf("object")
            )
        )

        val analyzer = CameraXAnalyzer(
            clock = clock,
            onPerceptionEvent = {},
            onSearchEvent = {}
        )
        analyzer.startSession(1L, AppVisionMode.MOBILITY, slowRunner)

        val thread = Thread {
            val firstFrame = createFakeYuvImage(640, 480, 1_000_000_000L)
            analyzer.analyze(firstFrame)
        }
        thread.start()

        assertTrue("Wait for inference to start", inferenceStartedLatch.await(3, TimeUnit.SECONDS))

        val secondFrame = createFakeYuvImage(640, 480, 1_033_000_000L)
        analyzer.analyze(secondFrame)

        assertTrue("Second frame must be closed immediately on drop", secondFrame.isClosed)

        blockInferenceLatch.countDown()
        thread.join(3000)

        val metrics = analyzer.metrics()
        assertEquals(2L, metrics.receivedFrames)
        assertEquals(1L, metrics.droppedFrames)
        assertEquals(1L, metrics.processedFrames)

        analyzer.close()
        slowRunner.close()
    }

    @Test
    fun liveRearCameraProducesFreshH2EventsWhenPermissionGranted() {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasCameraPermission) {
            println("SKIPPING live rear camera test: Manifest.permission.CAMERA not yet granted on device")
            return
        }

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val provider = ProcessCameraProvider.getInstance(context).get(10, TimeUnit.SECONDS)
        val owner = ResumedLifecycleOwner()
        val executor = Executors.newSingleThreadExecutor()
        val events = CopyOnWriteArrayList<MobilePerceptionEvent>()
        val eventLatch = CountDownLatch(2)
        val runner = YoloModelRunner(InferenceBackend { _, _, _, _, _ -> emptyList() })
        runner.load(
            ModelMetadata(
                modelIdentity = "camera-live-smoke",
                mode = AppVisionMode.MOBILITY,
                inputWidth = 640,
                inputHeight = 640,
                classLabels = listOf("person")
            )
        )
        val analyzer = CameraXAnalyzer(
            clock = SystemMonotonicClock(),
            onPerceptionEvent = {
                events += it
                eventLatch.countDown()
            },
            onSearchEvent = {}
        )
        assertEquals(CameraXAnalyzer.StartResult.Started, analyzer.startSession(7L, AppVisionMode.MOBILITY, runner))

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { it.setAnalyzer(executor, analyzer) }

        try {
            instrumentation.runOnMainSync {
                owner.resume()
                provider.unbindAll()
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, imageAnalysis)
            }

            assertTrue("Expected at least two live rear-camera frames", eventLatch.await(10, TimeUnit.SECONDS))
            assertTrue(events.size >= 2)
            assertTrue(events.zipWithNext().all { (first, second) -> second.frameId > first.frameId })
            assertTrue(events.all { it.sessionGeneration == 7L && it.mode == AppVisionMode.MOBILITY })
            assertTrue(events.all { it.geometryVersion > 0 && it.captureMonotonicMs > 0L })
            assertTrue(events.all { it.deliveryMonotonicMs >= it.captureMonotonicMs })
            assertTrue(analyzer.metrics().processedFrames >= 2L)
        } finally {
            instrumentation.runOnMainSync {
                provider.unbindAll()
                owner.destroy()
            }
            analyzer.close()
            runner.close()
            executor.shutdownNow()
        }
    }

    private fun createFakeYuvImage(
        width: Int,
        height: Int,
        timestampNanos: Long,
        rotationDegrees: Int = 0
    ): FakeImageProxy {
        val ySize = width * height
        val uvWidth = width / 2
        val uvHeight = height / 2
        val uvSize = uvWidth * uvHeight

        // Generate high-contrast pattern so FrameQualityChecker detects sufficient variation
        val yData = ByteArray(ySize) { i ->
            val x = i % width
            val y = i / width
            if ((x / 32 + y / 32) % 2 == 0) 30.toByte() else 210.toByte()
        }
        val uData = ByteArray(uvSize) { 128.toByte() }
        val vData = ByteArray(uvSize) { 128.toByte() }

        val planes: Array<ImageProxy.PlaneProxy> = arrayOf(
            FakePlaneProxy(ByteBuffer.wrap(yData), rowStride = width, pixelStride = 1),
            FakePlaneProxy(ByteBuffer.wrap(uData), rowStride = uvWidth, pixelStride = 1),
            FakePlaneProxy(ByteBuffer.wrap(vData), rowStride = uvWidth, pixelStride = 1)
        )

        return FakeImageProxy(
            width = width,
            height = height,
            format = ImageFormat.YUV_420_888,
            cropRect = Rect(0, 0, width, height),
            planes = planes,
            imageInfo = FakeImageInfo(timestamp = timestampNanos, rotationDegrees = rotationDegrees)
        )
    }

    private class FakePlaneProxy(
        private val byteBuffer: ByteBuffer,
        private val rowStride: Int,
        private val pixelStride: Int
    ) : ImageProxy.PlaneProxy {
        override fun getRowStride(): Int = rowStride
        override fun getPixelStride(): Int = pixelStride
        override fun getBuffer(): ByteBuffer = byteBuffer
    }

    private class FakeImageInfo(
        private val timestamp: Long,
        private val rotationDegrees: Int
    ) : ImageInfo {
        override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()
        override fun getTimestamp(): Long = timestamp
        override fun getRotationDegrees(): Int = rotationDegrees
        override fun populateExifData(builder: ExifData.Builder) {}
    }

    private class FakeImageProxy(
        private val width: Int,
        private val height: Int,
        private val format: Int,
        private var cropRect: Rect,
        private val planes: Array<ImageProxy.PlaneProxy>,
        private val imageInfo: ImageInfo
    ) : ImageProxy {
        private val closed = AtomicBoolean(false)
        val isClosed: Boolean get() = closed.get()

        override fun getCropRect(): Rect = cropRect
        override fun setCropRect(rect: Rect?) {
            cropRect = rect ?: Rect(0, 0, width, height)
        }
        override fun getFormat(): Int = format
        override fun getWidth(): Int = width
        override fun getHeight(): Int = height
        override fun getPlanes(): Array<ImageProxy.PlaneProxy> = planes
        override fun getImageInfo(): ImageInfo = imageInfo
        override fun getImage(): Image? = null
        override fun close() {
            closed.set(true)
        }
    }

    private class ResumedLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun resume() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun destroy() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
    }
}

