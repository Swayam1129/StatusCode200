package com.example.accessu.weather

import com.example.accessu.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Fetches Edmonton weather and returns ice warning when temp <= 0°C and precip (snow/rain).
 */
class WeatherService {

    private val client = OkHttpClient.Builder().build()
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        private const val EDMONTON_LAT = 53.55
        private const val EDMONTON_LON = -113.50
        private const val BASE_URL = "https://api.openweathermap.org/data/2.5/weather"
    }

    /**
     * Returns "Caution: possible ice in Edmonton. Walk carefully." when conditions indicate ice risk,
     * else null. Call from background thread.
     */
    fun getIceWarningMessage(callback: (String?) -> Unit) {
        val apiKey = BuildConfig.OPENWEATHER_API_KEY
        if (apiKey.isBlank()) {
            callback(null)
            return
        }

        val url = "$BASE_URL?lat=$EDMONTON_LAT&lon=$EDMONTON_LON&units=metric&appid=$apiKey"
        executor.execute {
            try {
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    callback(null)
                    return@execute
                }
                val body = response.body?.string() ?: return@execute callback(null)
                val json = JSONObject(body)
                val temp = json.optJSONObject("main")?.optDouble("temp", 100.0) ?: 100.0
                val weatherArray = json.optJSONArray("weather") ?: return@execute callback(null)
                val main = (0 until weatherArray.length())
                    .mapNotNull { weatherArray.optJSONObject(it)?.optString("main", "") }
                    .firstOrNull() ?: ""

                val hasPrecip = main.contains("rain", true) ||
                    main.contains("snow", true) ||
                    main.contains("drizzle", true)

                val iceRisk = temp <= 0 && hasPrecip
                callback(
                    if (iceRisk) "Caution: possible ice in Edmonton. Walk carefully."
                    else null
                )
            } catch (e: Exception) {
                e.printStackTrace()
                callback(null)
            }
        }
    }
}
