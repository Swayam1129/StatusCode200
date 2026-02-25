package com.example.accessu.obstacle

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.example.accessu.obstacle.CameraPipeline.FrameData

class ObstacleDetector(private val context: Context) {

    // Expose the detector so the new QR camera screen can feed it directly
    val objectDetector: ObjectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects()
            .build()
        ObjectDetection.getClient(options)
    }

    private var lastSpokenMessage: String? = null
    private var lastSpokenTimeMs: Long = 0
    private val globalCooldownMs = 3500L
    private var lastStateWasObstacle: Boolean = false
    private var hasSpokenInitialClear: Boolean = false

    // 1. THIS FIXES THE ERRORS: The helper for ObstacleCameraScreen.kt
    fun processFrame(frame: FrameData): String? {
        val bitmap = frame.bitmap ?: return null
        if (bitmap.width < 32 || bitmap.height < 32) return null

        return try {
            val inputImage = InputImage.fromBitmap(bitmap, frame.rotationDegrees)
            val task = objectDetector.process(inputImage)
            val detectedObjects = Tasks.await(task)

            // Pass the data into our new, smarter detection logic
            val result = processDetections(detectedObjects, frame.width, frame.height, frame.rotationDegrees)
            bitmap.recycle()
            result
        } catch (e: Exception) {
            Log.e("ObstacleDetector", "Detection error", e)
            try { bitmap.recycle() } catch (_: Exception) { }
            null
        }
    }

    // 2. The main logic used by your new CameraNavigationActivity.kt
    fun processDetections(
        detectedObjects: List<DetectedObject>,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int
    ): String? {
        val isPortrait = rotationDegrees == 90 || rotationDegrees == 270
        val actualWidth = if (isPortrait) imageHeight else imageWidth
        val actualHeight = if (isPortrait) imageWidth else imageHeight

        val message = analyzeDetections(detectedObjects, actualWidth, actualHeight)

        val now = System.currentTimeMillis()
        val timeSinceLastSpeak = now - lastSpokenTimeMs

        val result = when {
            message.contains("Obstacle") -> {
                lastStateWasObstacle = true
                hasSpokenInitialClear = true

                if (timeSinceLastSpeak >= globalCooldownMs) {
                    lastSpokenMessage = message
                    lastSpokenTimeMs = now
                    message
                } else null
            }
            else -> {
                if (lastStateWasObstacle && timeSinceLastSpeak >= globalCooldownMs) {
                    lastStateWasObstacle = false
                    lastSpokenMessage = message
                    lastSpokenTimeMs = now
                    message
                } else if (!hasSpokenInitialClear) {
                    hasSpokenInitialClear = true
                    lastSpokenMessage = message
                    lastSpokenTimeMs = now
                    message
                } else null
            }
        }
        return result
    }

    private fun analyzeDetections(
        detectedObjects: List<DetectedObject>,
        imageWidth: Int,
        imageHeight: Int
    ): String {
        val centerX = imageWidth / 2f
        val totalPixels = imageWidth * imageHeight

        val centerPathLeft = imageWidth * 0.25f
        val centerPathRight = imageWidth * 0.75f

        val pathTop = imageHeight * 0.15f
        val pathBottom = imageHeight * 1.0f

        val minBboxArea = totalPixels * 0.01f
        val maxBboxArea = totalPixels * 1.0f

        var mostUrgentObstacle: Pair<String, Float>? = null
        var lowestY = 0f

        for (obj in detectedObjects) {
            val box = obj.boundingBox
            val area = box.width() * box.height()

            if (area < minBboxArea || area > maxBboxArea) continue

            val boxCenterX = box.centerX().toFloat()
            val boxCenterY = box.centerY().toFloat()
            val boxBottomY = box.bottom.toFloat()

            if (boxCenterX < centerPathLeft || boxCenterX > centerPathRight) continue
            if (boxCenterY < pathTop || boxCenterY > pathBottom) continue

            val evasionInstruction = if (boxCenterX < centerX) "Obstacle ahead, move slightly right." else "Obstacle ahead, move slightly left."

            if (mostUrgentObstacle == null || boxBottomY > lowestY) {
                mostUrgentObstacle = evasionInstruction to boxCenterX
                lowestY = boxBottomY
            }
        }

        return if (mostUrgentObstacle != null) mostUrgentObstacle!!.first else "Path is clear."
    }
}