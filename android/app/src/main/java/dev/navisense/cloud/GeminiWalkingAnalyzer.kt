package dev.navisense.cloud

import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/**
 * Periodically captures camera frames every 4 seconds in Walking Mode and
 * queries Gemini 1.5 Flash for obstacle identity and spatial directionality.
 */
class GeminiWalkingAnalyzer(
    private val client: GeminiFlashClient,
    private val frameProvider: () -> Bitmap?,
    private val onNarrationReceived: (String) -> Unit
) {
    private var analysisJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        analysisJob = scope.launch {
            Log.i(TAG, "Starting Gemini 1.5 Flash 4-second walking analysis loop")
            while (isActive && isRunning) {
                try {
                    val bitmap = frameProvider()
                    if (bitmap != null) {
                        val jpegBytes = compressBitmapToJpeg(bitmap)
                        val narration = client.analyzeSceneForWalking(jpegBytes)
                        if (!narration.isNullOrBlank() && isRunning) {
                            Log.i(TAG, "Gemini narration: $narration")
                            onNarrationReceived(narration)
                        }
                    }
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.w(TAG, "Error during periodic Gemini walking frame analysis", e)
                }

                // 4-second delay between analyses as specified
                delay(INTERVAL_MS)
            }
        }
    }

    fun stop() {
        isRunning = false
        analysisJob?.cancel()
        analysisJob = null
        Log.i(TAG, "Stopped Gemini walking analysis loop")
    }

    private fun compressBitmapToJpeg(source: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        // Scale to 640 max dimension if needed for bandwidth and low latency
        val scaled = if (source.width > 640 || source.height > 640) {
            val scale = 640f / maxOf(source.width, source.height)
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt(),
                (source.height * scale).toInt(),
                true
            )
        } else source

        scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
        return stream.toByteArray()
    }

    companion object {
        private const val TAG = "GeminiWalkingAnalyzer"
        const val INTERVAL_MS = 4000L // 4 seconds delay per image
    }
}
