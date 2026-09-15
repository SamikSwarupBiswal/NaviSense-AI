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

    @Test fun searchTargetNormalizationAndInvalidTarget() {
        assertEquals(SearchTarget.KEYS, SearchTarget.fromValue("keys"))
        assertEquals(SearchTarget.KEYS, SearchTarget.fromValue("Keys"))
        assertEquals(SearchTarget.KEYS, SearchTarget.fromValue("keychain"))
        assertEquals(SearchTarget.WALLET, SearchTarget.fromValue("wallet"))
        assertEquals(SearchTarget.WALLET, SearchTarget.fromValue("Purse"))
        assertNull(SearchTarget.fromValue("unknown_item"))

        val engine = TargetSearchEngine()
        engine.startSearch("keychain", 1L, 1000L)
        assertTrue(engine.isSearchActive)

        assertThrows(IllegalArgumentException::class.java) {
            engine.startSearch("chair", 2L, 1000L)
        }
    }

    @Test fun watchdogOnTickEmitsTimeoutWithoutFrames() {
        val engine = TargetSearchEngine(searchTimeoutMs = 15000L)
        engine.startSearch("keys", 1L, 1000L)

        // Before 15 seconds: null
        assertNull(engine.onTick(1000L + 5000L))
        assertNull(engine.onTick(1000L + 14999L))

        // At 15 seconds: TIMEOUT event emitted
        val event = engine.onTick(1000L + 15000L)
        assertNotNull(event)
        assertEquals(SearchStatus.TIMEOUT, event?.status)
        assertEquals("keys", event?.targetClass)

        // Subsequent onTick calls do not duplicate timeout
        assertNull(engine.onTick(1000L + 16000L))
    }

    @Test fun sessionCoordinatorNearbySearchLifecycle() {
        val fakeClock = FakeClock(1000L)
        val sessionGen = SessionGeneration(1L)
        val spokenPhrases = mutableListOf<String>()
        val mockSpeechArbiter = object : dev.navisense.voice.ISpeechArbiter {
            override fun speak(request: dev.navisense.voice.SpeechRequest): Boolean {
                spokenPhrases.add(request.phrase)
                return true
            }
            override fun cancelAll() {
                spokenPhrases.add("CANCEL_ALL")
            }
            override fun invalidateSession(newGeneration: Long) {}
        }

        val coordinator = dev.navisense.app.SessionCoordinator(
            sessionGeneration = sessionGen,
            clock = fakeClock,
            speechArbiter = mockSpeechArbiter
        )

        var observedSearchState: SearchUiState = SearchUiState.NONE
        coordinator.addListener(object : dev.navisense.app.SessionCoordinator.StateChangeListener {
            override fun onModeChanged(newMode: AppMode, token: SessionToken) {}
            override fun onPathStatusChanged(newStatus: PathStatus) {}
            override fun onSensorHealthChanged(newHealth: SensorHealth) {}
            override fun onSearchStateChanged(newState: SearchUiState, event: SearchEvent?) {
                observedSearchState = newState
            }
        })

        // 1. Start nearby search
        val token = coordinator.startNearbySearch("keys")
        assertEquals(AppMode.FINAL_SEARCH, coordinator.currentMode)
        assertEquals(SearchUiState.LOADING_MODEL, observedSearchState)
        assertEquals(SearchUiState.LOADING_MODEL, coordinator.currentSearchState)
        assertTrue(spokenPhrases.contains("Searching for keys"))

        // 2. Mark ready when model loaded
        assertTrue(coordinator.markNearbySearchReady(token.generation))
        assertEquals(SearchUiState.SEARCHING, observedSearchState)

        // 3. Confirm target found
        val confirmEvent = SearchEvent(
            sessionGeneration = token.generation,
            targetClass = "keys",
            status = SearchStatus.CONFIRMED,
            direction = TargetDirection.LEFT,
            candidateCount = 1,
            timestampMonotonicMs = 2000L
        )
        coordinator.onSearchEvent(confirmEvent)
        assertEquals(AppMode.FOUND, coordinator.currentMode)
        assertEquals(SearchUiState.FOUND, observedSearchState)
        assertTrue(spokenPhrases.any { it.contains("Target found left") })

        // 4. Retry search
        coordinator.retryNearbySearch()
        assertEquals(AppMode.FINAL_SEARCH, coordinator.currentMode)
        assertEquals(SearchUiState.LOADING_MODEL, coordinator.currentSearchState)

        // 5. Fail search
        coordinator.failNearbySearch("Model error")
        assertEquals(AppMode.IDLE, coordinator.currentMode)
        assertEquals(SearchUiState.ERROR, observedSearchState)
        assertTrue(spokenPhrases.contains("Nearby search is unavailable."))
    }
}
