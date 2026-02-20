package com.example.accessu.transit

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.StringReader
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Loads ETS GTFS static data (stops, routes, trips, stop_times).
 * Can load from assets (bundled) or from URL (Edmonton GTFS zip).
 */
class GtfsStaticLoader(private val context: Context) {

    private val stops = mutableMapOf<String, Stop>()
    private val routes = mutableMapOf<String, Route>()
    private val trips = mutableMapOf<String, Trip>()
    private val stopTimesByTrip = mutableMapOf<String, MutableList<StopTime>>()

    fun getStops(): Map<String, Stop> = stops
    fun getRoutes(): Map<String, Route> = routes
    fun getTrips(): Map<String, Trip> = trips
    fun getStopTimesForTrip(tripId: String): List<StopTime> =
        stopTimesByTrip[tripId]?.sortedBy { it.stopSequence } ?: emptyList()

    fun getOrderedStopsForTrip(tripId: String): List<Stop> =
        getStopTimesForTrip(tripId).mapNotNull { stops[it.stopId] }

    /** Last error message when load failed. */
    var lastError: String? = null
        private set

    /**
     * Load from CSV files in assets. Expects: stops.txt, routes.txt, trips.txt, stop_times.txt
     * in assets/gtfs/
     */
    fun loadFromAssets(assetPath: String = "gtfs"): Boolean {
        lastError = null
        return try {
            context.assets.open("$assetPath/stops.txt").reader().buffered().use {
                loadStopsFromStream(it)
            }
            context.assets.open("$assetPath/routes.txt").reader().buffered().use {
                loadRoutesFromStream(it)
            }
            context.assets.open("$assetPath/trips.txt").reader().buffered().use {
                loadTripsFromStream(it)
            }
            context.assets.open("$assetPath/stop_times.txt").reader().buffered().use {
                loadStopTimesFromStream(it)
            }
            true
        } catch (e: Exception) {
            lastError = "${e.javaClass.simpleName}: ${e.message}"
            e.printStackTrace()
            false
        }
    }

    /**
     * Load from ETS GTFS zip URL. Call from a background thread.
     */
    fun loadFromUrl(url: String): Boolean {
        return try {
            val conn = URL(url).openConnection()
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            ZipInputStream(conn.getInputStream()).use { zipStream ->
                var entry = zipStream.nextEntry
                while (entry != null) {
                    val content = zipStream.readBytes().toString(Charsets.UTF_8)
                    val reader = BufferedReader(StringReader(content))
                    when (entry.name) {
                        "stops.txt" -> loadStopsFromStream(reader)
                        "routes.txt" -> loadRoutesFromStream(reader)
                        "trips.txt" -> loadTripsFromStream(reader)
                        "stop_times.txt" -> loadStopTimesFromStream(reader)
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun loadStopsFromStream(reader: BufferedReader) {
        reader.useLines { lines ->
            val lineList = lines.toList()
            val header = lineList.first()
            val idx = parseCsvHeader(header, "stop_id", "stop_name", "stop_lat", "stop_lon")
            lineList.drop(1).forEach { line ->
                val cols = parseCsvLine(line)
                if (cols.size >= 4) {
                    val id = cols.getOrElse(idx["stop_id"]!!) { "" }
                    val name = cols.getOrElse(idx["stop_name"]!!) { "" }
                    val lat = cols.getOrElse(idx["stop_lat"]!!) { "0" }.toDoubleOrNull() ?: 0.0
                    val lon = cols.getOrElse(idx["stop_lon"]!!) { "0" }.toDoubleOrNull() ?: 0.0
                    if (id.isNotBlank()) stops[id] = Stop(id, name, lat, lon)
                }
            }
        }
    }

    private fun loadRoutesFromStream(reader: BufferedReader) {
        reader.useLines { lines ->
            val lineList = lines.toList()
            val header = lineList.first()
            val idx = parseCsvHeader(header, "route_id", "route_short_name", "route_long_name")
            lineList.drop(1).forEach { line ->
                val cols = parseCsvLine(line)
                if (cols.size >= 3) {
                    val id = cols.getOrElse(idx["route_id"]!!) { "" }
                    val shortName = cols.getOrElse(idx["route_short_name"]!!) { "" }
                    val longName = cols.getOrElse(idx["route_long_name"]!!) { "" }
                    if (id.isNotBlank()) routes[id] = Route(id, shortName, longName)
                }
            }
        }
    }

    private fun loadTripsFromStream(reader: BufferedReader) {
        reader.useLines { lines ->
            val lineList = lines.toList()
            val header = lineList.first()
            val idx = parseCsvHeader(header, "trip_id", "route_id", "service_id")
            lineList.drop(1).forEach { line ->
                val cols = parseCsvLine(line)
                if (cols.size >= 3) {
                    val id = cols.getOrElse(idx["trip_id"]!!) { "" }
                    val routeId = cols.getOrElse(idx["route_id"]!!) { "" }
                    val serviceId = cols.getOrElse(idx["service_id"]!!) { "" }
                    if (id.isNotBlank()) trips[id] = Trip(id, routeId, serviceId)
                }
            }
        }
    }

    private fun loadStopTimesFromStream(reader: BufferedReader) {
        reader.useLines { lines ->
            val lineList = lines.toList()
            val header = lineList.first()
            val idx = parseCsvHeader(header, "trip_id", "stop_id", "arrival_time", "departure_time", "stop_sequence")
            lineList.drop(1).forEach { line ->
                val cols = parseCsvLine(line)
                if (cols.size >= 5) {
                    val tripId = cols.getOrElse(idx["trip_id"]!!) { "" }
                    val stopId = cols.getOrElse(idx["stop_id"]!!) { "" }
                    val arrival = cols.getOrElse(idx["arrival_time"]!!) { "" }.takeIf { it.isNotBlank() }
                    val departure = cols.getOrElse(idx["departure_time"]!!) { "" }.takeIf { it.isNotBlank() }
                    val seq = cols.getOrElse(idx["stop_sequence"]!!) { "0" }.toIntOrNull() ?: 0
                    if (tripId.isNotBlank() && stopId.isNotBlank()) {
                        stopTimesByTrip.getOrPut(tripId) { mutableListOf() }.add(
                            StopTime(tripId, stopId, arrival, departure, seq)
                        )
                    }
                }
            }
        }
    }

    private fun parseCsvHeader(header: String, vararg names: String): Map<String, Int> {
        val cols = parseCsvLine(header.trimStart('\uFEFF'))
        return names.mapNotNull { name ->
            val i = cols.indexOf(name)
            if (i >= 0) name to i else null
        }.toMap()
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                (c == ',' && !inQuotes) -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(c)
            }
        }
        result.add(current.toString().trim())
        return result
    }
}
