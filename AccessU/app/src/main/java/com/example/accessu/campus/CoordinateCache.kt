package com.example.accessu.campus

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class CoordinateCache(private val context: Context) {

    private val gson = Gson()
    private val type = object : TypeToken<Map<String, CachedCoords>>() {}.type
    private val file = File(context.filesDir, "campus_coords_cache.json")

    data class CachedCoords(val lat: Double, val lon: Double)

    fun get(nodeId: String): CachedCoords? = load()[nodeId]

    fun put(nodeId: String, lat: Double, lon: Double) {
        val map = load().toMutableMap()
        map[nodeId] = CachedCoords(lat, lon)
        save(map)
    }

    fun remove(nodeId: String) {
        val map = load().toMutableMap()
        map.remove(nodeId)
        save(map)
    }

    fun clear() = save(emptyMap())

    fun entryCount(): Int = load().size

    private fun load(): Map<String, CachedCoords> = try {
        if (file.exists()) {
            file.readText().let { gson.fromJson(it, type) } ?: emptyMap()
        } else emptyMap()
    } catch (_: Exception) {
        emptyMap()
    }

    private fun save(map: Map<String, CachedCoords>) {
        try {
            file.writeText(gson.toJson(map))
        } catch (_: Exception) {}
    }
}
