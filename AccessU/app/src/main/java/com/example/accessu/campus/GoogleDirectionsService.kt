package com.example.accessu.campus

import com.example.accessu.BuildConfig
import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DirectionsResult(
    val success: Boolean,
    val steps: List<String>,
    val error: String? = null
)

/**
 * Fetches walking directions from Google Directions API.
 * Requires GOOGLE_MAPS_API_KEY in local.properties.
 */
class GoogleDirectionsService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun getWalkingDirections(
        originLat: Double, originLon: Double,
        destLat: Double, destLon: Double
    ): DirectionsResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GOOGLE_MAPS_API_KEY
        if (apiKey.isBlank()) {
            return@withContext DirectionsResult(success = false, steps = emptyList(), error = "No API key")
        }
        val url = "https://maps.googleapis.com/maps/api/directions/json" +
            "?origin=$originLat,$originLon" +
            "&destination=$destLat,$destLon" +
            "&mode=walking" +
            "&key=$apiKey"
        val request = Request.Builder().url(url).build()
        runCatching {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@runCatching null
            parseDirectionsResponse(body)
        }.getOrElse { DirectionsResult(success = false, steps = emptyList(), error = it.message) }
            ?: DirectionsResult(success = false, steps = emptyList(), error = "Failed to parse")
    }

    private fun parseDirectionsResponse(json: String): DirectionsResult? {
        val root = com.google.gson.Gson().fromJson(json, DirectionsResponse::class.java)
            ?: return null
        if (root.status != "OK") return DirectionsResult(
            success = false,
            steps = emptyList(),
            error = root.status
        )
        val steps = root.routes?.firstOrNull()?.legs?.flatMap { leg ->
            (leg.steps ?: emptyList()).map { step ->
                val instr = step.htmlInstructions
                    ?.replace(Regex("<[^>]+>"), "")
                    ?.replace("&nbsp;", " ")
                    ?.trim() ?: "Continue"
                val dist = step.distance?.text ?: ""
                if (dist.isNotEmpty()) "$instr for $dist" else instr
            }
        } ?: emptyList()
        return DirectionsResult(success = true, steps = steps)
    }
}

private data class DirectionsResponse(
    @SerializedName("status") val status: String,
    @SerializedName("routes") val routes: List<Route>?
)

private data class Route(
    @SerializedName("legs") val legs: List<Leg>?
)

private data class Leg(
    @SerializedName("steps") val steps: List<Step>?
)

private data class Step(
    @SerializedName("html_instructions") val htmlInstructions: String?,
    @SerializedName("distance") val distance: Distance?
)

private data class Distance(
    @SerializedName("text") val text: String?
)
