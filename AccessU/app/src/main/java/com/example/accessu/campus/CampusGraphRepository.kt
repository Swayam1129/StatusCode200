package com.example.accessu.campus

import android.content.Context
import com.google.gson.Gson
import java.io.InputStreamReader

class CampusGraphRepository(private val context: Context) {

    private val gson = Gson()
    private var graph: CampusGraph? = null
    private val coordinateCache = CoordinateCache(context)

    fun loadGraph(): CampusGraph? {
        if (graph != null) return graph
        graph = try {
            context.assets.open("campus/buildings.json").use { stream ->
                gson.fromJson(InputStreamReader(stream), CampusGraph::class.java)
            }
        } catch (_: Exception) {
            null
        }
        return graph
    }

    fun resolveNode(node: BuildingNode, badNodeIds: Set<String> = emptySet()): ResolvedNode {
        val cached = coordinateCache.get(node.id)
        if (cached != null) return ResolvedNode(node, cached.lat, cached.lon, CoordSource.CACHE)
        val jsonLat = node.lat
        val jsonLon = node.lon
        val jsonValid = jsonLat != null && jsonLon != null && node.id !in badNodeIds &&
            !isPlaceholder(jsonLat, jsonLon)
        if (jsonValid) return ResolvedNode(node, jsonLat, jsonLon, CoordSource.JSON)
        return ResolvedNode(node, null, null, CoordSource.FALLBACK)
    }

    private fun isPlaceholder(lat: Double, lon: Double): Boolean {
        if (lat == 0.0 && lon == 0.0) return true
        val dist = NavigationUtils.haversineMeters(lat, lon, CAMPUS_CENTER_LAT, CAMPUS_CENTER_LON)
        return dist < 1.0
    }

    fun detectBadNodeIds(): Set<String> {
        val g = loadGraph() ?: return emptySet()
        val bad = mutableSetOf<String>()
        for (n in g.nodes) {
            if (n.lat == null || n.lon == null) bad.add(n.id)
            else if (isPlaceholder(n.lat, n.lon)) bad.add(n.id)
        }
        return bad
    }

    fun getNodesForPicker(): Pair<List<ResolvedNode>, List<ResolvedNode>> {
        val g = loadGraph() ?: return emptyList<ResolvedNode>() to emptyList()
        val badIds = detectBadNodeIds()
        val all = g.nodes
            .filter { it.type == "building" || it.id == "university_transit" }
            .sortedBy { it.name }
            .map { resolveNode(it, badIds) }
        val resolved = all.filter { it.isResolved }
        val unresolved = all.filter { !it.isResolved }
        return resolved to unresolved
    }

    fun findRoute(originId: String, destId: String): List<ResolvedNode> {
        val g = loadGraph() ?: return emptyList()
        val badIds = detectBadNodeIds()
        val nodeMap = g.nodes.associateBy { it.id }
        val origin = nodeMap[originId] ?: return emptyList()
        val dest = nodeMap[destId] ?: return emptyList()
        val rOrigin = resolveNode(origin, badIds)
        val rDest = resolveNode(dest, badIds)
        if (!rOrigin.isResolved || !rDest.isResolved) return emptyList()

        val queue = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        val parent = mutableMapOf<String, String>()
        queue.addLast(originId)
        visited.add(originId)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (curr == destId) break
            val node = nodeMap[curr] ?: continue
            for (next in node.connections) {
                if (next !in visited) {
                    val nextNode = nodeMap[next] ?: continue
                    val rNext = resolveNode(nextNode, badIds)
                    if (!rNext.isResolved) continue
                    visited.add(next)
                    parent[next] = curr
                    queue.addLast(next)
                }
            }
        }

        val path = mutableListOf<String>()
        var at = destId
        while (at.isNotEmpty()) {
            path.add(0, at)
            at = parent[at] ?: ""
        }
        return path.mapNotNull { nodeMap[it] }.map { resolveNode(it, badIds) }.filter { it.isResolved }
    }

    fun getOriginCoordsForIndoorPath(pathId: String): Pair<Double, Double>? {
        val ref = loadGraph()?.indoorPaths?.get(pathId) ?: return null
        val r = getNode(ref.origin) ?: return null
        if (!r.isResolved) return null
        return r.safeLat to r.safeLon
    }

    fun getNode(id: String): ResolvedNode? =
        loadGraph()?.nodes?.find { it.id == id }?.let { resolveNode(it, detectBadNodeIds()) }

    fun getIndoorPathId(originId: String, destId: String): String? {
        val g = loadGraph() ?: return null
        for ((pathId, ref) in g.indoorPaths ?: emptyMap()) {
            if (ref.origin == originId && ref.destination == destId) return pathId
        }
        return null
    }

    fun getUnresolvedNames(originId: String, destId: String): List<String> {
        val badIds = detectBadNodeIds()
        val g = loadGraph() ?: return emptyList()
        return listOf(originId, destId).mapNotNull { id ->
            val node = g.nodes.find { it.id == id } ?: return@mapNotNull null
            val r = resolveNode(node, badIds)
            if (!r.isResolved) node.name else null
        }
    }

}
