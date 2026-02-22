package com.example.accessu.campus

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Fetches lat/lon for place names via Google Geocoding API.
 * Handles OVER_QUERY_LIMIT / RESOURCE_EXHAUSTED: wait 2s, retry once max.
 */
class GoogleMapsGeocoder(private val apiKey: String) {

    data class LatLon(val lat: Double, val lon: Double)
    data class LookupResult(val latLon: LatLon?, val status: GeocodeStatus)

    enum class GeocodeStatus { OK, ZERO_RESULTS, OVER_QUERY_LIMIT, OTHER_ERROR }

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Geocode a query. Returns result with status. Use status to detect rate limits.
     */
    fun lookup(query: String): LookupResult {
        val safeKey = apiKey.ifBlank { return LookupResult(null, GeocodeStatus.OTHER_ERROR) }
        val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8)
        val url = "https://maps.googleapis.com/maps/api/geocode/json?address=$encoded&components=country:CA&key=$safeKey"
        return try {
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return LookupResult(null, GeocodeStatus.OTHER_ERROR)
            val body = resp.body?.string() ?: return LookupResult(null, GeocodeStatus.OTHER_ERROR)
            val parsed = com.google.gson.Gson().fromJson(body, GeocodeResponse::class.java)
            val status = when (parsed.status?.uppercase()) {
                "OK" -> GeocodeStatus.OK
                "ZERO_RESULTS" -> GeocodeStatus.ZERO_RESULTS
                "OVER_QUERY_LIMIT", "RESOURCE_EXHAUSTED" -> GeocodeStatus.OVER_QUERY_LIMIT
                else -> GeocodeStatus.OTHER_ERROR
            }
            val latLon = parsed.results?.firstOrNull()?.geometry?.location?.let { LatLon(it.lat, it.lng) }
            LookupResult(latLon, status)
        } catch (_: Exception) {
            LookupResult(null, GeocodeStatus.OTHER_ERROR)
        }
    }

    /** For backward compatibility. Returns null on rate limit after one retry. */
    fun lookupLatLon(query: String): LatLon? = lookupWithRetry(this, query)

    private data class GeocodeResponse(
        @SerializedName("status") val status: String?,
        @SerializedName("results") val results: List<GeocodeResult>?
    )

    private data class GeocodeResult(
        @SerializedName("geometry") val geometry: Geometry?
    )

    private data class Geometry(
        @SerializedName("location") val location: Location?
    )

    private data class Location(
        @SerializedName("lat") val lat: Double,
        @SerializedName("lng") val lng: Double
    )

    companion object {
        private const val RATE_LIMIT_WAIT_MS = 2000L

        /**
         * Retry once on OVER_QUERY_LIMIT (wait 2s). Otherwise no further retries.
         */
        fun lookupWithRetry(geocoder: GoogleMapsGeocoder, query: String): LatLon? {
            val first = geocoder.lookup(query)
            if (first.status == GeocodeStatus.OK && first.latLon != null) return first.latLon
            if (first.status == GeocodeStatus.OVER_QUERY_LIMIT) {
                Thread.sleep(RATE_LIMIT_WAIT_MS)
                val second = geocoder.lookup(query)
                return second.latLon?.takeIf { second.status == GeocodeStatus.OK }
            }
            return null
        }
    }
}
