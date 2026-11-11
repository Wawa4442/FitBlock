package com.fitblock.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StoreDeviceTest {
    private lateinit var store:Store
    @Before fun prepare() { store=Store.get(InstrumentationRegistry.getInstrumentation().targetContext);store.clear();store.consent=true }
    @After fun cleanup() { store.clear() }
    @Test fun upgradedGoalsRespectMinimumAndActiveTicketKeepsItsSize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("fitblock_private", 0).edit().putInt("goal_WALK", 20).commit()
        assertEquals(100, store.baseGoal(Exercise.WALK))
        store.setGoal(Exercise.WALK, 180)
        val ticket = store.startWorkout(Exercise.WALK)
        store.setGoal(Exercise.WALK, 110)
        assertEquals(180, ticket.goal)
        assertEquals(110, store.baseGoal(Exercise.WALK))
    }
    @Test fun cannotMintRewardEarlyOrWithWrongTicket() {
        val t=store.startWorkout(Exercise.PUSHUP)
        assertFalse(store.completeWorkout(t.id,t.goal));assertFalse(store.completeWorkout("forged",100));assertEquals(0L,store.balance())
    }
    @Test fun completedSessionMintsExactlyOnce() {
        val t=store.startWorkout(Exercise.PUSHUP)
        Thread.sleep(Exercise.PUSHUP.base * Exercise.PUSHUP.minimumUnitMillis + 100)
        assertTrue(store.completeWorkout(t.id,t.goal));assertEquals(600_000L,store.balance())
        assertFalse(store.completeWorkout(t.id,t.goal));assertEquals(1,store.dayStats().optInt("sessions"))
        assertEquals(10,store.dayStats().optInt("earned"))
    }
    @Test fun canceledAndSupersededSessionsCannotMint() {
        val old=store.startWorkout(Exercise.PUSHUP);store.startWorkout(Exercise.PUSHUP)
        Thread.sleep(Exercise.PUSHUP.base * Exercise.PUSHUP.minimumUnitMillis + 100);assertFalse(store.completeWorkout(old.id,old.goal))
        val fresh=store.startWorkout(Exercise.PUSHUP);store.cancelWorkout(fresh.id)
        Thread.sleep(Exercise.PUSHUP.base * Exercise.PUSHUP.minimumUnitMillis + 100);assertFalse(store.completeWorkout(fresh.id,fresh.goal))
    }
    @Test fun emergencyIsLimitedAndAddsDebt() {
        assertTrue(store.emergency());assertTrue(store.emergencyActive());assertFalse(store.emergency())
        assertEquals(15,store.requiredGoal(Exercise.SQUAT));assertEquals(1,store.dayStats().optInt("emergencies"))
    }
    @Test fun spendingStopsAtQuotaAndUnselectedAppsCannotSpend() {
        val t=store.startWorkout(Exercise.PUSHUP);Thread.sleep(Exercise.PUSHUP.base * Exercise.PUSHUP.minimumUnitMillis + 100);assertTrue(store.completeWorkout(t.id,t.goal))
        store.selected=setOf("test.selected");store.enabled=true;store.setAppLimit("test.selected",1)
        store.consume("test.other",2000);assertEquals(600_000L,store.balance())
        repeat(35){store.consume("test.selected",2000)}
        assertEquals(60_000L,store.used("test.selected"));assertEquals(540_000L,store.balance());assertTrue(store.blocked("test.selected"))
        assertFalse(store.blocked("test.other"))
    }
    @Test fun rebootInvalidatesPasesAndKeepsSelection() {
        store.selected=setOf("test.selected")
        val t=store.startWorkout(Exercise.PUSHUP);Thread.sleep(Exercise.PUSHUP.base * Exercise.PUSHUP.minimumUnitMillis + 100);assertTrue(store.completeWorkout(t.id,t.goal))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("fitblock_private",0).edit().putInt("boot",-999).commit()
        assertEquals(0L,store.balance());assertEquals(setOf("test.selected"),store.selected)
    }
    @Test fun perAppQuotaDoesNotTruncateEarnedSharedCredit() {
        store.dailyLimit=1;store.rewardMinutes=10
        val t=store.startWorkout(Exercise.PUSHUP);Thread.sleep(Exercise.PUSHUP.base * Exercise.PUSHUP.minimumUnitMillis + 100)
        assertTrue(store.completeWorkout(t.id,t.goal));assertEquals(600_000L,store.balance())
        assertEquals(10,store.dayStats().optInt("earned"))
    }
}

