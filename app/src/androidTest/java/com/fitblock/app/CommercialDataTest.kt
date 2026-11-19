package com.fitblock.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CommercialDataTest {
    private lateinit var store: Store
    private val today get() = LocalDate.now().toString()
    @Before fun prepare() {
        store = Store.get(InstrumentationRegistry.getInstrumentation().targetContext)
        WalkService.discard(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clear(); store.consent = true
    }
    @After fun cleanup() {
        WalkService.discard(InstrumentationRegistry.getInstrumentation().targetContext)
        store.clear()
    }
    private fun walk(steps: Int = 40): WalkProgress {
        val ticket = store.startWorkout(Exercise.WALK)
        val route = listOf(RoutePoint(19.4326, -99.1332, 1000, 5f), RoutePoint(19.4328, -99.1332, 20000, 5f))
        return WalkProgress(ticket, steps, WalkPhase.PAUSED, "QA", 60000, true, route, 22.2,
            mapOf(today to WalkDay(steps, 60000, 22.2)))
    }
    @Test fun checkpointRecoveryAndPartialSaveDoNotDuplicateDailyData() {
        val walk = walk()
        assertTrue(WalkPersistence.save(store, walk))
        assertEquals(40, store.dayStats().optInt("steps"))
        assertEquals(60000L, store.dayStats().optLong("walkMs"))
        val restored = WalkPersistence.read(store)!!
        assertEquals(walk.ticket, restored.ticket)
        assertEquals(walk.route, restored.route)
        assertEquals(WalkPhase.PAUSED, restored.phase)
        assertTrue(store.savePartialWalk(restored))
        assertFalse(store.savePartialWalk(restored))
        assertNull(WalkPersistence.read(store))
        assertEquals(40, store.dayStats().optInt("steps"))
        assertEquals(0L, store.balance())
        assertEquals(0, store.dayStats().optInt("sessions"))
        assertEquals(1, store.records().length())
    }
    @Test fun completedWalkSavesRewardRouteAndDailyTotalsExactlyOnce() {
        val walk = walk(100)
        WalkPersistence.save(store, walk)
        assertTrue(store.completeWorkout(walk.ticket.id, 100, walk))
        assertFalse(store.completeWorkout(walk.ticket.id, 100, walk))
        assertEquals(100, store.dayStats().optInt("steps"))
        assertEquals(600000L, store.balance())
        assertEquals(1, store.dayStats().optInt("sessions"))
        assertEquals(60000, store.dayStats().optLong("walkMs").toInt())
        assertNull(WalkPersistence.read(store))
        assertEquals(2, store.records().getJSONObject(0).getJSONArray("route").length())
        assertTrue(store.export().contains("route"))
        assertFalse(store.export().contains("password"))
        store.deleteRoute(walk.ticket.id)
        assertFalse(store.records().getJSONObject(0).has("route"))
        assertEquals(100, store.dayStats().optInt("steps"))
    }
    @Test fun overnightWalkSplitsDailyMetricsAndAggregatesThePeriod() {
        val previous = LocalDate.now().minusDays(1)
        val walk = walk(80).copy(days = mapOf(previous.toString() to WalkDay(30, 20000, 10.0),
            today to WalkDay(50, 40000, 12.2)))
        assertTrue(store.savePartialWalk(walk))
        assertEquals(30, store.dayStats(previous.toString()).optInt("steps"))
        assertEquals(50, store.dayStats(today).optInt("steps"))
        val period = store.periodStats(previous, LocalDate.now())
        assertEquals(80, period.optInt("steps"))
        assertEquals(2, period.optInt("activeDays"))
        assertEquals(22.2, period.optDouble("distanceMeters"), .001)
        assertEquals(80L, store.totalAmount("steps"))
    }
    @Test fun malformedTotalsAndWrongOwnerCannotSaveOrGrantRewards() {
        val walk = walk(100)
        assertFalse(store.completeWorkout(walk.ticket.id, 100, walk.copy(days = emptyMap())))
        assertFalse(store.savePartialWalk(walk.copy(ticket = walk.ticket.copy(owner = "other"))))
        assertEquals(0L, store.balance())
    }
}
