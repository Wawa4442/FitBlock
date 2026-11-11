package com.fitblock.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AccountsAndHistoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = Store.get(context)
    private val accounts get() = Accounts.get(context)

    @Before fun reset() { store.clear(); store.consent = true }
    @After fun clear() { store.clear() }

    private fun create(email: String) = accounts.register("Persona de prueba", email, "Example-1234".toCharArray())

    @Test fun accountCreationAuthenticationAndHistoryAreIsolated() {
        val a = create("A@example.test")
        accounts.activate(a)
        assertTrue(store.recordSport("a-record", SportCatalog.get("yoga")!!, 60_000, 60_000))
        store.setFavoriteSport("yoga", true)
        accounts.logout()
        val b = create("b@example.test")
        accounts.activate(b)
        assertEquals(0, store.records().length())
        assertTrue(store.favoriteSports().isEmpty())
        accounts.logout()
        accounts.activate(accounts.authenticate("a@EXAMPLE.test", "Example-1234".toCharArray()))
        assertEquals(a.id, accounts.current()!!.id)
        assertEquals(1, store.records().length())
        assertEquals(setOf("yoga"), store.favoriteSports())
        assertEquals(1, store.totalActivities())
    }

    @Test fun credentialsAreHashedAndDuplicateOrWrongCredentialsFail() {
        create("same@example.test")
        val saved = context.getSharedPreferences("fitblock_accounts", 0).getString("users", "")!!
        assertFalse(saved.contains("Example-1234"))
        assertTrue(saved.contains("salt"))
        assertTrue(saved.contains("hash"))
        try { create("SAME@example.test"); fail("Duplicate email must fail") } catch (_: AccountException) { }
        try { accounts.authenticate("same@example.test", "incorrect".toCharArray()); fail("Wrong password must fail") } catch (_: AccountException) { }
        assertNull(accounts.current())
    }

    @Test fun firstAccountClaimsGuestHistoryOnlyOnce() {
        assertTrue(store.recordSport("guest-record", SportCatalog.get("tennis")!!, 60_000, 60_000))
        store.setSportGoal("tennis", 23)
        val first = create("first@example.test")
        accounts.activate(first)
        assertEquals(1, store.records().length())
        assertEquals(23, store.sportGoal("tennis"))
        accounts.logout()
        assertEquals(0, store.records().length())
        val second = create("second@example.test")
        accounts.activate(second)
        assertEquals(0, store.records().length())
        assertEquals(10, store.sportGoal("tennis"))
    }

    @Test fun streaksKeepDetailedHistoryBeyondAggregateWindowAndBadgesRemainUnlocked() {
        val today = LocalDate.now()
        val history = JSONArray()
        // The oldest run is no longer in the rolling 90-day aggregates.
        for (offset in 100L..106L) history.put(JSONObject()
            .put("id", "old-$offset").put("sport", "yoga").put("date", today.minusDays(offset).toString())
            .put("goalMet", true).put("verified", false))
        for (offset in 0L..2L) history.put(JSONObject()
            .put("id", "recent-$offset").put("sport", "cycling").put("date", today.minusDays(offset).toString())
            .put("goalMet", true).put("verified", false))
        // A partial entry on the gap day does not extend a streak.
        history.put(JSONObject().put("id", "partial").put("sport", "golf")
            .put("date", today.minusDays(3).toString()).put("goalMet", false).put("verified", false))
        store.profilePreferences().edit().putString("records", history.toString()).commit()
        assertEquals(3, store.streak())
        assertEquals(7, store.bestStreak())
        assertEquals(10, store.totalActivities())
        assertTrue("week" in AwardCatalog.refresh(store))
        assertFalse("explorer" in AwardCatalog.refresh(store))
        // Two days without activity resets the current run, while the best and badge survive.
        val old = JSONArray()
        for (index in 0..6) old.put(history.getJSONObject(index))
        store.profilePreferences().edit().putString("records", old.toString()).commit()
        assertEquals(0, store.streak())
        assertEquals(7, store.bestStreak())
        assertTrue(AwardCatalog.claim(store, "week"))
    }

    @Test fun manualRecordsAreIdempotentAndDoNotCreateUnlockCredit() {
        val sport = SportCatalog.get("cycling")!!
        assertTrue(store.recordSport("one-record", sport, 65_000, 60_000))
        assertTrue(store.recordSport("one-record", sport, 65_000, 60_000))
        assertEquals(1, store.records().length())
        assertEquals(1, store.streak())
        assertEquals(0L, store.balance())
        assertTrue(AwardCatalog.claim(store, "first"))
        assertTrue(AwardCatalog.claim(store, "first"))
        assertEquals(setOf("first"), AwardCatalog.claimed(store))
        assertFalse(AwardCatalog.claim(store, "month"))
    }

    @Test fun partialRecordsDoNotCreateStreakAndActiveSessionsPreventAccountChanges() {
        store.recordSport("partial", SportCatalog.get("golf")!!, 20_000, 60_000)
        assertEquals(0, store.streak())
        val account = create("timer@example.test")
        SportTimer.get(context).begin(SportCatalog.get("golf")!!, 10)
        try { accounts.activate(account); fail("Active session must keep its owner") } catch (_: AccountException) { }
        SportTimer.get(context).discard()
        accounts.activate(account)
        val ticket = store.startWorkout(Exercise.SQUAT)
        try { accounts.logout(); fail("Verified session must keep its owner") } catch (_: AccountException) { }
        store.cancelWorkout(ticket.id)
        accounts.logout()
        assertNull(accounts.current())
    }
}
