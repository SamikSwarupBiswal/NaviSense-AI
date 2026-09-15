package dev.navisense.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Manages continuous hands-free voice command recognition via Android SpeechRecognizer.
 * Runs on-device (offline speech recognition where supported) with zero external API keys.
 *
 * Key safety features:
 * 1. Automatically mutes recognition or drops results when TTS is actively speaking to prevent echo loops.
 * 2. Self-healing continuous loop: silently restarts on silence timeout or no-match error.
 * 3. Partial results inspection: triggers emergency STOP immediately without waiting for speech pause.
 * 4. Dispatches parsed [VoiceCommand] to the listener.
 */
class VoiceCommandManager(
    private val context: Context,
    private val isTtsSpeakingProvider: () -> Boolean,
    private val onCommandRecognized: (VoiceCommand) -> Unit,
    private val onStateChanged: ((isListening: Boolean) -> Unit)? = null
) : RecognitionListener {

    companion object {
        private const val TAG = "VoiceCommandManager"
        private const val RESTART_DELAY_MS = 400L
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isContinuousListening: Boolean = false
    private var isListening: Boolean = false

    fun startListening() {
        mainHandler.post {
            isContinuousListening = true
            initAndStartRecognizer()
        }
    }

    fun stopListening() {
        mainHandler.post {
            isContinuousListening = false
            safeStopRecognizer()
        }
    }

    fun destroy() {
        mainHandler.post {
            isContinuousListening = false
            mainHandler.removeCallbacksAndMessages(null)
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
            }
            speechRecognizer = null
            isListening = false
            onStateChanged?.invoke(false)
        }
    }

    private fun initAndStartRecognizer() {
        if (!isContinuousListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer is not available on this device")
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(this@VoiceCommandManager)
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra("android.speech.extra.PREFER_OFFLINE", true)
            }

            speechRecognizer?.startListening(intent)
            isListening = true
            onStateChanged?.invoke(true)
            Log.d(TAG, "SpeechRecognizer listening started")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer: ${e.message}", e)
            scheduleRestart(1000L)
        }
    }

    private fun safeStopRecognizer() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping SpeechRecognizer: ${e.message}")
        }
        isListening = false
        onStateChanged?.invoke(false)
    }

    private fun scheduleRestart(delayMs: Long = RESTART_DELAY_MS) {
        if (!isContinuousListening) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            // If TTS is currently speaking, wait a bit longer to prevent acoustic self-triggering
            if (isTtsSpeakingProvider()) {
                scheduleRestart(500L)
            } else {
                initAndStartRecognizer()
            }
        }, delayMs)
    }

    // --- RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "onReadyForSpeech")
        isListening = true
        onStateChanged?.invoke(true)
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech")
    }

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech")
        isListening = false
        onStateChanged?.invoke(false)
    }

    override fun onError(error: Int) {
        Log.d(TAG, "SpeechRecognizer error: $error")
        isListening = false
        onStateChanged?.invoke(false)
        if (isContinuousListening) {
            scheduleRestart(RESTART_DELAY_MS)
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        onStateChanged?.invoke(false)

        // Drop speech if TTS was actively speaking during detection
        if (isTtsSpeakingProvider()) {
            Log.d(TAG, "Ignoring recognition results: TTS is currently speaking")
            scheduleRestart(RESTART_DELAY_MS)
            return
        }

        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            Log.i(TAG, "Recognized speech candidates: $matches")
            var matchedCommand: VoiceCommand? = null
            for (match in matches) {
                val cmd = VoiceCommandParser.parse(match)
                if (cmd !is VoiceCommand.Unknown) {
                    matchedCommand = cmd
                    break
                }
            }

            val finalCmd = matchedCommand ?: VoiceCommandParser.parse(matches[0])
            Log.i(TAG, "Dispatched VoiceCommand: $finalCmd")
            onCommandRecognized(finalCmd)
        }

        if (isContinuousListening) {
            scheduleRestart(RESTART_DELAY_MS)
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            for (match in matches) {
                val cmd = VoiceCommandParser.parse(match)
                // Immediate preemption: if user says STOP during partial speech, execute immediately!
                if (cmd is VoiceCommand.Stop) {
                    Log.w(TAG, "Immediate STOP detected in partial speech: $match")
                    onCommandRecognized(VoiceCommand.Stop)
                    break
                }
            }
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}
}
