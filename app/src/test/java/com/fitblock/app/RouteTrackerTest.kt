package com.fitblock.app

import org.junit.Assert.*
import org.junit.Test

class RouteTrackerTest {
    private fun point(lat: Double = 19.4326, lon: Double = -99.1332, at: Long = 1000, accuracy: Float = 5f) =
        RoutePoint(lat, lon, at, accuracy)

    @Test fun walkingAddsDistanceWhileStationaryNoiseDoesNot() {
        val tracker = RouteTracker()
        assertTrue(tracker.add(point(), 1000))
        assertFalse(tracker.add(point(lat = 19.432601, at = 4000), 4000))
        assertTrue(tracker.add(point(lat = 19.43265, at = 7000), 7000))
        assertEquals(5.56, tracker.distanceMeters, .1)
        assertEquals(2, tracker.points.size)
    }

    @Test fun rejectsStaleInaccurateImpossibleAndNonFiniteFixes() {
        val tracker = RouteTracker()
        assertFalse(tracker.add(point(at = 1000), 20000))
        assertFalse(tracker.add(point(accuracy = 80f), 1000))
        assertFalse(tracker.add(point(lat = Double.NaN), 1000))
        assertTrue(tracker.add(point(), 1000))
        assertFalse(tracker.add(point(lat = 20.0, at = 4000), 4000))
        assertFalse(tracker.add(point(lat = 19.4327, at = 500), 4000))
        assertEquals(0.0, tracker.distanceMeters, .001)
    }

    @Test fun pausesAndSignalLossCreateSegmentsWithoutAddingDistance() {
        val tracker = RouteTracker()
        tracker.add(point(), 1000)
        tracker.add(point(lat = 19.43265, at = 7000), 7000)
        val resumed = RouteTracker(tracker.points, tracker.distanceMeters)
        assertTrue(resumed.add(point(lat = 19.44, at = 10000), 10000))
        assertEquals(tracker.distanceMeters, resumed.distanceMeters, .001)
        assertNotEquals(resumed.points[0].segment, resumed.points.last().segment)
        resumed.add(point(lat = 19.45, at = 60000), 60000)
        assertEquals(tracker.distanceMeters, resumed.distanceMeters, .001)
    }

    @Test fun datelineDistanceUsesShortArc() {
        assertTrue(RouteTracker.meters(point(lat = 0.0, lon = 179.9999), point(lat = 0.0, lon = -179.9999)) < 23)
    }
}
