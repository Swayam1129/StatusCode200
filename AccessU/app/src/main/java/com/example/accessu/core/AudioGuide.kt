package com.example.accessu.core

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.*

object AudioGuide : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val queue = mutableListOf<String>() // Queue messages until TTS is ready

    // Initialize TTS
    fun init(context: Context) {
        tts = TextToSpeech(context, this)
    }

    // Called when TTS engine is ready
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isReady = true

            // Play any queued messages
            queue.forEach { speak(it) }
            queue.clear()
        }
    }

    // Speak text (dynamic)
    fun speak(message: String) {
        if (isReady) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "AudioGuideID")
        } else {
            // TTS not ready yet, save it for later
            queue.add(message)
        }
    }

    // Shutdown TTS safely
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}