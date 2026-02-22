package com.example.accessu.indoor

import android.content.Context
import com.google.gson.Gson
import java.io.InputStreamReader

class IndoorGenericGraphRepository(private val context: Context) {

    private val gson = Gson()

    fun loadGraph(): IndoorGenericGraph? {
        return try {
            context.assets.open("indoor_generic/indoor_graph.json").use { stream ->
                gson.fromJson(InputStreamReader(stream), IndoorGenericGraph::class.java)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getNodeIds(): List<String> = loadGraph()?.nodes?.map { it.id } ?: emptyList()

    fun getNodeName(id: String): String? = loadGraph()?.nodes?.find { it.id == id }?.name
}
