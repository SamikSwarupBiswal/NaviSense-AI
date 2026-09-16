package dev.navisense.camera

import android.graphics.ImageFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import dev.navisense.contracts.AppVisionMode
import dev.navisense.contracts.FrameQualityStatus
import dev.navisense.contracts.IClock
import dev.navisense.contracts.MobilePerceptionEvent
import dev.navisense.contracts.SearchEvent
import dev.navisense.inference.ModelRunner
import dev.navisense.search.TargetSearchEngine
import dev.navisense.tracking.VisualTracker
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Samik-owned CameraX producer for H2 perception and H5 search events.
 *
 * The receiver must bind this analyzer to a single-thread executor and an
 * ImageAnalysis use case configured with STRATEGY_KEEP_ONLY_LATEST. The local
 * in-flight gate is a second bound: a concurrent frame is closed immediately.
 */
class CameraXAnalyzer(
    private val clock: IClock,
    private val onPerceptionEvent: (MobilePerceptionEvent) -> Unit,
    private val onSearchEvent: (SearchEvent) -> Unit,
    private val onOverlayDetections: (List<dev.navisense.contracts.DetectedObject>) -> Unit = {},
    private val trackerFactory: () -> VisualTracker = { VisualTracker() },
    private val searchEngineFactory: () -> TargetSearchEngine = { TargetSearchEngine() }
) : ImageAnalysis.Analyzer, AutoCloseable {

    sealed interface StartResult {
        data object Started : StartResult
        data class Rejected(val reason: String) : StartResult
    }

    data class Metrics(
        val receivedFrames: Long,
        val processedFrames: Long,
        val usableFrames: Long,
        val droppedFrames: Long,
        val emittedPerceptionEvents: Long,
        val emittedSearchEvents: Long
    )

    private data class FrameGeometry(
        val width: Int,
        val height: Int,
        val rotationDegrees: Int,
        val cropLeft: Int,
        val cropTop: Int,
        val cropRight: Int,
        val cropBottom: Int
    )

    private class GeometryVersions {
        private var current: FrameGeometry? = null
        private var version = 0

        fun versionFor(geometry: FrameGeometry): Int {
            if (current != geometry) {
                current = geometry
                version = Math.addExact(version, 1)
            }
            return version
        }
    }

    private data class ActiveSession(
        val epoch: Long,
        val sessionGeneration: Long,
        val mode: AppVisionMode,
        val modelIdentity: String,
        val runner: ModelRunner,
        val timestampMapper: CameraTimestampMapper,
        val geometryVersions: GeometryVersions,
        val tracker: VisualTracker,
        val searchEngine: TargetSearchEngine?
    )

    private val activeSession = AtomicReference<ActiveSession?>(null)
    private val processing = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val epochSequence = AtomicLong(0L)
    private val frameSequence = AtomicLong(0L)
    private val receivedFrames = AtomicLong(0L)
    private val processedFrames = AtomicLong(0L)
    private val usableFrames = AtomicLong(0L)
    private val droppedFrames = AtomicLong(0L)
    private val emittedPerceptionEvents = AtomicLong(0L)
    private val emittedSearchEvents = AtomicLong(0L)
    private val dispatchLock = Any()

    /**
     * Activates an already-loaded model after Rishav's readiness checks finish.
     * Search timeout starts here, so callers must not invoke this during loading.
     */
    fun startSession(
        sessionGeneration: Long,
        mode: AppVisionMode,
        runner: ModelRunner,
        targetClass: String? = null
    ): StartResult {
        if (closed.get()) return StartResult.Rejected("Camera analyzer is closed")
        if (sessionGeneration < 0L) return StartResult.Rejected("Session generation must be non-negative")
        if (mode != AppVisionMode.MOBILITY && mode != AppVisionMode.LOCATE_SEARCH) {
            return StartResult.Rejected("Camera analyzer only supports Mobility or Locate Search")
        }

        val metadata = runner.metadata
        if (!runner.isLoaded || metadata == null) {
            return StartResult.Rejected("Requested vision model is not loaded")
        }
        if (metadata.mode != mode) {
            return StartResult.Rejected("Loaded model mode ${metadata.mode} does not match requested mode $mode")
        }
        if (metadata.modelIdentity.isBlank()) {
            return StartResult.Rejected("Loaded model identity is blank")
        }

        val normalizedTarget = targetClass?.trim()?.takeIf { it.isNotEmpty() }
        if (mode == AppVisionMode.LOCATE_SEARCH && normalizedTarget == null) {
            return StartResult.Rejected("Locate Search requires a target class")
        }

        val searchEngine = if (mode == AppVisionMode.LOCATE_SEARCH) {
            searchEngineFactory().also {
                it.startSearch(
                    targetClass = checkNotNull(normalizedTarget),
                    sessionGeneration = sessionGeneration,
                    startMonotonicMs = clock.nowMonotonicMs()
                )
            }
        } else {
            null
        }

        val session = ActiveSession(
            epoch = epochSequence.incrementAndGet(),
            sessionGeneration = sessionGeneration,
            mode = mode,
            modelIdentity = metadata.modelIdentity,
            runner = runner,
            timestampMapper = CameraTimestampMapper(),
            geometryVersions = GeometryVersions(),
            tracker = trackerFactory(),
            searchEngine = searchEngine
        )
        synchronized(dispatchLock) {
            activeSession.set(session)
        }
        return StartResult.Started
    }

    /** Invalidates in-flight work immediately; the caller owns model resource closure. */
    fun stopSession() {
        synchronized(dispatchLock) {
            activeSession.set(null)
            epochSequence.incrementAndGet()
        }
    }

    /** Keeps the 15-second search timeout accurate even if camera frames stop. */
    fun onWatchdogTick(currentTimeMonotonicMs: Long) {
        synchronized(dispatchLock) {
            val session = activeSession.get() ?: return
            if (!isCurrent(session)) return
            val event = session.searchEngine?.onTick(currentTimeMonotonicMs) ?: return
            onSearchEvent(event)
            emittedSearchEvents.incrementAndGet()
        }
    }

    override fun analyze(image: ImageProxy) {
        receivedFrames.incrementAndGet()
        if (!processing.compareAndSet(false, true)) {
            droppedFrames.incrementAndGet()
            image.close()
            return
        }

        var processingSession: ActiveSession? = null
        var processingFrameId = 0L
        var processingCaptureMs = 0L
        var processingGeometryVersion = 0
        try {
            val session = activeSession.get() ?: return
            processingSession = session
            if (closed.get()) return

            val frameId = frameSequence.incrementAndGet()
            processingFrameId = frameId
            val observedNanos = clock.nowMonotonicNanos()
            val deliveryAtReceiptMs = observedNanos / NANOS_PER_MILLISECOND
            val crop = image.cropRect
            val geometry = FrameGeometry(
                width = crop.width(),
                height = crop.height(),
                rotationDegrees = image.imageInfo.rotationDegrees,
                cropLeft = crop.left,
                cropTop = crop.top,
                cropRight = crop.right,
                cropBottom = crop.bottom
            )
            val geometryVersion = session.geometryVersions.versionFor(geometry)
            processingGeometryVersion = geometryVersion

            val timestamp = session.timestampMapper.map(image.imageInfo.timestamp, observedNanos)
            if (timestamp is CameraTimestampMapper.Mapping.Rejected) {
                dispatchPerceptionIfCurrent(
                    session,
                    errorEvent(
                        session = session,
                        frameId = frameId,
                        captureMonotonicMs = timestamp.mappedCaptureMonotonicMs ?: 0L,
                        deliveryMonotonicMs = deliveryAtReceiptMs,
                        geometryVersion = geometryVersion,
                        message = timestamp.reason
                    )
                )
                return
            }
            val captureMonotonicMs = (timestamp as CameraTimestampMapper.Mapping.Usable).captureMonotonicMs
            processingCaptureMs = captureMonotonicMs

            if (geometry.rotationDegrees !in VALID_ROTATIONS) {
                dispatchPerceptionIfCurrent(
                    session,
                    errorEvent(session, frameId, captureMonotonicMs, deliveryAtReceiptMs, geometryVersion, "Unsupported camera rotation")
                )
                return
            }
            if (image.format != ImageFormat.YUV_420_888 || image.planes.size < 3) {
                dispatchPerceptionIfCurrent(
                    session,
                    errorEvent(session, frameId, captureMonotonicMs, deliveryAtReceiptMs, geometryVersion, "Camera must provide YUV_420_888 frames")
                )
                return
            }

            val rgb = Yuv420RgbConverter.convert(
                imageWidth = image.width,
                imageHeight = image.height,
                cropLeft = crop.left,
                cropTop = crop.top,
                cropWidth = crop.width(),
                cropHeight = crop.height(),
                yPlane = image.planes[0].asYuvPlane(),
                uPlane = image.planes[1].asYuvPlane(),
                vPlane = image.planes[2].asYuvPlane()
            )
            val tConvertNanos = clock.nowMonotonicNanos()

            val rawRunnerEvent = session.runner.detect(
                framePixels = rgb,
                frameWidth = geometry.width,
                frameHeight = geometry.height,
                rotationDegrees = geometry.rotationDegrees,
                frameId = frameId,
                captureMonotonicMs = captureMonotonicMs,
                deliveryMonotonicMs = deliveryAtReceiptMs,
                sessionGeneration = session.sessionGeneration,
                geometryVersion = geometryVersion
            )
            val tRunnerNanos = clock.nowMonotonicNanos()
            val runnerEvent = if (
                rawRunnerEvent.qualityStatus == FrameQualityStatus.USABLE &&
                rawRunnerEvent.errorMessage == null &&
                session.mode == AppVisionMode.MOBILITY
            ) {
                val obstacleDetections = rawRunnerEvent.detections.filter {
                    it.label.lowercase() !in NON_MOBILITY_OBSTACLES
                }
                rawRunnerEvent.copy(
                    detections = session.tracker.update(
                        detections = obstacleDetections,
                        currentMonotonicMs = rawRunnerEvent.captureMonotonicMs,
                        geometryVersion = rawRunnerEvent.geometryVersion
                    )
                )
            } else rawRunnerEvent
            // Overlay output is informational only. Safety consumers still receive
            // the freshness-filtered event below and must never use stale boxes.
            onOverlayDetections(runnerEvent.detections)
            val deliveredMs = clock.nowMonotonicNanos() / NANOS_PER_MILLISECOND
            val event = if (deliveredMs < captureMonotonicMs || deliveredMs - captureMonotonicMs > MAX_CAPTURE_AGE_MS) {
                errorEvent(
                    session,
                    frameId,
                    captureMonotonicMs,
                    deliveredMs,
                    geometryVersion,
                    "Processed camera frame exceeded the 500 ms freshness limit"
                )
            } else {
                runnerEvent.copy(deliveryMonotonicMs = deliveredMs)
            }

            processedFrames.incrementAndGet()
            val convertMs = (tConvertNanos - observedNanos) / NANOS_PER_MILLISECOND
            val runnerMs = (tRunnerNanos - tConvertNanos) / NANOS_PER_MILLISECOND
            val ageMs = deliveredMs - captureMonotonicMs

            if (session.mode == AppVisionMode.LOCATE_SEARCH && (frameId % 5L == 0L || runnerEvent.detections.isNotEmpty())) {
                Log.d(
                    TAG,
                    "LOCATE_BENCH frame=$frameId convertMs=$convertMs runnerMs=$runnerMs " +
                        "ageMs=$ageMs rawDets=${runnerEvent.detections.size} safetyDets=${event.detections.size} " +
                        "quality=${event.qualityStatus} err=${event.errorMessage}"
                )
            } else if (frameId % DIAGNOSTIC_FRAME_INTERVAL == 0L) {
                Log.d(
                    TAG,
                    "frame=$frameId mode=${session.mode} ageMs=$ageMs " +
                        "quality=${event.qualityStatus} rawDetections=${runnerEvent.detections.size} " +
                        "safetyDetections=${event.detections.size} error=${event.errorMessage}"
                )
            }
            dispatchCompletedFrameIfCurrent(session, event)
        } catch (failure: Exception) {
            Log.e(TAG, "Camera frame processing failed", failure)
            val session = processingSession
            if (session != null) {
                val nowMs = clock.nowMonotonicNanos() / NANOS_PER_MILLISECOND
                dispatchPerceptionIfCurrent(
                    session,
                    errorEvent(
                        session = session,
                        frameId = processingFrameId.takeIf { it > 0L } ?: frameSequence.incrementAndGet(),
                        captureMonotonicMs = processingCaptureMs,
                        deliveryMonotonicMs = nowMs,
                        geometryVersion = processingGeometryVersion,
                        message = "Camera frame processing failed: ${failure.message ?: failure.javaClass.simpleName}"
                    )
                )
            }
        } finally {
            image.close()
            processing.set(false)
        }
    }

    fun metrics(): Metrics = Metrics(
        receivedFrames = receivedFrames.get(),
        processedFrames = processedFrames.get(),
        usableFrames = usableFrames.get(),
        droppedFrames = droppedFrames.get(),
        emittedPerceptionEvents = emittedPerceptionEvents.get(),
        emittedSearchEvents = emittedSearchEvents.get()
    )

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            stopSession()
        }
    }

    private fun dispatchCompletedFrameIfCurrent(session: ActiveSession, event: MobilePerceptionEvent) {
        synchronized(dispatchLock) {
            if (!isCurrent(session)) return
            val contractValid = event.sessionGeneration == session.sessionGeneration &&
                event.mode == session.mode &&
                event.modelIdentity == session.modelIdentity
            val deliveredEvent = if (!contractValid) {
                errorEvent(
                    session,
                    event.frameId,
                    event.captureMonotonicMs,
                    event.deliveryMonotonicMs,
                    event.geometryVersion,
                    "Model runner changed contract during frame processing"
                )
            } else {
                event
            }

            if (deliveredEvent.qualityStatus == FrameQualityStatus.USABLE && deliveredEvent.errorMessage == null) {
                usableFrames.incrementAndGet()
            }
            onPerceptionEvent(deliveredEvent)
            emittedPerceptionEvents.incrementAndGet()

            if (!isCurrent(session)) return
            val searchEvent = session.searchEngine?.processFrame(deliveredEvent) ?: return
            onSearchEvent(searchEvent)
            emittedSearchEvents.incrementAndGet()
        }
    }

    private fun dispatchPerceptionIfCurrent(session: ActiveSession, event: MobilePerceptionEvent) {
        synchronized(dispatchLock) {
            if (!isCurrent(session)) return
            onPerceptionEvent(event)
            emittedPerceptionEvents.incrementAndGet()
        }
    }

    private fun isCurrent(session: ActiveSession): Boolean {
        return !closed.get() && activeSession.get()?.epoch == session.epoch
    }

    private fun errorEvent(
        session: ActiveSession,
        frameId: Long,
        captureMonotonicMs: Long,
        deliveryMonotonicMs: Long,
        geometryVersion: Int,
        message: String
    ) = MobilePerceptionEvent(
        sessionGeneration = session.sessionGeneration,
        mode = session.mode,
        frameId = frameId,
        captureMonotonicMs = captureMonotonicMs,
        deliveryMonotonicMs = deliveryMonotonicMs,
        geometryVersion = geometryVersion,
        modelIdentity = session.modelIdentity,
        qualityStatus = FrameQualityStatus.UNUSABLE,
        detections = emptyList(),
        errorMessage = message
    )

    private fun ImageProxy.PlaneProxy.asYuvPlane() = YuvPlane(
        buffer = buffer,
        rowStride = rowStride,
        pixelStride = pixelStride
    )

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MAX_CAPTURE_AGE_MS = 500L
        const val DIAGNOSTIC_FRAME_INTERVAL = 30L
        const val TAG = "NaviSenseCameraAnalyzer"
        val VALID_ROTATIONS = setOf(0, 90, 180, 270)
        val NON_MOBILITY_OBSTACLES = setOf("keys", "wallet")
    }
}
