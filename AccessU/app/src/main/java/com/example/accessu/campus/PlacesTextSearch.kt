package com.example.accessu.campus

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Uses Google Places Text Search API with location bias for accurate building coordinates.
 * Prefer this over plain Geocoding to avoid generic "University of Alberta" results.
 *
 * Requires Places API enabled. Fallback to Geocoding API if no results.
 */
class PlacesTextSearch(private val apiKey: String) {

    data class LatLon(val lat: Double, val lon: Double)
    data class SearchResult(val latLon: LatLon?, val status: String)

    private val campusCenter = "53.5232,-113.5263"
    private val radiusMeters = 2500

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Text search with campus location bias. Returns best result or null.
     * status is logged for debugging (OK, ZERO_RESULTS, OVER_QUERY_LIMIT, etc.)
     */
    fun search(query: String): SearchResult {
        val safeKey = apiKey.ifBlank { return SearchResult(null, "NO_API_KEY") }
        val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8)
        val url = "https://maps.googleapis.com/maps/api/place/textsearch/json" +
            "?query=$encoded" +
            "&location=$campusCenter" +
            "&radius=$radiusMeters" +
            "&key=$safeKey"
        return try {
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return SearchResult(null, "HTTP_${resp.code}")
            val body = resp.body?.string() ?: return SearchResult(null, "EMPTY_BODY")
            val parsed = com.google.gson.Gson().fromJson(body, PlacesResponse::class.java)
            val status = parsed.status ?: "UNKNOWN"
            val latLon = parsed.results?.firstOrNull()?.geometry?.location?.let {
                LatLon(it.lat, it.lng)
            }
            SearchResult(latLon, status)
        } catch (e: Exception) {
            SearchResult(null, "ERROR: ${e.message}")
        }
    }

    private data class PlacesResponse(
        @SerializedName("status") val status: String?,
        @SerializedName("results") val results: List<PlaceResult>?
    )

    private data class PlaceResult(
        @SerializedName("geometry") val geometry: PlaceGeometry?
    )

    private data class PlaceGeometry(
        @SerializedName("location") val location: PlaceLocation?
    )

    private data class PlaceLocation(
        @SerializedName("lat") val lat: Double,
        @SerializedName("lng") val lng: Double
    )
}
