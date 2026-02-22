package com.example.accessu.indoor

import java.util.ArrayDeque

/**
 * BFS shortest path on the indoor generic graph.
 * Returns path as list of node ids; step instructions are resolved from graph edges.
 */
class IndoorGenericRouter(private val graph: IndoorGenericGraph) {

    private val neighborsByFrom: Map<String, List<String>> = run {
        val map = mutableMapOf<String, MutableList<String>>()
        for (edge in graph.edges) {
            map.getOrPut(edge.from) { mutableListOf() }.add(edge.to)
        }
        map
    }

    private fun edgeSay(from: String, to: String): String? =
        graph.edges.find { it.from == from && it.to == to }?.say

    fun findPath(startId: String, destId: String): List<String> {
        if (startId == destId) return listOf(startId)
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        val parent = mutableMapOf<String, String>()
        queue.addLast(startId)
        visited.add(startId)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            if (curr == destId) {
                val path = mutableListOf<String>()
                var at = destId
                while (at.isNotEmpty()) {
                    path.add(0, at)
                    at = parent[at] ?: ""
                }
                return path
            }
            for (next in neighborsByFrom[curr] ?: emptyList()) {
                if (next !in visited) {
                    visited.add(next)
                    parent[next] = curr
                    queue.addLast(next)
                }
            }
        }
        return emptyList()
    }

    /**
     * Returns list of (nodeId, stepSay) for each step along the path.
     * stepSay is the instruction for going FROM previous node TO this node.
     * First element is (startId, "") since there is no "previous" step.
     */
    fun getStepsWithSay(path: List<String>): List<Pair<String, String>> {
        if (path.isEmpty()) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        result.add(path[0] to "")
        for (i in 1 until path.size) {
            val from = path[i - 1]
            val to = path[i]
            val say = edgeSay(from, to) ?: "Go to ${getNodeName(to)}."
            result.add(to to say)
        }
        return result
    }

    fun getNodeName(id: String): String = graph.nodes.find { it.id == id }?.name ?: id
}
