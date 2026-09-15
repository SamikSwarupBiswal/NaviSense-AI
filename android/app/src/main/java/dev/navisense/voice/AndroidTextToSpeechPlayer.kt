package dev.navisense.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android implementation of ITextToSpeechPlayer wrapping the platform TextToSpeech engine.
 */
class AndroidTextToSpeechPlayer(context: Context) : ITextToSpeechPlayer {

    private var tts: TextToSpeech? = null
    private val isReady = AtomicBoolean(false)
    private val speaking = AtomicBoolean(false)

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isReady.set(true)
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                speaking.set(true)
            }

            override fun onDone(utteranceId: String?) {
                speaking.set(false)
            }

            override fun onError(utteranceId: String?) {
                speaking.set(false)
            }
        })
    }

    override fun speak(text: String, utteranceId: String): Boolean {
        if (!isReady.get() || tts == null) return false
        val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        return result == TextToSpeech.SUCCESS
    }

    override fun stop(): Boolean {
        val result = tts?.stop()
        speaking.set(false)
        return result == TextToSpeech.SUCCESS
    }

    override fun isSpeaking(): Boolean {
        return speaking.get() || (tts?.isSpeaking == true)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady.set(false)
        speaking.set(false)
    }
}
