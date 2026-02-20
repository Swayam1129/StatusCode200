package com.example.accessu.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * TTS helper used by the entire app for all spoken output.
 * Niharika owns this; Sanika and Swayam call AudioGuide.speak(text) for any message.
 */
object AudioGuide {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    /**
     * Initialize AudioGuide. Call from MainActivity.onCreate or first use.
     * Must be called on main thread with a Context (Activity or Application).
     */
    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isInitialized = true
            }
        }
    }

    /**
     * Speak text via TTS. Non-blocking; queues the utterance.
     * Use this for all user-facing spoken output (obstacles, nav, bus, weather).
     */
    fun speak(text: String, queueMode: Int = TextToSpeech.QUEUE_ADD) {
        if (text.isBlank()) return
        tts?.let { engine ->
            if (isInitialized) {
                engine.speak(text, queueMode, null, "accessu_utterance")
            }
        }
    }

    /**
     * Stop any current speech immediately.
     */
    fun stop() {
        tts?.stop()
    }

    /**
     * Release TTS when app is done. Call from Activity.onDestroy if needed.
     */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    fun isReady(): Boolean = isInitialized && tts != null
}
