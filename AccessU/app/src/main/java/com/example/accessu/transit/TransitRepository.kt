package com.example.accessu.transit

import android.content.Context

/**
 * Central access to transit data. Use this from the app (Niharika's voice layer, Swayam's on-bus logic).
 */
class TransitRepository(context: Context) {

    private val loader = GtfsStaticLoader(context)
    private var loaded = false

    fun loadGtfsFromAssets(): Boolean {
        loaded = loader.loadFromAssets()
        return loaded
    }

    fun getLastLoadError(): String? = loader.lastError

    fun isLoaded(): Boolean = loaded

    fun getStops(): Map<String, Stop> = loader.getStops()
    fun getRoutes(): Map<String, Route> = loader.getRoutes()
    fun getTrips(): Map<String, Trip> = loader.getTrips()
    fun getOrderedStopsForTrip(tripId: String): List<Stop> = loader.getOrderedStopsForTrip(tripId)

    fun getStopById(id: String): Stop? = loader.getStops()[id]
    fun getRouteById(id: String): Route? = loader.getRoutes()[id]
    fun getTripsForRoute(routeId: String): List<Trip> =
        loader.getTrips().values.filter { it.routeId == routeId }
}
