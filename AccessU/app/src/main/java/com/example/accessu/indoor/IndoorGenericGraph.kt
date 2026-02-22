package com.example.accessu.indoor

import com.google.gson.annotations.SerializedName

data class IndoorGenericGraph(
    @SerializedName("version") val version: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("nodes") val nodes: List<IndoorNode>,
    @SerializedName("edges") val edges: List<IndoorEdge>
)

data class IndoorNode(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String
)

data class IndoorEdge(
    @SerializedName("from") val from: String,
    @SerializedName("to") val to: String,
    @SerializedName("say") val say: String
)
