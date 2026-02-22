package com.example.accessu.campus

import com.google.gson.annotations.SerializedName

data class CampusGraph(
    @SerializedName("version") val version: String,
    @SerializedName("campus") val campus: String,
    @SerializedName("nodes") val nodes: List<BuildingNode>,
    @SerializedName("indoorPaths") val indoorPaths: Map<String, IndoorPathRef>? = null
)

data class BuildingNode(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("fullName") val fullName: String,
    @SerializedName("type") val type: String,
    @SerializedName("lat") val lat: Double?,
    @SerializedName("lon") val lon: Double?,
    @SerializedName("connections") val connections: List<String>,
    @SerializedName("ttsPrompt") val ttsPrompt: String,
    @SerializedName("aliases") val aliases: List<String>? = null
)

/** Source of coordinates. Never use FALLBACK for routing. */
enum class CoordSource { CACHE, JSON, GEOCODE, FALLBACK }

/** Resolved node with known coord source. Use only isResolved nodes for routing. */
data class ResolvedNode(
    val node: BuildingNode,
    val lat: Double?,
    val lon: Double?,
    val coordSource: CoordSource
) {
    val isResolved: Boolean get() = coordSource != CoordSource.FALLBACK && lat != null && lon != null
    val isFallback: Boolean get() = coordSource == CoordSource.FALLBACK
    /** Use only for display/UI when isResolved. Never for routing. */
    val safeLat: Double get() = lat ?: CAMPUS_CENTER_LAT
    val safeLon: Double get() = lon ?: CAMPUS_CENTER_LON
}

val CAMPUS_CENTER_LAT = 53.523
val CAMPUS_CENTER_LON = -113.525

data class IndoorPathRef(
    @SerializedName("origin") val origin: String,
    @SerializedName("destination") val destination: String
)
