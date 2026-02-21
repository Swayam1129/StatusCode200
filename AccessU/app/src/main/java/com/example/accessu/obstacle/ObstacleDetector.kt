package com.example.accessu.obstacle

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.example.accessu.obstacle.CameraPipeline.FrameData

class ObstacleDetector(private val context: Context) {

    private val objectDetector: ObjectDetector by lazy {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .build()
        ObjectDetection.getClient(options)
    }

    private var lastSpokenMessage: String? = null
    private var lastSpokenTimeMs: Long = 0
    private val globalCooldownMs = 3500L
    private var lastStateWasObstacle: Boolean = false
    private var hasSpokenInitialClear: Boolean = false

    fun processFrame(frame: FrameData): String? {
        val bitmap = frame.bitmap ?: return null
        if (bitmap.width < 32 || bitmap.height < 32) return null

        return try {
            val inputImage = InputImage.fromBitmap(bitmap, frame.rotationDegrees)
            val task = objectDetector.process(inputImage)
            val detectedObjects = Tasks.await(task)

            val isPortrait = frame.rotationDegrees == 90 || frame.rotationDegrees == 270
            val actualWidth = if (isPortrait) frame.height else frame.width
            val actualHeight = if (isPortrait) frame.width else frame.height

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
                    } else {
                        null
                    }
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
                    } else {
                        null
                    }
                }
            }

            bitmap.recycle()
            result
        } catch (e: Exception) {
            Log.e("ObstacleDetector", "Detection error", e)
            try { bitmap.recycle() } catch (_: Exception) { }
            null
        }
    }

    private fun analyzeDetections(
        detectedObjects: List<com.google.mlkit.vision.objects.DetectedObject>,
        imageWidth: Int,
        imageHeight: Int
    ): String {
        val centerX = imageWidth / 2f
        val totalPixels = imageWidth * imageHeight

        // 50% Path: Strict shoulder-width cylinder
        val centerPathLeft = imageWidth * 0.25f
        val centerPathRight = imageWidth * 0.75f

        // HORIZON FIX: Raised back up to 15% to see people walking towards you from further away
        val pathTop = imageHeight * 0.15f
        val pathBottom = imageHeight * 1.0f

        // SIZE FIX: Minimum size is still 1%, but Maximum size is now 100% (1.0f).
        // If a massive door takes up the whole screen, DO NOT ignore it!
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

        return if (mostUrgentObstacle != null) {
            mostUrgentObstacle!!.first
        } else {
            "Path is clear."
        }
    }
}
