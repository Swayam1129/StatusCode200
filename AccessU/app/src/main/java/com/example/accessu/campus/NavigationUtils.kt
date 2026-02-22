package com.example.accessu.campus

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object NavigationUtils {

    /**
     * Bearing in degrees (0-360) from point A to point B.
     * 0=North, 90=East, 180=South, 270=West.
     */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δλ = Math.toRadians(lon2 - lon1)
        val y = sin(Δλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
        val θ = atan2(y, x)
        return (Math.toDegrees(θ) + 360) % 360
    }

    /**
     * Haversine distance in meters.
     */
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    /**
     * Angle difference in degrees (0-360): how far right the target bearing is from heading.
     * 0 = straight, 90 = right, 180 = behind, 270 = left.
     */
    fun angleDiffDegrees(heading: Double, bearing: Double): Double {
        return (bearing - heading + 360) % 360
    }

    /**
     * Returns turn direction string based on bearing to next node vs device heading.
     * headingDeg: device heading 0-360 (0=North)
     * bearingToNext: bearing from user to next node 0-360
     */
    fun getTurnDirection(headingDeg: Double?, bearingToNext: Double): String {
        if (headingDeg == null) return "head toward"
        val diff = angleDiffDegrees(headingDeg, bearingToNext)
        return when {
            diff <= 20 || diff >= 340 -> "continue straight"
            diff in 21.0..159.0 -> "turn right"
            diff in 160.0..200.0 -> "turn around"
            else -> "turn left"
        }
    }

    /**
     * Cardinal direction (N/NE/E/SE/S/SW/W/NW) from bearing.
     */
    fun bearingToCardinal(bearing: Double): String {
        val idx = ((bearing + 22.5) / 45).toInt() % 8
        return listOf("north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest")[idx]
    }

    /**
     * Human-readable distance for TTS.
     */
    fun formatDistanceMeters(meters: Double): String = when {
        meters < 15 -> "almost there"
        meters < 40 -> "about 25 meters"
        meters < 80 -> "about 50 meters"
        meters < 150 -> "about 100 meters"
        meters < 250 -> "about 200 meters"
        else -> "ahead"
    }
}
