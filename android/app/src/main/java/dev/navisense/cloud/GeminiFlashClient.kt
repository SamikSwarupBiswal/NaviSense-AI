package dev.navisense.cloud

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Client for Google Gemini 1.5 Flash multimodal vision inference.
 */
class GeminiFlashClient(
    private var apiKey: String? = null,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    fun setApiKey(key: String) {
        this.apiKey = key.trim()
    }

    fun hasApiKey(): Boolean = !apiKey.isNullOrBlank()

    /**
     * Sends camera JPEG frame to Gemini 1.5 Flash and returns concise scene narration.
     */
    fun analyzeSceneForWalking(jpegBytes: ByteArray): String? {
        val key = apiKey
        if (key.isNullOrBlank()) {
            Log.w(TAG, "Gemini API key is not configured.")
            return null
        }

        try {
            val base64Image = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)

            val promptText = "You are NaviSense assistive vision AI helping a blind person walk safely. " +
                    "In 1 or 2 concise, direct sentences, identify any obstacles, furniture (tables, chairs), " +
                    "people, or everyday objects (keys, wallet, doors, stairs) in the image. " +
                    "State their relative direction/position (e.g., 'directly ahead', 'on your left at 10 o'clock', " +
                    "'on your right') and approximate distance. If the path ahead is unobstructed, say 'Path is clear'."

            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray()
                val contentObj = JSONObject()
                val partsArray = JSONArray()

                // Text prompt part
                partsArray.put(JSONObject().apply {
                    put("text", promptText)
                })

                // Image inline data part
                partsArray.put(JSONObject().apply {
                    val inlineData = JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", base64Image)
                    }
                    put("inline_data", inlineData)
                })

                contentObj.put("parts", partsArray)
                contentsArray.put(contentObj)
                put("contents", contentsArray)

                val genConfig = JSONObject().apply {
                    put("temperature", 0.2)
                    put("maxOutputTokens", 90)
                }
                put("generationConfig", genConfig)
            }

            val requestBody = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val url = "$GEMINI_API_BASE?key=$key"

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()
                    Log.e(TAG, "Gemini API call failed with code ${response.code}: $errBody")
                    return null
                }

                val responseBodyStr = response.body?.string() ?: return null
                val root = JSONObject(responseBodyStr)
                val candidates = root.optJSONArray("candidates") ?: return null
                if (candidates.length() == 0) return null

                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content") ?: return null
                val parts = content.optJSONArray("parts") ?: return null
                if (parts.length() == 0) return null

                val text = parts.getJSONObject(0).optString("text", "").trim()
                return cleanNarrationText(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini 1.5 Flash analysis", e)
            return null
        }
    }

    private fun cleanNarrationText(raw: String): String {
        return raw.replace("\n", " ")
            .replace("*", "")
            .trim()
    }

    companion object {
        private const val TAG = "GeminiFlashClient"
        private const val GEMINI_API_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
    }
}
