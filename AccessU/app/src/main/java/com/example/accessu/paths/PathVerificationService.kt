package com.example.accessu.paths

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.InputStream

/**
 * Verifies that the user's current camera view matches the recorded path.
 * Uses keyframe similarity to detect if the user is on the correct path.
 *
 * When keyframes are empty (before video recording), this service is a no-op.
 * After extracting keyframes from recorded videos, the app will compare
 * the current frame to the next expected keyframe.
 */
class PathVerificationService(
    private val context: Context,
    private val pathRepository: PathRepository
) {
    companion object {
        private const val TAG = "PathVerification"
        private const val SIMILARITY_THRESHOLD = 0.7 // placeholder - adjust after testing
    }

    private var currentKeyframeIndex = 0
    private var keyframePaths: List<String> = emptyList()

    fun loadPath(pathId: String) {
        keyframePaths = pathRepository.getKeyframePaths(pathId)
        currentKeyframeIndex = 0
        Log.d(TAG, "Loaded path $pathId with ${keyframePaths.size} keyframes")
    }

    /**
     * Check if the current frame matches the expected path position.
     * Returns progress (0.0 to 1.0) or null if path has no keyframes yet.
     */
    fun verifyFrame(currentFrame: Bitmap): Float? {
        if (keyframePaths.isEmpty()) return null
        if (currentKeyframeIndex >= keyframePaths.size) return 1f

        // TODO: After keyframes exist - load next keyframe, compute similarity
        // (e.g. using histogram comparison or feature matching)
        // For now, return null to indicate "not yet implemented"
        return null
    }

    fun getNextKeyframeImage(): InputStream? {
        if (currentKeyframeIndex >= keyframePaths.size) return null
        val path = keyframePaths[currentKeyframeIndex]
        return try {
            context.assets.open(path)
        } catch (_: Exception) {
            null
        }
    }

    fun advanceToNextKeyframe() {
        if (currentKeyframeIndex < keyframePaths.size) currentKeyframeIndex++
    }
}
