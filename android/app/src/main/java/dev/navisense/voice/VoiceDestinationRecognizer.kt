package dev.navisense.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

/**
 * Speech recognition wrapper parsing natural spoken navigation intents into destinations.
 * Examples:
 * - "Take me to Central Library" -> "Central Library"
 * - "I want to go to Starbucks" -> "Starbucks"
 * - "Navigate to Main Gate" -> "Main Gate"
 */
class VoiceDestinationRecognizer(
    private val context: Context,
    private val onDestinationParsed: (String) -> Unit,
    private val onError: (String) -> Unit,
    private val onStopRequested: (() -> Unit)? = null
) {

    companion object {
        private const val TAG = "VoiceDestRecognizer"
        private val INTENT_PREFIXES = listOf(
            "take me to",
            "i want to go to",
            "navigate to",
            "directions to",
            "go to",
            "walk to",
            "find route to",
            "route to"
        )

        /**
         * Extracts destination name by stripping common filler commands.
         */
        fun parseDestinationPhrase(rawText: String): String {
            var cleaned = rawText.lowercase().trim()
            for (prefix in INTENT_PREFIXES) {
                if (cleaned.startsWith(prefix)) {
                    cleaned = cleaned.removePrefix(prefix).trim()
                    break
                }
            }
            // Capitalize words for clean presentation
            return cleaned.split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    init {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            val googleComponent = android.content.ComponentName(
                "com.google.android.googlequicksearchbox",
                "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
            )
            val recognizer = try {
                SpeechRecognizer.createSpeechRecognizer(context, googleComponent)
            } catch (e: Exception) {
                SpeechRecognizer.createSpeechRecognizer(context)
            }
            speechRecognizer = recognizer.apply {
                setRecognitionListener(createListener())
            }
        }
    }

    fun startListening() {
        if (speechRecognizer == null) {
            onError("Speech recognition is not available on this device")
            return
        }

        if (isListening) {
            stopListening()
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Where would you like to walk to?")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        try {
            speechRecognizer?.startListening(intent)
            isListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech recognition", e)
            onError("Failed to start voice recognition: ${e.message}")
        }
    }

    fun stopListening() {
        if (!isListening) return
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping speech recognition", e)
        }
        isListening = false
    }

    fun destroy() {
        stopListening()
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error destroying speech recognizer", e)
        }
        speechRecognizer = null
    }



    private fun createListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d(TAG, "Beginning of speech")
        }

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListening = false
        }

        override fun onError(error: Int) {
            isListening = false
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH -> "No destination heard. Please try again."
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out. Please try again."
                SpeechRecognizer.ERROR_NETWORK -> "Network error during speech recognition."
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                else -> "Speech recognition error (Code $error)."
            }
            onError(errorMsg)
        }

        override fun onResults(results: Bundle?) {
            isListening = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val rawUtterance = matches[0]
                val cleaned = rawUtterance.lowercase(Locale.ROOT).trim()
                val words = cleaned.split(" ")
                if (!cleaned.contains("bus stop") && (words.contains("stop") || words.contains("cancel") || words.contains("halt") || words.contains("quit"))) {
                    Log.i(TAG, "Stop command recognized during destination query: $rawUtterance")
                    onStopRequested?.invoke()
                    return
                }
                val destination = parseDestinationPhrase(rawUtterance)
                if (destination.isNotBlank()) {
                    onDestinationParsed(destination)
                } else {
                    onError("Could not understand destination: $rawUtterance")
                }
            } else {
                onError("No voice match found")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                for (match in matches) {
                    val cleaned = match.lowercase(Locale.ROOT).trim()
                    val words = cleaned.split(" ")
                    if (!cleaned.contains("bus stop") && (words.contains("stop") || words.contains("cancel") || words.contains("halt"))) {
                        Log.w(TAG, "Immediate Stop detected during partial destination listening: $match")
                        stopListening()
                        onStopRequested?.invoke()
                        break
                    }
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }
}
