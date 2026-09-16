package dev.navisense.voice

import android.content.Context
import android.content.Intent
import android.os.Build
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
 * Uses on-device speech recognition where supported or system default speech service with zero external API keys.
 *
 * Key safety features:
 * 1. Automatically mutes recognition or drops results when TTS is actively speaking to prevent echo loops.
 * 2. Self-healing continuous loop: silently restarts on silence timeout or no-match error.
 * 3. State change strictly tied to [onReadyForSpeech] to prevent rapid UI flickering between Standby and Listening.
 * 4. Partial results inspection: triggers emergency STOP immediately without waiting for speech pause.
 * 5. Dispatches parsed [VoiceCommand] to the listener.
 */
class VoiceCommandManager(
    private val context: Context,
    private val isTtsSpeakingProvider: () -> Boolean,
    private val onCommandRecognized: (VoiceCommand) -> Unit,
    private val onStateChanged: ((isListening: Boolean) -> Unit)? = null
) : RecognitionListener {

    companion object {
        private const val TAG = "VoiceCommandManager"
        private const val RESTART_DELAY_MS = 600L
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
            mainHandler.removeCallbacksAndMessages(null)
            safeStopRecognizer()
        }
    }

    fun destroy() {
        mainHandler.post {
            isContinuousListening = false
            mainHandler.removeCallbacksAndMessages(null)
            safeDestroyRecognizer()
            setListeningState(false)
        }
    }

    private fun setListeningState(listening: Boolean) {
        if (isListening != listening) {
            isListening = listening
            onStateChanged?.invoke(listening)
        }
    }

    private fun initAndStartRecognizer() {
        if (!isContinuousListening) return
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer is not available on this device")
            setListeningState(false)
            return
        }

        try {
            if (speechRecognizer == null) {
                speechRecognizer = createSpeechRecognizerInstance().apply {
                    setRecognitionListener(this@VoiceCommandManager)
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }

            speechRecognizer?.startListening(intent)
            Log.d(TAG, "SpeechRecognizer startListening called")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting SpeechRecognizer: ${e.message}", e)
            setListeningState(false)
            safeDestroyRecognizer()
            scheduleRestart(1500L)
        }
    }

    private fun createSpeechRecognizerInstance(): SpeechRecognizer {
        // Try on-device recognizer first on Android 13+ (API 33+) if available for lowest latency & offline operation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                if (SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                    Log.i(TAG, "Creating OnDeviceSpeechRecognizer")
                    return SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "On-device speech recognizer unavailable: ${e.message}")
            }
        }

        // Standard system recognizer (uses default RecognitionService e.g. Google Speech / TTS)
        Log.i(TAG, "Creating standard system SpeechRecognizer")
        return SpeechRecognizer.createSpeechRecognizer(context)
    }

    private fun safeStopRecognizer() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping SpeechRecognizer: ${e.message}")
        }
        setListeningState(false)
    }

    private fun safeDestroyRecognizer() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
        }
        speechRecognizer = null
    }

    private fun scheduleRestart(delayMs: Long = RESTART_DELAY_MS) {
        if (!isContinuousListening) return
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (!isContinuousListening) return@postDelayed
            // If TTS is currently speaking, back off to prevent acoustic feedback
            if (isTtsSpeakingProvider()) {
                scheduleRestart(500L)
            } else {
                initAndStartRecognizer()
            }
        }, delayMs)
    }

    // --- RecognitionListener Callbacks ---

    override fun onReadyForSpeech(params: Bundle?) {
        Log.d(TAG, "onReadyForSpeech: microphone is open and ready")
        setListeningState(true)
    }

    override fun onBeginningOfSpeech() {
        Log.d(TAG, "onBeginningOfSpeech")
    }

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        Log.d(TAG, "onEndOfSpeech")
    }

    override fun onError(error: Int) {
        Log.d(TAG, "SpeechRecognizer error: $error")
        setListeningState(false)

        // Always recreate recognizer on structural errors (client, busy, bind failure, server)
        when (error) {
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER,
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            10, 11, 12, 13 -> {
                safeDestroyRecognizer()
            }
        }

        if (isContinuousListening) {
            val delay = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 600L
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 1200L
                10 -> 2500L // Bind failed, back off gracefully
                else -> 1200L
            }
            scheduleRestart(delay)
        }
    }

    override fun onResults(results: Bundle?) {
        setListeningState(false)

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
