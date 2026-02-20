package com.example.accessu.transit

/**
 * GTFS data models for ETS (Edmonton Transit).
 */

data class Stop(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
)

data class Route(
    val id: String,
    val shortName: String,
    val longName: String,
)

data class StopTime(
    val tripId: String,
    val stopId: String,
    val arrivalTime: String?,
    val departureTime: String?,
    val stopSequence: Int,
)

data class Trip(
    val id: String,
    val routeId: String,
    val serviceId: String,
)
