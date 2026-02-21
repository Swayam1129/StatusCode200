package com.example.accessu.core

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.*

object AudioGuide : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private val queue = mutableListOf<String>()

    private var onDoneCallback: (() -> Unit)? = null

    fun init(context: Context) {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isReady = true

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}

                override fun onDone(utteranceId: String?) {
                    // NEW: Force the callback back onto the Main UI Thread
                    Handler(Looper.getMainLooper()).post {
                        onDoneCallback?.invoke()
                        onDoneCallback = null
                    }
                }

                override fun onError(utteranceId: String?) {}
            })

            queue.forEach { speak(it) }
            queue.clear()
        }
    }

    fun speak(message: String, onDone: (() -> Unit)? = null) {
        if (isReady) {
            onDoneCallback = onDone
            val utteranceId = UUID.randomUUID().toString()
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        } else {
            queue.add(message)
        }
    }

    fun beep(context: Context) {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
            toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            Handler(Looper.getMainLooper()).postDelayed({ toneGen.release() }, 250)
        } catch (_: Exception) {}
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}