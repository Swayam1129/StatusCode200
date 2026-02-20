package com.example.accessu.transit

import com.google.transit.realtime.GtfsRealtime
import java.net.URL
import java.util.concurrent.Executors

object EtsRealtimeUrls {
    const val VEHICLE_POSITIONS =
        "http://gtfs.edmonton.ca/TMGTFSRealTimeWebService/Vehicle/VehiclePositions.pb"
    const val TRIP_UPDATES =
        "http://gtfs.edmonton.ca/TMGTFSRealTimeWebService/TripUpdate/TripUpdates.pb"
}

/**
 * Fetches and parses ETS GTFS Realtime feeds.
 * Call fetchVehiclePositions() and fetchTripUpdates() from a background thread/coroutine.
 */
class EtsRealtimeService {

    private val executor = Executors.newSingleThreadExecutor()

    fun fetchVehiclePositions(callback: (Result<GtfsRealtime.FeedMessage>) -> Unit) {
        executor.execute {
            try {
                val feed = GtfsRealtime.FeedMessage.parseFrom(
                    URL(EtsRealtimeUrls.VEHICLE_POSITIONS).openStream()
                )
                callback(Result.success(feed))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    fun fetchTripUpdates(callback: (Result<GtfsRealtime.FeedMessage>) -> Unit) {
        executor.execute {
            try {
                val feed = GtfsRealtime.FeedMessage.parseFrom(
                    URL(EtsRealtimeUrls.TRIP_UPDATES).openStream()
                )
                callback(Result.success(feed))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    /**
     * Get current stop and next stop for a trip from Trip Updates.
     * Returns Pair(currentStopId or null, nextStopId or null).
     */
    fun getCurrentAndNextStop(
        tripUpdates: GtfsRealtime.FeedMessage,
        tripId: String
    ): Pair<String?, String?> {
        for (entity in tripUpdates.entityList) {
            if (!entity.hasTripUpdate()) continue
            val tu = entity.tripUpdate
            if (tu.trip.tripId != tripId) continue

            val stopTimes = tu.stopTimeUpdateList.sortedBy { it.stopSequence }
            if (stopTimes.isEmpty()) return null to null

            val now = System.currentTimeMillis() / 1000
            var lastDepartedIdx = -1
            for (i in stopTimes.indices) {
                val stu = stopTimes[i]
                val dep = if (stu.hasDeparture()) stu.departure else null
                val depTime = dep?.takeIf { it.hasTime() }?.time ?: continue
                if (now >= depTime) lastDepartedIdx = i
            }

            val currentId = stopTimes.getOrNull(lastDepartedIdx)?.stopId
            val nextId = stopTimes.getOrNull(lastDepartedIdx + 1)?.stopId
            return currentId to nextId
        }
        return null to null
    }

    /**
     * Find trip_id for a route that is currently running (from Vehicle Positions).
     */
    fun findActiveTripForRoute(
        vehiclePositions: GtfsRealtime.FeedMessage,
        routeId: String
    ): String? {
        for (entity in vehiclePositions.entityList) {
            if (!entity.hasVehicle()) continue
            val vp = entity.vehicle
            if (!vp.hasTrip()) continue
            val trip = vp.trip
            if (trip.routeId == routeId) return trip.tripId
        }
        return null
    }
}
