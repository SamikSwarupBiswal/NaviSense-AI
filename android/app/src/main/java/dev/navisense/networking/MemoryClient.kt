package dev.navisense.networking

import dev.navisense.contracts.IClock
import dev.navisense.contracts.NormalizedRect
import dev.navisense.contracts.SystemMonotonicClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Production implementation of MemoryClientContract for communicating with the
 * Laptop Locate REST API (PRD Sections 13.3, 23 & 24).
 *
 * Owned by Rishav / Subham.
 */
class MemoryClient(
    private val baseUrl: String = "http://127.0.0.1:8000",
    private val bearerToken: String? = null,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(MemoryClientContract.MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(MemoryClientContract.MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(MemoryClientContract.MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(MemoryClientContract.MAX_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build(),
    private val clock: IClock = SystemMonotonicClock(),
    private val activeSessionProvider: (() -> Long)? = null
) : MemoryClientContract {

    @Volatile
    private var inFlightCall: Call? = null

    override suspend fun locateObject(queryName: String, sessionGeneration: Long): LocateResult =
        withContext(Dispatchers.IO) {
            val trimmedQuery = queryName.trim()

            // PRD §13.3: Decoded name limited to 64 characters
            if (trimmedQuery.isEmpty() || trimmedQuery.length > 64) {
                return@withContext LocateResult.NetworkError(
                    queryName = trimmedQuery,
                    statusCode = 400,
                    message = "Query name must be between 1 and 64 characters"
                )
            }

            // Verify session is active before sending request
            if (activeSessionProvider != null && activeSessionProvider.invoke() != sessionGeneration) {
                return@withContext LocateResult.NetworkError(
                    queryName = trimmedQuery,
                    statusCode = null,
                    message = "Session generation $sessionGeneration superseded before request dispatch"
                )
            }

            // Only one active request permitted at a time
            cancelPending()

            val baseHttpUrl = baseUrl.trimEnd('/').toHttpUrlOrNull()
                ?: return@withContext LocateResult.NetworkError(
                    queryName = trimmedQuery,
                    statusCode = null,
                    message = "Invalid base URL: $baseUrl"
                )

            val endpointUrl = baseHttpUrl.newBuilder()
                .addPathSegment("api")
                .addPathSegment("v1")
                .addPathSegment("objects")
                .addPathSegment("locate")
                .addQueryParameter("name", trimmedQuery)
                .addQueryParameter("query", trimmedQuery)
                .build()

            val requestBuilder = Request.Builder()
                .url(endpointUrl)
                .header("Accept", "application/json")

            if (!bearerToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $bearerToken")
            }

            val request = requestBuilder.build()
            val call = client.newCall(request)

            synchronized(this@MemoryClient) {
                inFlightCall = call
            }

            val startMonotonicMs = clock.nowMonotonicMs()

            try {
                val response = call.execute()
                val requestDurationMs = clock.nowMonotonicMs() - startMonotonicMs

                // Verify session generation validity immediately upon return
                if (activeSessionProvider != null && activeSessionProvider.invoke() != sessionGeneration) {
                    response.close()
                    return@withContext LocateResult.NetworkError(
                        queryName = trimmedQuery,
                        statusCode = null,
                        message = "Session generation $sessionGeneration superseded during request execution"
                    )
                }

                // PRD §13.3: Read up to 64 KiB maximum
                val bodySource = response.body?.source()
                if (bodySource == null) {
                    return@withContext LocateResult.NetworkError(
                        queryName = trimmedQuery,
                        statusCode = response.code,
                        message = "Empty response body"
                    )
                }

                val buffer = Buffer()
                var totalBytesRead = 0L
                val maxBytes = MemoryClientContract.MAX_RESPONSE_BYTES
                var exceeded = false

                while (true) {
                    val read = bodySource.read(buffer, 8192L)
                    if (read == -1L) break
                    totalBytesRead += read
                    if (totalBytesRead > maxBytes) {
                        exceeded = true
                        break
                    }
                }

                if (exceeded) {
                    response.close()
                    return@withContext LocateResult.NetworkError(
                        queryName = trimmedQuery,
                        statusCode = response.code,
                        message = "Response body exceeded maximum 64 KiB limit"
                    )
                }

                val responseBodyString = buffer.readUtf8()

                // Non-200 responses are service failures, never not_found
                if (!response.isSuccessful) {
                    val errorMessage = try {
                        val errJson = JSONObject(responseBodyString)
                        errJson.optString("message", "HTTP ${response.code}")
                    } catch (_: Exception) {
                        "HTTP ${response.code}: ${response.message}"
                    }
                    return@withContext LocateResult.NetworkError(
                        queryName = trimmedQuery,
                        statusCode = response.code,
                        message = errorMessage
                    )
                }

                parseLocateResponse(trimmedQuery, responseBodyString, requestDurationMs)
            } catch (e: IOException) {
                LocateResult.NetworkError(
                    queryName = trimmedQuery,
                    statusCode = null,
                    message = e.message ?: "Network I/O failure"
                )
            } catch (e: Exception) {
                LocateResult.NetworkError(
                    queryName = trimmedQuery,
                    statusCode = null,
                    message = e.message ?: "Unexpected error during locate request"
                )
            } finally {
                synchronized(this@MemoryClient) {
                    if (inFlightCall === call) {
                        inFlightCall = null
                    }
                }
            }
        }

    override suspend fun checkHealth(): Boolean = withContext(Dispatchers.IO) {
        cancelPending()

        val baseHttpUrl = baseUrl.trimEnd('/').toHttpUrlOrNull() ?: return@withContext false
        val healthUrl = baseHttpUrl.newBuilder()
            .addPathSegment("api")
            .addPathSegment("v1")
            .addPathSegment("health")
            .build()

        val requestBuilder = Request.Builder()
            .url(healthUrl)
            .header("Accept", "application/json")

        if (!bearerToken.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $bearerToken")
        }

        val request = requestBuilder.build()
        val call = client.newCall(request)

        synchronized(this@MemoryClient) {
            inFlightCall = call
        }

        try {
            val response = call.execute()
            if (!response.isSuccessful) {
                response.close()
                return@withContext false
            }

            val body = response.body?.string() ?: return@withContext false
            val json = JSONObject(body)
            val serviceStatus = json.optString("service_status", json.optString("status", ""))
            serviceStatus.equals("ok", ignoreCase = true)
        } catch (_: Exception) {
            false
        } finally {
            synchronized(this@MemoryClient) {
                if (inFlightCall === call) {
                    inFlightCall = null
                }
            }
        }
    }

    override fun cancelPending() {
        synchronized(this) {
            inFlightCall?.cancel()
            inFlightCall = null
        }
    }

    private fun parseLocateResponse(
        queryName: String,
        jsonString: String,
        requestDurationMs: Long
    ): LocateResult {
        return try {
            val root = JSONObject(jsonString)
            val statusStr = root.optString("status", "").lowercase()
            val rawCanonicalName = root.optString("canonical_name", root.optString("target_class", ""))
            val canonicalName = if (rawCanonicalName.isNotEmpty()) rawCanonicalName else queryName

            val scanId = root.optString("scan_id", "")
            val observationTimeIso = root.optString("observation_time_iso", "")
            val cameraProfileId = root.optString("camera_profile_id", "")

            // Calculate conservative age adding measured request duration
            val baseAge = root.optDouble("age_seconds", 0.0)
            val conservativeAgeSeconds = baseAge + (requestDurationMs / 1000.0)

            val candidatesList = mutableListOf<MemoryCandidate>()
            val candidatesArray = root.optJSONArray("candidates")
            if (candidatesArray != null) {
                for (i in 0 until candidatesArray.length()) {
                    val cObj = candidatesArray.getJSONObject(i)
                    val instId = cObj.optString("instance_id", cObj.optString("observation_id", "inst_$i"))
                    val zId = cObj.optString("zone_id", cObj.optString("zone", "unknown"))
                    val zName = if (cObj.has("zone_name")) cObj.optString("zone_name") else null
                    val conf = cObj.optDouble("confidence", 0.0).toFloat()

                    val boxArray = cObj.optJSONArray("box")
                    val rect = if (boxArray != null && boxArray.length() == 4) {
                        NormalizedRect(
                            left = boxArray.optDouble(0).toFloat(),
                            top = boxArray.optDouble(1).toFloat(),
                            right = boxArray.optDouble(2).toFloat(),
                            bottom = boxArray.optDouble(3).toFloat()
                        ).clamped()
                    } else {
                        val rx = cObj.optDouble("relative_x", 0.5).toFloat()
                        val ry = cObj.optDouble("relative_y", 0.5).toFloat()
                        NormalizedRect(rx - 0.05f, ry - 0.05f, rx + 0.05f, ry + 0.05f).clamped()
                    }

                    candidatesList.add(
                        MemoryCandidate(
                            instanceId = instId,
                            zoneId = zId,
                            zoneName = zName,
                            box = rect,
                            confidence = conf
                        )
                    )
                }
            }

            when (statusStr) {
                "found" -> {
                    if (candidatesList.isEmpty()) {
                        LocateResult.NetworkError(
                            queryName = queryName,
                            statusCode = 200,
                            message = "Response declared 'found' but returned 0 candidates"
                        )
                    } else if (conservativeAgeSeconds > MemoryClientContract.STALE_THRESHOLD_SECONDS) {
                        LocateResult.Stale(
                            queryName = queryName,
                            canonicalName = canonicalName,
                            candidate = candidatesList.first(),
                            ageSeconds = conservativeAgeSeconds,
                            observationTimeIso = observationTimeIso,
                            scanId = scanId,
                            cameraProfileId = cameraProfileId
                        )
                    } else {
                        LocateResult.Found(
                            queryName = queryName,
                            canonicalName = canonicalName,
                            candidate = candidatesList.first(),
                            ageSeconds = conservativeAgeSeconds,
                            observationTimeIso = observationTimeIso,
                            scanId = scanId,
                            cameraProfileId = cameraProfileId
                        )
                    }
                }

                "ambiguous" -> {
                    LocateResult.Ambiguous(
                        queryName = queryName,
                        canonicalName = canonicalName,
                        candidates = candidatesList,
                        ageSeconds = conservativeAgeSeconds,
                        scanId = scanId,
                        cameraProfileId = cameraProfileId
                    )
                }

                "stale" -> {
                    val candidate = candidatesList.firstOrNull() ?: MemoryCandidate(
                        instanceId = "stale_0",
                        zoneId = "unknown",
                        zoneName = null,
                        box = NormalizedRect(0.4f, 0.4f, 0.6f, 0.6f),
                        confidence = 0f
                    )
                    LocateResult.Stale(
                        queryName = queryName,
                        canonicalName = canonicalName,
                        candidate = candidate,
                        ageSeconds = conservativeAgeSeconds,
                        observationTimeIso = observationTimeIso,
                        scanId = scanId,
                        cameraProfileId = cameraProfileId
                    )
                }

                "historical_only" -> {
                    LocateResult.HistoricalOnly(
                        queryName = queryName,
                        canonicalName = canonicalName,
                        candidates = candidatesList,
                        ageSeconds = conservativeAgeSeconds,
                        cameraProfileId = cameraProfileId
                    )
                }

                "not_found" -> {
                    LocateResult.NotFound(
                        queryName = queryName,
                        canonicalName = if (rawCanonicalName.isNotEmpty()) rawCanonicalName else null
                    )
                }

                "unsupported" -> {
                    LocateResult.Unsupported(
                        queryName = queryName
                    )
                }

                else -> {
                    LocateResult.NetworkError(
                        queryName = queryName,
                        statusCode = 200,
                        message = "Unrecognized locate status: $statusStr"
                    )
                }
            }
        } catch (e: Exception) {
            LocateResult.NetworkError(
                queryName = queryName,
                statusCode = 200,
                message = "Malformed JSON locate response: ${e.message}"
            )
        }
    }
}
