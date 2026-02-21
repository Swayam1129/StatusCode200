package com.example.accessu.navigation

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import com.example.accessu.ui.theme.AccessUDestPin
import com.example.accessu.ui.theme.UofAGreen
import com.example.accessu.ui.theme.UofAGreenDark
import com.example.accessu.ui.theme.UofAGreenLight
import com.example.accessu.ui.theme.UofAGreenLightBg
import com.example.accessu.ui.theme.UofAGold
import com.example.accessu.ui.theme.UofAGoldLight
import com.example.accessu.ui.theme.UofAWhite
import com.example.accessu.ui.theme.UofACharcoal
import com.example.accessu.ui.theme.UofASlate
import com.example.accessu.ui.theme.UofAWarmGray
import com.example.accessu.R
import com.example.accessu.ui.theme.NunitoFont
import com.example.accessu.ui.theme.UofACream
import com.example.accessu.core.AudioGuide
import com.example.accessu.mode.CameraPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private sealed class ApplyResult { object Success : ApplyResult(); object NotFound : ApplyResult(); object SameAsCurrent : ApplyResult() }

private enum class NavStep {
    WELCOME,
    ASK_LOCATION,
    SHOW_LOCATION,
    ASK_DESTINATION,
    VERIFY,
    CONFIRMED,
    NAVIGATING
}

@Composable
fun NavFlowScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(NavStep.WELCOME) }
    var currentLocation by remember { mutableStateOf<LocationInfo?>(null) }
    var currentLocationTranscribed by remember { mutableStateOf<String?>(null) }
    var destination by remember { mutableStateOf<LocationInfo?>(null) }
    var destinationTranscribed by remember { mutableStateOf<String?>(null) }
    var locationListenFailed by remember { mutableStateOf(false) }
    var destinationListenFailed by remember { mutableStateOf(false) }
    var verifyListenFailed by remember { mutableStateOf(false) }
    var pendingSpeechFor by remember { mutableStateOf<String?>(null) }
    var liveListeningText by remember { mutableStateOf<String?>(null) }
    var pendingRetryFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var listenTimeoutHandled by remember { mutableStateOf(false) }
    var lastPartialMatch by remember { mutableStateOf<String?>(null) }

    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val hasMicPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            AudioGuide.speak("Camera permission is needed for navigation. Tap to retry.")
        }
    }

    fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    }

    fun reportListenFailed(forWhat: String) {
        when (forWhat) {
            "location" -> locationListenFailed = true
            "destination" -> destinationListenFailed = true
            "verify" -> verifyListenFailed = true
        }
        AudioGuide.speak("Couldn't hear you. Tap to try again.")
    }

    fun handleVerifyResult(yesNo: Boolean?) {
        liveListeningText = null
        pendingSpeechFor = null
        when (yesNo) {
            true -> {
                verifyListenFailed = false
                step = NavStep.CONFIRMED
                val from = currentLocation?.fullName ?: "your location"
                val to = destination?.fullName ?: "destination"
                AudioGuide.speak("Confirmed. From $from to $to.")
            }
            false -> {
                destination = null
                destinationTranscribed = null
                destinationListenFailed = false
                step = NavStep.ASK_DESTINATION
            }
            null -> {
                verifyListenFailed = true
                AudioGuide.speak("Couldn't hear you. Say yes or no.")
            }
        }
    }

    fun applyFinalResult(forWhat: String, raw: String): ApplyResult {
        val matched = CampusLocations.matchLocation(raw)
        if (matched == null) return ApplyResult.NotFound
        when (forWhat) {
            "location" -> {
                currentLocationTranscribed = raw
                currentLocation = matched
                step = NavStep.SHOW_LOCATION
                return ApplyResult.Success
            }
            "destination" -> {
                if (matched.abbreviation == currentLocation?.abbreviation) {
                    return ApplyResult.SameAsCurrent
                }
                destinationTranscribed = raw
                destination = matched
                step = NavStep.VERIFY
                return ApplyResult.Success
            }
        }
        return ApplyResult.Success
    }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    DisposableEffect(speechRecognizer) {
        onDispose { speechRecognizer?.destroy() }
    }

    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        }
    }

    fun launchSpeechRecognition(forWhat: String) {
        pendingSpeechFor = forWhat
        liveListeningText = "Listening..."
        lastPartialMatch = null
        if (speechRecognizer == null) {
            reportListenFailed(forWhat)
            return
        }
        speechRecognizer!!.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                Log.e("NavFlow", "SpeechRecognizer error: $error")
                coroutineScope.launch(Dispatchers.Main) {
                    if (listenTimeoutHandled) {
                        listenTimeoutHandled = false
                        liveListeningText = null
                        pendingSpeechFor = null
                        lastPartialMatch = null
                        return@launch
                    }
                    val partial = lastPartialMatch
                    lastPartialMatch = null
                    liveListeningText = null
                    pendingSpeechFor = null
                    if (error == SpeechRecognizer.ERROR_NO_MATCH && !partial.isNullOrBlank()) {
                        val matched = CampusLocations.matchLocation(partial)
                        if (matched != null && forWhat != "verify") {
                            applyFinalResult(forWhat, partial)
                            return@launch
                        }
                        val yesNo = if (forWhat == "verify") CampusLocations.parseYesNo(partial) else null
                        if (yesNo != null) {
                            handleVerifyResult(yesNo)
                            return@launch
                        }
                    }
                    reportListenFailed(forWhat)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = list?.firstOrNull()?.trim()
                if (!text.isNullOrBlank()) {
                    coroutineScope.launch(Dispatchers.Main) {
                        liveListeningText = text
                        lastPartialMatch = text
                    }
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val raw = matches[0]?.trim() ?: ""
                    coroutineScope.launch(Dispatchers.Main) {
                        liveListeningText = null
                        pendingSpeechFor = null
                        when (forWhat) {
                            "verify" -> handleVerifyResult(CampusLocations.firstYesNoFromAlternatives(matches))
                            else -> {
                                val matchedRaw = CampusLocations.firstMatchingAlternative(matches)
                                val rawToTry = matchedRaw ?: raw
                                when {
                                    !rawToTry.isNullOrBlank() -> {
                                        when (applyFinalResult(forWhat, rawToTry)) {
                                            is ApplyResult.Success -> {}
                                            is ApplyResult.NotFound -> pendingRetryFor = forWhat to "That location wasn't found. Please say again. Say after the beep."
                                            is ApplyResult.SameAsCurrent -> pendingRetryFor = forWhat to "Sorry, you are already at that location. Where do you want to go? Say after the beep."
                                        }
                                    }
                                    else -> reportListenFailed(forWhat)
                                }
                            }
                        }
                    }
                } else {
                    coroutineScope.launch(Dispatchers.Main) {
                        liveListeningText = null
                        pendingSpeechFor = null
                        reportListenFailed(forWhat)
                    }
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer!!.startListening(speechIntent)
    }

    fun askLocationAndListen() {
        locationListenFailed = false
        AudioGuide.speak("Where are you located? Say after the beep.") {
            ContextCompat.getMainExecutor(context).execute {
                AudioGuide.beep(context)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ launchSpeechRecognition("location") }, 400)
            }
        }
    }

    fun askDestinationAndListen() {
        destinationListenFailed = false
        AudioGuide.speak("Where do you want to go? Say after the beep.") {
            ContextCompat.getMainExecutor(context).execute {
                AudioGuide.beep(context)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ launchSpeechRecognition("destination") }, 400)
            }
        }
    }

    fun askVerifyAndListen() {
        verifyListenFailed = false
        val from = currentLocation?.fullName ?: "your location"
        val to = destination?.fullName ?: "destination"
        AudioGuide.speak("From $from to $to. Is that correct? Say yes or no after the beep.") {
            ContextCompat.getMainExecutor(context).execute {
                AudioGuide.beep(context)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ launchSpeechRecognition("verify") }, 400)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            AudioGuide.speak("Microphone permission is needed for voice input.")
            return@rememberLauncherForActivityResult
        }
        when (step) {
            NavStep.ASK_LOCATION -> askLocationAndListen()
            NavStep.ASK_DESTINATION -> askDestinationAndListen()
            NavStep.VERIFY -> askVerifyAndListen()
            else -> {}
        }
    }

    DisposableEffect(context) {
        AudioGuide.init(context)
        onDispose { }
    }

    LaunchedEffect(pendingRetryFor) {
        val (forWhat, message) = pendingRetryFor ?: return@LaunchedEffect
        pendingRetryFor = null
        AudioGuide.speak(message) {
            ContextCompat.getMainExecutor(context).execute {
                AudioGuide.beep(context)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ launchSpeechRecognition(forWhat) }, 400)
            }
        }
    }

    LaunchedEffect(pendingSpeechFor) {
        val forWhat = pendingSpeechFor ?: return@LaunchedEffect
        delay(8000)
        if (pendingSpeechFor == forWhat) {
            listenTimeoutHandled = true
            speechRecognizer?.stopListening()
            liveListeningText = null
            pendingSpeechFor = null
            reportListenFailed(forWhat)
        }
    }

    LaunchedEffect(step) {
        when (step) {
            NavStep.WELCOME -> {
                AudioGuide.speak("Welcome to Access U. Tap anywhere to begin.")
                if (!hasMicPermission) micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            NavStep.ASK_LOCATION -> {
                if (hasMicPermission) askLocationAndListen()
                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            NavStep.SHOW_LOCATION -> {
                AudioGuide.speak("Tap on the screen for next move.")
                delay(5000)
                step = NavStep.ASK_DESTINATION
            }
            NavStep.ASK_DESTINATION -> {
                if (hasMicPermission) askDestinationAndListen()
                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            NavStep.VERIFY -> {
                if (hasMicPermission) askVerifyAndListen()
                else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            NavStep.CONFIRMED -> {
                delay(2200)
                step = NavStep.NAVIGATING
            }
            NavStep.NAVIGATING -> {
                if (hasCameraPermission) {
                    AudioGuide.speak("Camera on. Navigating from ${currentLocation?.fullName ?: "here"} to ${destination?.fullName ?: "destination"}.")
                } else {
                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }
        }
    }

    val innerPageBg = UofACharcoal

    val floatingBoxModifier = Modifier
        .shadow(12.dp, RoundedCornerShape(24.dp), ambientColor = UofAGreenDark.copy(alpha = 0.4f), spotColor = UofAGreenDark.copy(alpha = 0.3f))
        .clip(RoundedCornerShape(24.dp))

    val pageBgOpacity by animateFloatAsState(
        targetValue = 0.5f,
        animationSpec = tween(durationMillis = 4200),
        label = "pageBg"
    )

    Column(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(UofAGold)
                    .padding(horizontal = 12.dp, vertical = 28.dp)
            ) {
                Text(
                    "AccessU",
                    style = MaterialTheme.typography.displayMedium,
                    color = UofAGreenDark,
                    fontWeight = FontWeight.Bold,
                    fontFamily = NunitoFont,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Start
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .background(UofAWhite)
            )
        }
        Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxSize()
            .background(innerPageBg)
            .clickable {
                when (step) {
                    NavStep.WELCOME -> step = NavStep.ASK_LOCATION
                    NavStep.SHOW_LOCATION -> step = NavStep.ASK_DESTINATION
                    NavStep.ASK_DESTINATION -> if (hasMicPermission) askDestinationAndListen() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    NavStep.VERIFY -> if (hasMicPermission) askVerifyAndListen() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    NavStep.ASK_LOCATION -> if (hasMicPermission) askLocationAndListen() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    else -> {}
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.campus_welcome_bg),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .alpha(pageBgOpacity),
            contentScale = ContentScale.Crop
        )
        if (step == NavStep.NAVIGATING && hasCameraPermission) {
            Box(modifier = Modifier.fillMaxSize()) {
                CameraPreview()
            }
        }
        if (step == NavStep.WELCOME) {
            var taglineVisible by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { delay(800); taglineVisible = true }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = 32.dp, start = 24.dp, end = 24.dp)
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = taglineVisible,
                    enter = fadeIn(animationSpec = tween(1500))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf("YOUR", "FAVOURITE", "NAVIGATION", "APP").forEach { word ->
                            Text(
                                word,
                                style = MaterialTheme.typography.displayLarge,
                                color = UofAGold,
                                fontWeight = FontWeight.Black,
                                fontFamily = NunitoFont
                            )
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (step) {
                NavStep.WELCOME -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(floatingBoxModifier)
                            .background(UofAGold)
                            .border(4.dp, UofAWhite, RoundedCornerShape(24.dp))
                            .padding(36.dp)
                    ) {
                        Text(
                            "Tap anywhere to begin",
                            style = MaterialTheme.typography.headlineLarge,
                            color = UofAGreenDark,
                            fontWeight = FontWeight.Bold,
                            fontFamily = NunitoFont,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
                NavStep.ASK_LOCATION, NavStep.SHOW_LOCATION -> {
                    Text(
                        "Current location",
                        style = MaterialTheme.typography.titleMedium,
                        color = UofACream
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(floatingBoxModifier)
                            .background(UofAGold)
                            .border(2.dp, UofAWhite.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                            .padding(32.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val displayText = when {
                                currentLocation != null -> currentLocation!!.fullName
                                locationListenFailed -> "Couldn't hear you. Tap to try again."
                                else -> (liveListeningText?.takeIf { pendingSpeechFor == "location" } ?: "Listening...")
                            }
                            Text(
                                displayText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = UofAGreenDark,
                                textAlign = TextAlign.Center
                            )
                            if (currentLocation != null && currentLocation!!.fullName != currentLocation!!.abbreviation) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    "(${currentLocation!!.abbreviation})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UofAGreen
                                )
                            }
                            if (currentLocation != null && currentLocationTranscribed != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "Decoded: $currentLocationTranscribed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = UofASlate,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                NavStep.ASK_DESTINATION, NavStep.VERIFY -> {
                    val arrowProgress by animateFloatAsState(
                        targetValue = if (destination != null) 1f else 0f,
                        animationSpec = tween(1000),
                        label = "arrow"
                    )
                    Text(
                        if (step == NavStep.VERIFY) "Confirm: From → To" else "From → To",
                        style = MaterialTheme.typography.titleMedium,
                        color = UofAGoldLight
                    )
                    if (step == NavStep.VERIFY) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Say yes or no",
                            style = MaterialTheme.typography.bodyMedium,
                            color = UofACream
                        )
                    }
                    Spacer(modifier = Modifier.height(28.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(floatingBoxModifier)
                            .background(UofAGold)
                            .border(2.dp, UofAWhite.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .padding(20.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                currentLocation?.fullName ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = UofAGreenDark,
                                textAlign = TextAlign.Center
                            )
                            if (currentLocation != null && currentLocation!!.fullName != currentLocation!!.abbreviation) {
                                Text(
                                    "(${currentLocation!!.abbreviation})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = UofASlate
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    val lineHeight = 160.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(lineHeight),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxWidth().height(lineHeight)) {
                            val strokeWidth = 2.5.dp.toPx()
                            val midX = size.width / 2
                            drawLine(
                                color = UofAGoldLight.copy(alpha = 0.9f),
                                start = Offset(midX, 0f),
                                end = Offset(midX, size.height),
                                strokeWidth = strokeWidth
                            )
                            val dotY = size.height * arrowProgress
                            drawCircle(
                                color = UofAGreenLight,
                                radius = 6.dp.toPx(),
                                center = Offset(midX, dotY)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                        if (destination != null) {
                            val pinScale by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = keyframes {
                                    durationMillis = 600
                                    0f at 0
                                    1.55f at 180
                                    0.92f at 320
                                    1.08f at 420
                                    1f at 600
                                },
                                label = "pin"
                            )
                            Icon(
                                Icons.Default.Place,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(64.dp)
                                    .scale(pinScale)
                                    .padding(end = 14.dp),
                                tint = AccessUDestPin
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .then(floatingBoxModifier)
                                .background(UofAGold)
                                .border(2.dp, UofAWhite.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                .padding(32.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val destDisplayText = when {
                                    destination != null -> destination!!.fullName
                                    destinationListenFailed -> "Couldn't hear you. Tap to try again."
                                    else -> (liveListeningText?.takeIf { pendingSpeechFor == "destination" } ?: "Listening...")
                                }
                                val verifyStatusText = when {
                                    step == NavStep.VERIFY && pendingSpeechFor == "verify" -> (liveListeningText ?: "Listening...")
                                    step == NavStep.VERIFY && verifyListenFailed -> "Say yes or no. Tap to try again."
                                    else -> null
                                }
                                Text(
                                    destDisplayText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = UofACharcoal,
                                    textAlign = TextAlign.Center
                                )
                                if (verifyStatusText != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        verifyStatusText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = UofAWarmGray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                                if (destination != null && destination!!.fullName != destination!!.abbreviation) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "(${destination!!.abbreviation})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = UofASlate
                                    )
                                }
                                if (destination != null && destinationTranscribed != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        "Decoded: $destinationTranscribed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = UofASlate,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
                }
                NavStep.CONFIRMED -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(floatingBoxModifier)
                            .background(UofAGold)
                            .border(2.dp, UofAWhite.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                            .padding(32.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Confirmed",
                                style = MaterialTheme.typography.titleLarge,
                                color = UofACharcoal,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "${currentLocation?.fullName ?: ""} → ${destination?.fullName ?: ""}",
                                style = MaterialTheme.typography.bodyLarge,
                                color = UofASlate,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                NavStep.NAVIGATING -> {
                    if (!hasCameraPermission) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(floatingBoxModifier)
                                .background(UofAGold)
                                .border(2.dp, UofAWhite.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                .padding(32.dp)
                                .clickable { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Grant Camera Permission",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = UofACharcoal,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Tap to allow camera for navigation",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = UofASlate,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
                else -> {}
            }
        }
        }
    }
}
