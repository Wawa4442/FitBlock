package com.fitblock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ActivityTrackingTest {
    private val closed = PoseSample(mapOf(
        11 to Joint(.32f, .30f), 12 to Joint(.52f, .30f),
        13 to Joint(.32f, .39f), 14 to Joint(.52f, .39f),
        15 to Joint(.32f, .51f), 16 to Joint(.52f, .51f),
        23 to Joint(.35f, .51f), 24 to Joint(.49f, .51f),
        27 to Joint(.37f, .91f), 28 to Joint(.47f, .91f)
    ), imageWidth = .75f)
    private val open = closed.copy(joints = closed.joints + mapOf(
        15 to Joint(.20f, .23f, .56f), 16 to Joint(.63f, .23f, .60f),
        27 to Joint(.24f, .89f), 28 to Joint(.60f, .89f)
    ))

    @Test fun continuousJacksCountWithBriefEndpointsAndMissingFrame() {
        val session = PoseSession(Exercise.JACK, emptyList())
        session.frame(closed, 0)
        for (rep in 0 until 15) {
            val start = rep * 680L
            session.frame(open, start + 320)
            session.frame(PoseSample(emptyMap()), start + 440)
            session.frame(closed, start + 680)
        }
        assertEquals(15, session.count)
    }

    @Test fun armsOnlyOrOneMissingFootCannotCompleteJack() {
        val session = PoseSession(Exercise.JACK, emptyList())
        session.frame(closed, 0)
        val armsOnly = open.copy(joints = open.joints + mapOf(27 to closed[27], 28 to closed[28]))
        session.frame(armsOnly, 320)
        session.frame(closed, 640)
        session.frame(open.copy(joints = open.joints + (28 to Joint(.90f, .89f))), 940)
        session.frame(closed, 1240)
        assertEquals(0, session.count)
    }

    @Test fun livenessChallengePrecedesCounting() {
        val session = PoseSession(Exercise.JACK, listOf(0))
        for (time in 0L..1400 step 100) session.frame(if (time in 300..500) open else closed, time)
        assertEquals(0, session.count)
        val leftUp = closed.copy(joints = closed.joints + (15 to Joint(.32f, .12f)))
        for (time in 1500L..2000 step 100) session.frame(leftUp, time)
        for (time in 2100L..2400 step 100) session.frame(closed, time)
        session.frame(closed, 2500)
        session.frame(open, 2800)
        session.frame(closed, 3100)
        assertEquals(1, session.count)
    }

    @Test fun stretchCountsOnlyObservedTimeInPosition() {
        val session = PoseSession(Exercise.STRETCH, emptyList())
        val stretched = closed.copy(joints = closed.joints + mapOf(
            13 to Joint(.32f, .22f), 14 to Joint(.52f, .22f),
            15 to Joint(.32f, .08f), 16 to Joint(.52f, .08f)
        ))
        for (time in 0L..1700 step 100) session.frame(stretched, time)
        for (time in 3500L..3900 step 100) session.frame(stretched, time)
        assertEquals(2, session.count)
    }

    @Test fun cumulativePedometerAcceptsDelayedBatchesAndIgnoresDuplicateTotals() {
        val tracker = WalkingTracker(StepSource.COUNTER, 1000)
        tracker.counter(400, 4242) // last step before the app opened
        for (at in 1000L..5200 step 100) tracker.acceleration(at, 0f, 10.7f, 0f)
        tracker.counter(5000, 4249)
        tracker.counter(5000, 4249)
        tracker.settle(5650)
        assertEquals(7, tracker.steps)
        tracker.counter(5500, 4300)
        tracker.settle(7000)
        assertEquals(7, tracker.steps)
    }

    @Test fun sensorEventsNeedWalkingMotionButAcceptAStepAfterAStop() {
        val tracker = WalkingTracker(StepSource.DETECTOR, 0)
        tracker.detected(1000)
        tracker.settle(1500)
        assertEquals(0, tracker.steps)
        tracker.acceleration(4840, 0f, 11.3f, 0f)
        tracker.detected(5000)
        tracker.detected(5090)
        tracker.settle(5500)
        assertEquals(1, tracker.steps)
    }

    @Test fun accelerometerFallbackCountsRhythmAndRejectsHardShaking() {
        val tracker = WalkingTracker(StepSource.ACCELEROMETER, 0)
        for (at in 0L until 8000 step 40) {
            val signal = (1.75 * sin(2 * PI * at / 800)).toFloat()
            tracker.acceleration(at, 0f, 9.81f + signal, 0f)
        }
        assertEquals(10, tracker.steps)
        for (at in 8000L..11000 step 40) tracker.acceleration(at, 0f, if (at % 80 == 0L) 32f else 2f, 0f)
        assertEquals(10, tracker.steps)
        assertTrue(Exercise.WALK.goalRange.contains(100))
    }
}
