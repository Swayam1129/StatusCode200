package com.example.accessu.obstacle

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.example.accessu.obstacle.CameraPipeline.FrameData

/**
 * Obstacle detection from camera frames using ML Kit.
 * Task 2: Model integration (ML Kit Object Detection).
 * Task 3: Obstacle logic - position (left/center/right), output string.
 * Task 4: TTS via AudioGuide.speak() with throttle (handled by caller).
 */
class ObstacleDetector(private val context: Context) {

    private val objectDetector: ObjectDetector by lazy {
        // No enableClassification() - raw bounding boxes, detects walls/poles/furniture
        // SINGLE_IMAGE_MODE = complete results every frame (STREAM_MODE can return empty initially)
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .build()
        ObjectDetection.getClient(options)
    }

    private var lastSpokenMessage: String? = null
    private var lastSpokenTimeMs: Long = 0
    private val throttleMs = 5000L
    private var lastStateWasObstacle: Boolean = false
    private var hasSpokenInitialClear: Boolean = false
    private var debugLogCounter = 0

    /**
     * Returns message to speak, or null if nothing to say.
     * - Obstacle: speak with 5 sec throttle
     * - Clear path: speak ONLY when transitioning from obstacle to clear (not continuously)
     */
    fun processFrame(frame: FrameData): String? {
        val bitmap = frame.bitmap ?: return null
        if (bitmap.width < 32 || bitmap.height < 32) return null

        return try {
            val inputImage = InputImage.fromBitmap(bitmap, frame.rotationDegrees)
            val task = objectDetector.process(inputImage)
            val detectedObjects = Tasks.await(task)

            val message = analyzeDetections(detectedObjects, frame.width, frame.height)
            if (++debugLogCounter % 60 == 0) {
                Log.d(TAG, "Debug: raw detections=${detectedObjects.size}, result=$message")
            }
            val result = when {
                message.startsWith("move ") -> {
                    lastStateWasObstacle = true
                    hasSpokenInitialClear = true
                    applyThrottle(message)
                }
                else -> {
                    // "go straight" - speak when: (1) transitioning from obstacle, or (2) first time (startup)
                    if (lastStateWasObstacle) {
                        lastStateWasObstacle = false
                        lastSpokenMessage = message
                        lastSpokenTimeMs = System.currentTimeMillis()
                        message
                    } else if (!hasSpokenInitialClear) {
                        hasSpokenInitialClear = true
                        lastSpokenMessage = message
                        lastSpokenTimeMs = System.currentTimeMillis()
                        message
                    } else {
                        null
                    }
                }
            }
            if (result != null) Log.d(TAG, "Speak: $result")
            bitmap.recycle()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Detection error", e)
            try { bitmap.recycle() } catch (_: Exception) { }
            null
        }
    }

    /**
     * Throttle obstacle messages: same message not repeated within 5 sec.
     */
    private fun applyThrottle(message: String): String? {
        val now = System.currentTimeMillis()
        return if (message == lastSpokenMessage && (now - lastSpokenTimeMs) < throttleMs) {
            null
        } else {
            lastSpokenMessage = message
            lastSpokenTimeMs = now
            message
        }
    }

    /**
     * Detect obstacles AHEAD. Classification is OFF - any detected object in center path = obstacle.
     * Center path = middle 50%. Relaxed thresholds for real-world camera.
     */
    private fun analyzeDetections(
        detectedObjects: List<com.google.mlkit.vision.objects.DetectedObject>,
        imageWidth: Int,
        imageHeight: Int
    ): String {
        val centerX = imageWidth / 2f
        val totalPixels = imageWidth * imageHeight

        // Center path = middle 50% (25-75%). Wider for better detection.
        val centerPathLeft = imageWidth * 0.25f
        val centerPathRight = imageWidth * 0.75f
        val pathTop = imageHeight * 0.15f
        val pathBottom = imageHeight * 0.9f

        // Lower min (1%) so small obstacles (poles, chairs) detected. Max 60%.
        val minBboxArea = totalPixels * 0.01f
        val maxBboxArea = totalPixels * 0.6f

        var bestObstacle: Pair<String, Float>? = null

        for (obj in detectedObjects) {
            val box = obj.boundingBox
            val area = box.width() * box.height()
            if (area < minBboxArea || area > maxBboxArea) continue

            val boxCenterX = box.centerX().toFloat()
            val boxCenterY = box.centerY().toFloat()
            if (boxCenterX < centerPathLeft || boxCenterX > centerPathRight) continue
            if (boxCenterY < pathTop || boxCenterY > pathBottom) continue

            // No classification = accept any object in path as obstacle
            val moveDirection = if (boxCenterX < centerX) "move right" else "move left"
            val distFromCenter = kotlin.math.abs(boxCenterX - centerX)
            if (bestObstacle == null || distFromCenter < kotlin.math.abs(bestObstacle!!.second - centerX)) {
                bestObstacle = moveDirection to boxCenterX
            }
        }

        return if (bestObstacle != null) {
            bestObstacle!!.first
        } else {
            "go straight"
        }
    }

    companion object {
        private const val TAG = "ObstacleDetector"
    }
}
