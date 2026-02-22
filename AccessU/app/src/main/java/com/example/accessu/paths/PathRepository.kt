package com.example.accessu.paths

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

private const val TAG = "PathRepository"

class PathRepository(private val context: Context) {

    private val gson = Gson()

    fun loadPaths(): List<PathMetadata> {
        return try {
            context.assets.open("paths/paths_index.json").use { stream ->
                val reader = InputStreamReader(stream)
                val type = object : TypeToken<List<PathMetadata>>() {}.type
                gson.fromJson(reader, type) ?: emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadPaths failed", e)
            emptyList()
        }
    }

    fun loadPathGraph(pathId: String): PathGraph? {
        val id = pathId.trim()
        if (id.isEmpty()) return null
        val assetPath = "paths/$id/graph.json"
        return try {
            context.assets.open(assetPath).use { stream ->
                gson.fromJson(InputStreamReader(stream), PathGraph::class.java)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadPathGraph failed for pathId='$pathId' assetPath='$assetPath'", e)
            null
        }
    }

    fun getKeyframePaths(pathId: String): List<String> {
        return try {
            val files = context.assets.list("paths/$pathId/keyframes") ?: return emptyList()
            files
                .filter { it.endsWith(".jpg", ignoreCase = true) || it.endsWith(".jpeg", ignoreCase = true) }
                .sorted()
                .map { "paths/$pathId/keyframes/$it" }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
