package com.example.accessu.paths

import com.google.gson.annotations.SerializedName

data class PathMetadata(
    val id: String,
    val name: String,
    val description: String,
    val origin: String,
    val destination: String,
    @SerializedName("keyframeCount") val keyframeCount: Int = 0,
    @SerializedName("estimatedDurationSeconds") val estimatedDurationSeconds: Int = 120
)
