package com.example.accessu.obstacle

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.accessu.core.AudioGuide

/**
 * Composable camera screen for obstacle detection.
 * Shows camera preview and runs frame analysis.
 *
 * Integration (Niharika):
 * - Walking mode + nav active: ObstacleCameraScreen(isActive = true)
 * - On bus: ObstacleCameraScreen(isActive = false) — stops camera, no obstacle messages
 * - Use ObstacleIntegration.isWalkingModeActive to sync with app state
 */
@Composable
fun ObstacleCameraScreen(
    isActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(context) {
        AudioGuide.init(context)
        onDispose { }
    }

    when {
        !hasCameraPermission -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Camera Permission")
                }
            }
        }
        !isActive -> {
            Text(
                "Obstacle camera paused (On bus mode)",
                modifier = modifier.padding(16.dp)
            )
        }
        else -> {
            Box(modifier = modifier.fillMaxSize()) {
                val detector = remember(context) { ObstacleDetector(context) }
                var lastObstacleMessage by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(lastObstacleMessage) {
                    if (lastObstacleMessage != null) {
                        delay(5000) // Clear indicator after 5 seconds
                        lastObstacleMessage = null
                    }
                }
                val cameraPipeline = remember {
                    CameraPipeline(
                        frameProcessor = { frame ->
                            val msg = detector.processFrame(frame)
                                if (msg != null) {
                                lastObstacleMessage = if (msg.startsWith("move ")) msg else null  // Badge for move left/right
                                AudioGuide.speak(msg)
                            }
                        },
                        captureEveryNthFrame = 3  // ~10 FPS - gives ML Kit time for reliable detection
                    )
                }

                var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
                var hasBound by remember { mutableStateOf(false) }

                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { previewView ->
                        if (!hasBound) {
                            hasBound = true
                            val providerFuture = ProcessCameraProvider.getInstance(context)
                            providerFuture.addListener({
                                val provider = providerFuture.get()
                                cameraProvider = provider
                                val preview = Preview.Builder().build().apply {
                                    setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .apply {
                                        setAnalyzer(
                                            ContextCompat.getMainExecutor(context),
                                            cameraPipeline.createAnalyzer()
                                        )
                                    }
                                try {
                                    provider.unbindAll()
                                    provider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                    Log.d("ObstacleCamera", "Camera bound successfully")
                                } catch (e: Exception) {
                                    Log.e("ObstacleCamera", "Camera bind failed", e)
                                    hasBound = false
                                }
                            }, ContextCompat.getMainExecutor(context))
                        }
                    }
                )

                DisposableEffect(Unit) {
                    onDispose {
                        cameraProvider?.unbindAll()
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Obstacle detection active")
                    // Optional visual indicator when obstacle detected (Task 6)
                    if (lastObstacleMessage != null) {
                        Text(
                            text = "⚠ $lastObstacleMessage",
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
