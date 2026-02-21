package com.example.accessu.voice

import android.content.Context
import android.util.Log
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Voice input helper for speech-to-text.
 * Used when the app asks "What is your current location?" and "Where do you want to go?"
 */
class VoiceInput(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var onResult: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    fun startListening(
        onResult: (String) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "Speech recognition not available on this device")
            onError?.invoke("Speech recognition not available")
            return
        }
        this.onResult = onResult
        this.onError = onError
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    Log.d(TAG, "SpeechRecognizer onError: code=$error")
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Couldn't hear"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        else -> "Couldn't hear"
                    }
                    val callback = this@VoiceInput.onError
                    ContextCompat.getMainExecutor(context).execute {
                        try { callback?.invoke(msg) } catch (e: Exception) {
                            Log.e(TAG, "onError callback failed: ${e.message}", e)
                        }
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()?.trim()
                    Log.d(TAG, "SpeechRecognizer onResults: text='$text'")
                    ContextCompat.getMainExecutor(context).execute {
                        try {
                            if (!text.isNullOrBlank()) {
                                onResult(text)
                            } else {
                                this@VoiceInput.onError?.invoke("No result")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "onResults callback failed: ${e.message}", e)
                        }
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
        }
        Log.d(TAG, "Starting speech recognition")
        speechRecognizer?.startListening(intent)
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    companion object {
        private const val TAG = "VoiceInput"
    }
}
