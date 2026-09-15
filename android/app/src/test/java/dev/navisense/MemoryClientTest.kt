package dev.navisense

import dev.navisense.app.SessionCoordinator
import dev.navisense.contracts.AppMode
import dev.navisense.contracts.SessionGeneration
import dev.navisense.networking.LocateResult
import dev.navisense.networking.MemoryClient
import dev.navisense.networking.MemoryClientContract
import dev.navisense.voice.SpeechRequest
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class MemoryClientTest {

    private fun mockOkHttpClient(responder: (Request) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain -> responder(chain.request()) }
            .build()
    }

    private fun jsonResponse(request: Request, code: Int, body: String): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Error")
            .body(body.toResponseBody("application/json".toMediaTypeOrNull()))
            .build()
    }

    @Test
    fun testLocateFoundValidCandidate() = runBlocking {
        val fakeClock = FakeClock(1000L)
        val json = """
        {
          "status": "found",
          "query_name": "keys",
          "canonical_name": "keys",
          "target_class": "keys",
          "candidates": [
            {
              "instance_id": "inst_001",
              "zone_id": "zone_center",
              "zone_name": "Center Table",
              "box": [0.35, 0.40, 0.50, 0.55],
              "confidence": 0.88
            }
          ],
          "scan_id": "scan_1234",
          "observation_time_iso": "2026-09-15T10:00:00.000Z",
          "age_seconds": 15.4,
          "camera_profile_id": "tabletop_cam_v1"
        }
        """.trimIndent()

        val client = MemoryClient(
            baseUrl = "http://127.0.0.1:8000",
            client = mockOkHttpClient { req ->
                fakeClock.advanceBy(100L) // 100 ms network duration
                jsonResponse(req, 200, json)
            },
            clock = fakeClock
        )

        val result = client.locateObject("keys", 1L)
        assertTrue("Expected Found, got $result", result is LocateResult.Found)
        val found = result as LocateResult.Found

        assertEquals("keys", found.queryName)
        assertEquals("keys", found.canonicalName)
        assertEquals("inst_001", found.candidate.instanceId)
        assertEquals("zone_center", found.candidate.zoneId)
        assertEquals("Center Table", found.candidate.zoneName)
        assertEquals(0.88f, found.candidate.confidence, 0.001f)
        assertEquals(0.35f, found.candidate.box.left, 0.001f)
        assertEquals(0.40f, found.candidate.box.top, 0.001f)
        assertEquals(0.50f, found.candidate.box.right, 0.001f)
        assertEquals(0.55f, found.candidate.box.bottom, 0.001f)
        assertEquals(15.5, found.ageSeconds, 0.001) // 15.4s + 0.1s duration
        assertEquals("scan_1234", found.scanId)
    }

    @Test
    fun testLocateAmbiguousCandidates() = runBlocking {
        val fakeClock = FakeClock(1000L)
        val json = """
        {
          "status": "ambiguous",
          "query_name": "house keys",
          "canonical_name": "keys",
          "candidates": [
            {
              "instance_id": "inst_001",
              "zone_id": "zone_left",
              "zone_name": "Left Table",
              "box": [0.05, 0.20, 0.25, 0.45],
              "confidence": 0.79
            },
            {
              "instance_id": "inst_002",
              "zone_id": "zone_right",
              "zone_name": "Right Table",
              "box": [0.70, 0.30, 0.85, 0.50],
              "confidence": 0.84
            }
          ],
          "scan_id": "scan_multi",
          "age_seconds": 22.1,
          "camera_profile_id": "tabletop_cam_v1"
        }
        """.trimIndent()

        val client = MemoryClient(
            baseUrl = "http://127.0.0.1:8000",
            client = mockOkHttpClient { req -> jsonResponse(req, 200, json) },
            clock = fakeClock
        )

        val result = client.locateObject("house keys", 1L)
        assertTrue(result is LocateResult.Ambiguous)
        val ambiguous = result as LocateResult.Ambiguous
        assertEquals(2, ambiguous.candidates.size)
        assertEquals("zone_left", ambiguous.candidates[0].zoneId)
        assertEquals("zone_right", ambiguous.candidates[1].zoneId)
    }

    @Test
    fun testLocateStaleExplicitStatus() = runBlocking {
        val fakeClock = FakeClock(1000L)
        val json = """
        {
          "status": "stale",
          "query_name": "wallet",
          "canonical_name": "wallet",
          "candidates": [
            {
              "instance_id": "inst_003",
              "zone_id": "zone_right",
              "zone_name": "Right Table",
              "box": [0.72, 0.15, 0.90, 0.40],
              "confidence": 0.91
            }
          ],
          "scan_id": "scan_stale",
          "age_seconds": 320.0,
          "camera_profile_id": "tabletop_cam_v1"
        }
        """.trimIndent()

        val client = MemoryClient(
            baseUrl = "http://127.0.0.1:8000",
            client = mockOkHttpClient { req -> jsonResponse(req, 200, json) },
            clock = fakeClock
        )

        val result = client.locateObject("wallet", 1L)
        assertTrue(result is LocateResult.Stale)
        val stale = result as LocateResult.Stale
        assertEquals("Right Table", stale.candidate.zoneName)
        assertTrue(stale.ageSeconds > 60.0)
    }

    @Test
    fun testLocateFoundBecomesStaleWhenAgeExceedsThreshold() = runBlocking {
        val fakeClock = FakeClock(1000L)
        val json = """
        {
          "status": "found",
          "query_name": "keys",
          "canonical_name": "keys",
          "candidates": [
            {
              "instance_id": "inst_001",
              "zone_id": "zone_center",
              "box": [0.35, 0.40, 0.50, 0.55],
              "confidence": 0.88
            }
          ],
          "age_seconds": 65.0
        }
        """.trimIndent()

        val client = MemoryClient(
            baseUrl = "http://127.0.0.1:8000",
            client = mockOkHttpClient { req -> jsonResponse(req, 200, json) },
            clock = fakeClock
        )

        val result = client.locateObject("keys", 1L)
        assertTrue("Expected Stale due to age > 60s, got $result", result is LocateResult.Stale)
    }

    @Test
    fun testLocateHistoricalOnly() = runBlocking {
        val fakeClock = FakeClock(1000L)
        val json = """
        {
          "status": "historical_only",
          "query_name": "wallet",
          "canonical_name": "wallet",
          "candidates": [],
          "age_seconds": 9020.0,
          "camera_profile_id": "tabletop_cam_v0"
        }
        """.trimIndent()

        val client = MemoryClient(
            baseUrl = "http://127.0.0.1:8000",
            client = mockOkHttpClient { req -> jsonResponse(req, 200, json) },
            clock = fakeClock
        )

        val result = client.locateObject("wallet", 1L)
        assertTrue(result is LocateResult.HistoricalOnly)
    }

    @Test
    fun testLocateNotFoundAndUnsupported() = runBlocking {
        val fakeClock = FakeClock(1000L)
        val notFoundJson = """
        {
          "status": "not_found",
          "query_name": "keys",
          "canonical_name": "keys",
          "candidates": []
        }
        """.trimIndent()

        val unsupportedJson = """
        {
          "status": "unsupported",
          "query_name": "laptop",
          "candidates": []
        }
        """.trimIndent()

        var currentBody = notFoundJson
        val client = MemoryClient(
            baseUrl = "http://127.0.0.1:8000",
            client = mockOkHttpClient { req -> jsonResponse(req, 200, currentBody) },
            clock = fakeClock
        )

        val nfResult = client.locateObject("keys", 1L)
        assertTrue(nfResult is LocateResult.NotFound)

        currentBody = unsupportedJson
        val unsuppResult = client.locateObject("laptop", 1L)
        assertTrue(unsuppResult is LocateResult.Unsupported)
    }

    @Test
    fun testHealthCheckEndpoint() = runBlocking {
        var isHealthy = true
        val client = MemoryClient(
            baseUrl = "http://127.0.0.1:8000",
            client = mockOkHttpClient { req ->
                val body = if (isHealthy) {
                    """{"service_status":"ok","camera_connected":true,"memory_records_count":42,"active_profile_id":"tabletop_cam_v1"}"""
                } else {
                    """{"service_status":"degraded","camera_connected":false,"memory_records_count":42,"active_profile_id":"tabletop_cam_v1"}"""
                }
                jsonResponse(req, 200, body)
            }
        )

        assertTrue(client.checkHealth())

        isHealthy = false
        assertFalse(client.checkHealth())
    }

    @Test
    fun testEnforces64KiBResponseLimit() = runBlocking {
        val fakeClock = FakeClock(1000L)
        // Construct string larger than 65536 bytes
        val largePadding = "x".repeat(70000)
        val oversizedJson = """{"status":"found","query_name":"keys","padding":"$largePadding"}"""

        val client = MemoryClient(
            baseUrl = "http://127.0.0.1:8000",
            client = mockOkHttpClient { req -> jsonResponse(req, 200, oversizedJson) },
            clock = fakeClock
        )

        val result = client.locateObject("keys", 1L)
        assertTrue("Expected NetworkError on oversized response, got $result", result is LocateResult.NetworkError)
        val err = result as LocateResult.NetworkError
        assertTrue(err.message.contains("64 KiB"))
    }

    @Test
    fun testEnforces64CharacterQueryLimit() = runBlocking {
        val client = MemoryClient(baseUrl = "http://127.0.0.1:8000")
        val longQuery = "a".repeat(65)
        val result = client.locateObject(longQuery, 1L)

        assertTrue(result is LocateResult.NetworkError)
        val err = result as LocateResult.NetworkError
        assertEquals(400, err.statusCode)
    }

    @Test
    fun testHttpErrorsAreNotNotFound() = runBlocking {
        val client = MemoryClient(
            baseUrl = "http://127.0.0.1:8000",
            client = mockOkHttpClient { req ->
                jsonResponse(req, 401, """{"api_version":1,"message":"Unauthorized access"}""")
            }
        )

        val result = client.locateObject("keys", 1L)
        assertTrue("HTTP 401 must produce NetworkError, not NotFound", result is LocateResult.NetworkError)
        val err = result as LocateResult.NetworkError
        assertEquals(401, err.statusCode)
        assertTrue(err.message.contains("Unauthorized"))
    }

    @Test
    fun testSessionGenerationInvalidationBeforeAndAfter() = runBlocking {
        val activeSessionGen = AtomicLong(1L)
        val client = MemoryClient(
            baseUrl = "http://127.0.0.1:8000",
            activeSessionProvider = { activeSessionGen.get() },
            client = mockOkHttpClient { req ->
                // Simulate generation advancing during network execution
                activeSessionGen.set(2L)
                jsonResponse(req, 200, """{"status":"found","query_name":"keys","canonical_name":"keys","candidates":[{"instance_id":"1","zone_id":"z1","box":[0,0,1,1],"confidence":0.9}]}""")
            }
        )

        val result = client.locateObject("keys", 1L)
        assertTrue("Superseded session generation must result in NetworkError", result is LocateResult.NetworkError)
        val err = result as LocateResult.NetworkError
        assertTrue(err.message.contains("superseded"))
    }

    @Test
    fun testSessionCoordinatorLocateAndGuideIntegration() = runBlocking {
        val fakeClock = FakeClock(1000L)
        val sessionGen = SessionGeneration(1L)

        val spokenPhrases = mutableListOf<String>()
        val mockSpeechArbiter = object : dev.navisense.voice.ISpeechArbiter {
            override fun speak(request: SpeechRequest): Boolean {
                spokenPhrases.add(request.phrase)
                return true
            }
            override fun cancelAll() {
                spokenPhrases.add("CANCEL_ALL")
            }
            override fun invalidateSession(newGeneration: Long) {}
        }

        val json = """
        {
          "status": "found",
          "query_name": "keys",
          "canonical_name": "keys",
          "candidates": [
            {
              "instance_id": "inst_001",
              "zone_id": "zone_center",
              "zone_name": "Center Table",
              "box": [0.35, 0.40, 0.50, 0.55],
              "confidence": 0.88
            }
          ],
          "age_seconds": 12.0
        }
        """.trimIndent()

        val mockMemoryClient = MemoryClient(
            baseUrl = "http://127.0.0.1:8000",
            client = mockOkHttpClient { req -> jsonResponse(req, 200, json) },
            clock = fakeClock,
            activeSessionProvider = { sessionGen.get() }
        )

        val coordinator = SessionCoordinator(
            sessionGeneration = sessionGen,
            clock = fakeClock,
            speechArbiter = mockSpeechArbiter,
            memoryClient = mockMemoryClient
        )

        // 1. Initial State
        assertEquals(AppMode.IDLE, coordinator.currentMode)

        // 2. Query Memory and Guide
        val result = coordinator.locateAndGuide("keys")
        assertTrue(result is LocateResult.Found)
        assertEquals(AppMode.MOBILITY, coordinator.currentMode)
        assertEquals("keys", coordinator.activeTargetClass)
        assertEquals("Center Table", coordinator.activeTargetZone)
        assertTrue(spokenPhrases.any { it.contains("Last seen at Center Table. Obstacle assistance started.") })

        // 3. Confirm arrival at target zone
        val arrivalToken = coordinator.confirmArrivalAtZone()
        assertNotNull(arrivalToken)
        assertEquals(AppMode.FINAL_SEARCH, coordinator.currentMode)
        assertTrue(spokenPhrases.any { it.contains("Arrived at zone. Searching for keys") })

        // 4. User Stop cancels everything
        coordinator.userStop()
        assertEquals(AppMode.IDLE, coordinator.currentMode)
        assertEquals(null, coordinator.activeTargetClass)
        assertEquals(null, coordinator.activeTargetZone)
        assertTrue(spokenPhrases.contains("CANCEL_ALL"))
    }
}
