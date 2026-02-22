package com.example.accessu.voice

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * TTS helper used by the entire app for all spoken output.
 * Niharika owns this; Sanika and Swayam call AudioGuide.speak(text) for any message.
 */
object AudioGuide {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var speakCallback: (() -> Unit)? = null

    /**
     * Initialize AudioGuide. Call from MainActivity.onCreate or first use.
     * Must be called on main thread with a Context (Activity or Application).
     */
    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {}
                    override fun onDone(utteranceId: String?) {
                        speakCallback?.let { cb ->
                            speakCallback = null
                            cb()
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        speakCallback?.let { cb ->
                            speakCallback = null
                            cb()
                        }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        speakCallback?.let { cb ->
                            speakCallback = null
                            cb()
                        }
                    }
                })
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
     * Speak text, then run onDone when finished. Use for "say after the beep" flow.
     */
    fun speakWithCallback(text: String, onDone: () -> Unit) {
        if (text.isBlank() || tts == null || !isInitialized) {
            onDone()
            return
        }
        speakCallback = onDone
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "accessu_utterance")
    }

    /**
     * Play a short beep to signal "start speaking now".
     */
    fun beep(context: Context) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                toneGen.release()
            }, 250)
        } catch (_: Exception) { /* ignore if ToneGenerator fails */ }
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
        speakCallback = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    fun isReady(): Boolean = isInitialized && tts != null
}
