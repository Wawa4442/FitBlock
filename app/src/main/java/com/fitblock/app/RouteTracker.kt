package com.fitblock.app

import kotlin.math.*

data class RoutePoint(val latitude: Double, val longitude: Double, val at: Long,
                      val accuracy: Float, val segment: Int = 0)

/** Pedestrian GPS filter. GPS never grants steps or exercise rewards. */
class RouteTracker(initial: List<RoutePoint> = emptyList(), initialDistance: Double = 0.0) {
    private val accepted = initial.toMutableList()
    val points: List<RoutePoint> get() = accepted.toList()
    var distanceMeters = initialDistance
        private set
    private var segment = (initial.lastOrNull()?.segment ?: -1) + 1
    private var firstInSegment = true

    fun add(point: RoutePoint, now: Long): Boolean {
        if (!point.latitude.isFinite() || !point.longitude.isFinite() ||
            point.latitude !in -85.0..85.0 || point.longitude !in -180.0..180.0 ||
            !point.accuracy.isFinite() || point.accuracy !in 0.1f..35f ||
            point.at > now + 1000 || now - point.at > 15_000 || accepted.size >= 6000) return false
        val previous = accepted.lastOrNull()
        val gap = if (previous == null) Long.MAX_VALUE else point.at - previous.at
        if (!firstInSegment && gap < 2000) return false
        var distance = 0.0
        if (!firstInSegment && previous != null) {
            val delta = meters(previous, point)
            // A lost GPS signal creates a gap, never a straight line across missing data.
            if (gap > 30_000) {
                segment++
            } else {
                if (delta < maxOf(4.0, (point.accuracy + previous.accuracy) * .25)) return false
                if (delta / (gap / 1000.0) > 5.0) return false
                distance = delta
            }
        }
        accepted.add(point.copy(segment = segment))
        distanceMeters += distance
        firstInSegment = false
        return true
    }

    companion object {
        fun meters(a: RoutePoint, b: RoutePoint): Double {
            val lat = Math.toRadians(b.latitude - a.latitude)
            val lon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
                cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
            return 6_371_000 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }
    }
}

fun activityTime(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return "%d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60)
}

fun distanceText(meters: Double) = if (meters < 1000) "${meters.toInt()} m" else
    "%.2f km".format(java.util.Locale.forLanguageTag("es-MX"), meters / 1000)
