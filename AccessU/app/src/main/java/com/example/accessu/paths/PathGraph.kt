package com.example.accessu.paths

import com.google.gson.annotations.SerializedName

data class PathGraph(
    @SerializedName("pathId") val pathId: String,
    @SerializedName("nodes") val nodes: List<Node>
)

data class Node(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("instruction") val instruction: String,
    @SerializedName("nextNodeId") val nextNodeId: String?
)
