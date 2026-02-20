package com.example.accessu.transit

import com.google.transit.realtime.GtfsRealtime

/**
 * Monitors user's bus trip and produces "your stop next" / "current stop" announcements.
 * Call setOnBus(routeShortName, myStopName) then poll getNextAnnouncement() every 15–30 sec.
 */
class OnBusMonitor(
    private val transitRepo: TransitRepository,
    private val realtimeService: EtsRealtimeService
) {

    private var routeShortName: String? = null
    private var myStopId: String? = null
    private var lastAnnouncement: String? = null
    private var lastAnnouncementTime = 0L
    private val throttleMs = 20_000L // Don't repeat same announcement within 20 sec

    /**
     * Set when user says "I'm on the bus, route 4, my stop is Southgate".
     */
    fun setOnBus(routeShortName: String, myStopName: String) {
        this.routeShortName = routeShortName
        val route = transitRepo.getRoutes().values.find {
            it.shortName == routeShortName
        }
        val stop = transitRepo.getStops().values.find {
            it.name.contains(myStopName, ignoreCase = true)
        }
        myStopId = stop?.id
    }

    fun isOnBus(): Boolean = routeShortName != null && myStopId != null

    fun clearOnBus() {
        routeShortName = null
        myStopId = null
        lastAnnouncement = null
    }

    /**
     * Call every 15–30 seconds. Returns announcement text or null.
     */
    fun getNextAnnouncement(callback: (String?) -> Unit) {
        val rsn = routeShortName ?: return callback(null)
        val stopId = myStopId ?: return callback(null)

        realtimeService.fetchVehiclePositions { vpResult ->
            vpResult.getOrNull() ?: return@fetchVehiclePositions callback(null)
            val vp = vpResult.getOrNull()!!

            val route = transitRepo.getRoutes().values.find { it.shortName == rsn }
                ?: return@fetchVehiclePositions callback(null)
            val tripId = realtimeService.findActiveTripForRoute(vp, route.id)
                ?: return@fetchVehiclePositions callback(null)

            realtimeService.fetchTripUpdates { tuResult ->
                val tu = tuResult.getOrNull() ?: return@fetchTripUpdates callback(null)
                val (currentStopId, nextStopId) =
                    realtimeService.getCurrentAndNextStop(tu, tripId)

                val ordered = transitRepo.getOrderedStopsForTrip(tripId)
                val currentStop = currentStopId?.let { transitRepo.getStopById(it) }
                val nextStop = nextStopId?.let { transitRepo.getStopById(it) }
                val myStop = transitRepo.getStopById(stopId)
                val myIdx = ordered.indexOfFirst { it.id == stopId }
                val nextIdx = nextStopId?.let { ordered.indexOfFirst { s -> s.id == it } }

                val msg = when {
                    nextStopId == stopId ->
                        "Your stop is next. Get ready to exit at ${myStop?.name ?: "your stop"}."
                    currentStop != null && myIdx >= 0 && nextIdx != null && nextIdx >= 0 -> {
                        val n = myIdx - nextIdx
                        if (n > 0) "Current stop: ${currentStop.name}. Your stop in $n stops."
                        else "Current stop: ${currentStop.name}. Your stop (${myStop?.name}) in ${-n} stops."
                    }
                    currentStop != null ->
                        "Current stop: ${currentStop.name}."
                    else -> null
                }

                if (msg != null && (msg != lastAnnouncement || System.currentTimeMillis() - lastAnnouncementTime > throttleMs)) {
                    lastAnnouncement = msg
                    lastAnnouncementTime = System.currentTimeMillis()
                    callback(msg)
                } else {
                    callback(null)
                }
            }
        }
    }
}
