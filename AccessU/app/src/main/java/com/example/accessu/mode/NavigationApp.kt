package com.example.accessu.mode

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log // NEW: Added Log import
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.accessu.core.AudioGuide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class NavState {
    IDLE,
    ASK_DESTINATION,
    ASK_START,
    CONFIRM_START,
    NAVIGATING
}

@Composable
fun NavigationApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val haptic = LocalHapticFeedback.current

    var navState by remember { mutableStateOf(NavState.IDLE) }
    var destination by remember { mutableStateOf("") }
    var startLocation by remember { mutableStateOf("") }
    var liveTranscript by remember { mutableStateOf("") }

    // Check permissions for BOTH Camera and Audio
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasAudioPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] ?: hasCameraPermission
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] ?: hasAudioPermission
    }

    // Setup Speech Recognizer
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
    }

    // Helper function to handle listening
    fun startListening(onResult: (String) -> Unit) {
        liveTranscript = "..." // Reset transcript when listening starts

        speechRecognizer.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}

            // FIXED: Combined and placed the onError function correctly inside the listener
            override fun onError(error: Int) {
                // Print the exact error code to Logcat
                Log.e("AudioGuideApp", "SpeechRecognizer failed with error code: $error")

                coroutineScope.launch(Dispatchers.Main) {
                    liveTranscript = ""
                    AudioGuide.speak("I didn't quite catch that. Please tap anywhere to try again.")
                    navState = NavState.IDLE
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    coroutineScope.launch(Dispatchers.Main) {
                        liveTranscript = matches[0]
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    coroutineScope.launch(Dispatchers.Main) {
                        liveTranscript = matches[0] // Final Polish
                        onResult(matches[0])
                    }
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        coroutineScope.launch(Dispatchers.Main) {
            speechRecognizer.startListening(speechIntent)
        }
    }

    // The sequential conversation flow
    fun startConversation() {
        if (!hasAudioPermission || !hasCameraPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA))
            return
        }

        navState = NavState.ASK_DESTINATION
        liveTranscript = "" // Clear before starting

        AudioGuide.speak("Where do you want to go?") {
            startListening { destResult ->
                destination = destResult
                navState = NavState.ASK_START
                liveTranscript = "" // Clear for the next prompt

                AudioGuide.speak("Where are you right now?") {
                    startListening { startResult ->
                        startLocation = startResult
                        navState = NavState.CONFIRM_START
                        liveTranscript = "" // Clear for the next prompt

                        AudioGuide.speak("Okay, routing from $startLocation to $destination. Shall we start the journey?") {
                            startListening { confirmResult ->
                                if (confirmResult.lowercase().contains("yes")) {
                                    navState = NavState.NAVIGATING
                                    AudioGuide.speak("Starting navigation.")
                                } else {
                                    navState = NavState.IDLE
                                    liveTranscript = ""
                                    AudioGuide.speak("Navigation cancelled. Tap anywhere to start over.")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // UI
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (navState == NavState.IDLE) Color(0xFF121212) else Color.DarkGray)
            .clickable {
                if (navState == NavState.IDLE) {
                    startConversation()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (navState == NavState.NAVIGATING && hasCameraPermission) {
            CameraPreview()
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = when (navState) {
                        NavState.IDLE -> "Tap anywhere to start"
                        NavState.ASK_DESTINATION -> "Listening for destination..."
                        NavState.ASK_START -> "Listening for current location..."
                        NavState.CONFIRM_START -> "Waiting for confirmation..."
                        else -> ""
                    },
                    color = Color.White,
                    fontSize = 28.sp,
                    textAlign = TextAlign.Center
                )

                // Show the live transcript if it's not empty
                if (liveTranscript.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "\"$liveTranscript\"",
                        color = Color(0xFFFFD700), // High contrast Gold/Yellow
                        fontSize = 36.sp, // Very large for partial visibility
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}