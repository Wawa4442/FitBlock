package com.fitblock.app

import org.junit.Assert.*
import org.junit.Test

class PolicyTest {
    @Test fun fullDayAndNormalScheduleRespectSelectedDays() {
        assertTrue(Schedule().active(7,1439))
        val s=Schedule(9*60,17*60,setOf(1,2,3,4,5))
        assertFalse(s.active(1,539));assertTrue(s.active(1,540));assertFalse(s.active(1,1020));assertFalse(s.active(6,600))
    }
    @Test fun overnightScheduleBelongsToItsStartingDay() {
        val s=Schedule(22*60,6*60,setOf(7))
        assertTrue(s.active(7,1380));assertTrue(s.active(1,300));assertFalse(s.active(1,360));assertFalse(s.active(7,300));assertFalse(s.active(1,1380))
    }
    @Test fun expiryCannotBeBypassedWithUnusedBalance() {
        assertEquals(0L,Policy.remaining(60_000,10_000,10_000));assertEquals(60_000L,Policy.remaining(60_000,10_000,9999))
        assertEquals(0L,Policy.remaining(-1,10_000,0))
    }
    @Test fun selectedAppsNeedBothCreditAndDailyQuota() {
        assertTrue(Policy.blocked(true,true,true,0,0,60000,false))
        assertTrue(Policy.blocked(true,true,true,50000,60000,60000,false))
        assertFalse(Policy.blocked(true,true,true,50000,0,60000,false))
        assertFalse(Policy.blocked(true,false,true,0,0,60000,false))
        assertFalse(Policy.blocked(true,true,false,0,0,60000,false))
        assertFalse(Policy.blocked(false,true,true,0,0,60000,false))
        assertFalse(Policy.blocked(true,true,true,0,60000,60000,true))
    }
    @Test fun progressiveGoalHasACapAndEmergencyDebtPersistsInGoal() {
        assertEquals(10,Policy.goal(10,0,true,false));assertEquals(14,Policy.goal(10,100,true,false))
        assertEquals(21,Policy.goal(10,100,true,true));assertEquals(15,Policy.goal(10,100,false,true))
    }
    @Test fun incompleteAndRapidCyclesDoNotCount() {
        val gate = RepGate(340)
        assertFalse(gate.update(0, true, false, true))
        assertFalse(gate.update(80, false, true, true))
        assertFalse(gate.update(160, true, false, true))
        assertFalse(gate.update(350, false, true, true))
        assertTrue(gate.update(590, true, false, true))
        assertFalse(gate.update(800, true, false, true))
    }
    @Test fun trackingLossAndLongGapsInvalidatePartialCycles() {
        val gate = RepGate(340)
        gate.update(0, true, false, true)
        gate.update(260, false, true, true)
        assertFalse(gate.update(430, false, false, false))
        assertTrue(gate.update(550, true, false, true))
        gate.update(820, false, true, true)
        assertFalse(gate.update(1320, true, false, true))
    }
    @Test fun staticFramesCannotGenerateRepetitions() {
        val g=RepGate(340);for(t in 0L..10_000L step 100)assertFalse(g.update(t,true,false,true))
    }
    @Test fun geometryRejectsDegenerateAngles() {
        assertEquals(180.0,PoseValidator.angle(Joint(0f,0f),Joint(1f,0f),Joint(2f,0f)),.001)
        assertEquals(90.0,PoseValidator.angle(Joint(0f,0f),Joint(1f,0f),Joint(1f,1f)),.001)
        assertEquals(0.0,PoseValidator.angle(Joint(0f,0f),Joint(0f,0f),Joint(0f,1f)),.001)
    }
    @Test fun missingOrLowConfidenceLandmarksNeverQualify() {
        Exercise.entries.filter { it.camera }.forEach { assertFalse(PoseValidator.check(it,PoseSample(emptyMap())).valid) }
        assertFalse(PoseValidator.challenge(PoseSample(emptyMap()),0))
        assertFalse(PoseSample(mapOf(11 to Joint(.3f,.3f,.3f))).has(11))
    }
    @Test fun squatRequiresBentKneeThenFullExtension() {
        val standing=PoseSample(mapOf(11 to Joint(.3f,.2f),13 to Joint(.2f,.4f),15 to Joint(.2f,.6f),23 to Joint(.3f,.5f),25 to Joint(.3f,.7f),27 to Joint(.3f,.9f)))
        assertTrue(PoseValidator.check(Exercise.SQUAT,standing).neutral)
        assertFalse(PoseValidator.check(Exercise.SQUAT,standing).active)
        val down=PoseSample(standing.joints+mapOf(11 to Joint(.5f,.3f),23 to Joint(.5f,.65f),25 to Joint(.3f,.65f)))
        assertTrue(PoseValidator.check(Exercise.SQUAT,down).active)
        assertFalse(PoseValidator.check(Exercise.SQUAT,down).neutral)
    }
    @Test fun pushupNeedsStraightBodyAsWellAsArmMotion() {
        val top=PoseSample(mapOf(11 to Joint(.3f,.3f),13 to Joint(.3f,.5f),15 to Joint(.3f,.7f),23 to Joint(.55f,.3f),25 to Joint(.7f,.3f),27 to Joint(.9f,.3f)))
        assertTrue(PoseValidator.check(Exercise.PUSHUP,top).neutral)
        val bottom=PoseSample(top.joints+mapOf(13 to Joint(.5f,.3f),15 to Joint(.5f,.5f)))
        assertTrue(PoseValidator.check(Exercise.PUSHUP,bottom).active)
        assertFalse(PoseValidator.check(Exercise.PUSHUP,PoseSample(bottom.joints+mapOf(23 to Joint(.55f,.7f)))).valid)
    }
    @Test fun jacksAndStretchesUseBothArmsAndFullBody() {
        val closed=PoseSample(mapOf(0 to Joint(.5f,.2f),11 to Joint(.4f,.3f),12 to Joint(.6f,.3f),13 to Joint(.4f,.4f),14 to Joint(.6f,.4f),15 to Joint(.4f,.5f),16 to Joint(.6f,.5f),23 to Joint(.45f,.5f),24 to Joint(.55f,.5f),25 to Joint(.45f,.7f),26 to Joint(.55f,.7f),27 to Joint(.45f,.9f),28 to Joint(.55f,.9f)))
        assertTrue(PoseValidator.check(Exercise.JACK,closed).neutral)
        val open=PoseSample(closed.joints+mapOf(13 to Joint(.35f,.2f),14 to Joint(.65f,.2f),15 to Joint(.3f,.1f),16 to Joint(.7f,.1f),27 to Joint(.2f,.9f),28 to Joint(.8f,.9f)))
        assertTrue(PoseValidator.check(Exercise.JACK,open).active)
        assertTrue(PoseValidator.check(Exercise.STRETCH,open).active)
        assertFalse(PoseValidator.check(Exercise.STRETCH,PoseSample(open.joints+mapOf(16 to Joint(.7f,.5f)))).active)
    }
}
