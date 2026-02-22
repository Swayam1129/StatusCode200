package com.example.accessu.obstacle

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PixelFormat
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Camera pipeline for obstacle detection.
 * Captures frames (every Nth frame) and passes to a processor on a background thread.
 * Uses RGBA_8888 output for direct RGB access (TFLite-compatible).
 * Sanika: Task 1 - Camera pipeline.
 */
class CameraPipeline(
    private val frameProcessor: (FrameData) -> Unit,
    private val captureEveryNthFrame: Int = 3
) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var frameCount = 0

    private var lastFrameTimeMs = 0L
    private var fpsUpdateInterval = 0

    /**
     * Create the ImageAnalysis analyzer for CameraX.
     * Attach to ImageAnalysis use case.
     */
    fun createAnalyzer(): ImageAnalysis.Analyzer = ImageAnalysis.Analyzer { imageProxy ->
        frameCount++
        if (frameCount % captureEveryNthFrame != 0) {
            imageProxy.close()
            return@Analyzer
        }

        val frame = extractRgb(imageProxy)
        val rotation = imageProxy.imageInfo.rotationDegrees
        if (frame != null) {
            val bitmap = rgbToBitmap(frame.data, frame.width, frame.height)
            val frameData = FrameData(
                rgbData = frame.data,
                width = frame.width,
                height = frame.height,
                bitmap = bitmap,
                rotationDegrees = rotation
            )
            scope.launch {
                try {
                    frameProcessor(frameData)
                } catch (e: Exception) {
                    Log.e(TAG, "Frame processing error", e)
                }
            }
        }

        // Log FPS periodically (every ~30 frames)
        fpsUpdateInterval++
        if (fpsUpdateInterval >= 30) {
            val now = System.currentTimeMillis()
            val elapsed = now - lastFrameTimeMs
            if (elapsed > 0) {
                val fps = (frameCount * 1000f / elapsed).coerceAtMost(30f)
                Log.d(TAG, "Frames received: $frameCount, ~${"%.1f".format(fps)} FPS (every $captureEveryNthFrame frame)")
            }
            lastFrameTimeMs = now
            fpsUpdateInterval = 0
        }

        imageProxy.close()
    }

    /**
     * Extract RGB from ImageProxy.
     * Supports RGBA_8888 (from setOutputImageFormat) or YUV_420_888 (fallback).
     */
    private fun extractRgb(imageProxy: ImageProxy): RgbFrame? {
        val width = imageProxy.width
        val height = imageProxy.height

        when (imageProxy.format) {
            PixelFormat.RGBA_8888 -> {
                val plane = imageProxy.planes[0]
                val buffer = plane.buffer
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride
                val rgba = ByteArray(buffer.remaining())
                buffer.get(rgba)
                // RGBA -> RGB (strip alpha for TFLite), handle row stride
                val rgb = ByteArray(width * height * 3)
                for (row in 0 until height) {
                    for (col in 0 until width) {
                        val srcIdx = row * rowStride + col * pixelStride
                        val dstIdx = (row * width + col) * 3
                        rgb[dstIdx] = rgba[srcIdx]
                        rgb[dstIdx + 1] = rgba[srcIdx + 1]
                        rgb[dstIdx + 2] = rgba[srcIdx + 2]
                    }
                }
                return RgbFrame(rgb, width, height)
            }
            ImageFormat.YUV_420_888 -> {
                val rgb = yuv420ToRgb(imageProxy, width, height)
                return RgbFrame(rgb, width, height)
            }
            else -> {
                Log.w(TAG, "Unexpected format: ${imageProxy.format}")
                return null
            }
        }
    }

    private fun yuv420ToRgb(imageProxy: ImageProxy, width: Int, height: Int): ByteArray {
        val rgb = ByteArray(width * height * 3)
        val yPlane = imageProxy.planes[0]
        val uPlane = imageProxy.planes[1]
        val vPlane = imageProxy.planes[2]
        val yArray = ByteArray(yPlane.buffer.remaining()).apply { yPlane.buffer.get(this) }
        val uArray = ByteArray(uPlane.buffer.remaining()).apply { uPlane.buffer.get(this) }
        val vArray = ByteArray(vPlane.buffer.remaining()).apply { vPlane.buffer.get(this) }
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        for (j in 0 until height) {
            for (i in 0 until width) {
                val y = (yArray[j * yRowStride + i * yPixelStride].toInt() and 0xff) - 16
                val u = (uArray[(j / 2) * uRowStride + (i / 2) * uPixelStride].toInt() and 0xff) - 128
                val v = (vArray[(j / 2) * vRowStride + (i / 2) * vPixelStride].toInt() and 0xff) - 128

                val r = (298 * y + 409 * v + 128).shr(8).coerceIn(0, 255)
                val g = (298 * y - 100 * u - 208 * v + 128).shr(8).coerceIn(0, 255)
                val b = (298 * y + 516 * u + 128).shr(8).coerceIn(0, 255)

                val idx = (j * width + i) * 3
                rgb[idx] = r.toByte()
                rgb[idx + 1] = g.toByte()
                rgb[idx + 2] = b.toByte()
            }
        }
        return rgb
    }

    private fun rgbToBitmap(rgb: ByteArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (i in 0 until width * height) {
            val r = rgb[i * 3].toInt() and 0xFF
            val g = rgb[i * 3 + 1].toInt() and 0xFF
            val b = rgb[i * 3 + 2].toInt() and 0xFF
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    data class RgbFrame(val data: ByteArray, val width: Int, val height: Int)
    data class FrameData(
        val rgbData: ByteArray,
        val width: Int,
        val height: Int,
        val bitmap: Bitmap? = null,
        val rotationDegrees: Int = 0
    )

    companion object {
        private const val TAG = "CameraPipeline"
    }
}
