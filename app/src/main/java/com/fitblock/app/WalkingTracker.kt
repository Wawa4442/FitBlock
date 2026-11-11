package com.fitblock.app

import kotlin.math.abs
import kotlin.math.sqrt

enum class StepSource { COUNTER, DETECTOR, ACCELEROMETER }

/** Combines the platform pedometer with motion, or recognizes walking cadence on its own. */
class WalkingTracker(private val source: StepSource, private val startedAt: Long) {
    private data class StepBatch(val from: Long, val to: Long, val count: Int)

    private val motion = ArrayDeque<Long>()
    private val sharpMotion = ArrayDeque<Long>()
    private val pending = ArrayDeque<StepBatch>()
    private var lastSample = -1L
    private var lastHardwareAt = startedAt
    private var lastTotal: Int? = null
    private var filtered = 0f
    private var peakAt = -1L
    private var lastStride = -1L
    private var previousInterval = 0L
    private var cadenceRun = 0
    var steps = 0
        private set

    fun acceleration(at: Long, x: Float, y: Float, z: Float) {
        if (at < startedAt || at <= lastSample) return
        if (lastSample >= 0 && at - lastSample > 650) {
            filtered = 0f
            resetCadence()
        }
        lastSample = at
        val deviation = sqrt(x * x + y * y + z * z) - 9.81f
        if (!deviation.isFinite()) return
        if (deviation > 17f || deviation < -8f) {
            rejectShake(at)
            return
        }
        filtered = filtered * .55f + deviation * .45f
        if (abs(filtered) >= .22f && (motion.lastOrNull()?.let { at - it >= 150 } != false)) {
            motion.addLast(at)
        }
        trimHistory(at)
        if (source != StepSource.ACCELEROMETER || at - (sharpMotion.lastOrNull() ?: -10_000L) < 650) return
        if (peakAt >= 0 && at - peakAt > 480) peakAt = -1
        if (peakAt < 0 && filtered >= .48f) peakAt = at
        if (peakAt >= 0 && at - peakAt in 75..480 && filtered <= -.16f) {
            recordStride(peakAt)
            peakAt = -1
        }
    }

    fun rotation(at: Long, x: Float, y: Float, z: Float) {
        if (at >= startedAt && x * x + y * y + z * z > 64f) rejectShake(at)
    }

    fun counter(at: Long, total: Int) {
        if (source != StepSource.COUNTER || total < 0) return
        val previous = lastTotal
        if (previous == null) {
            lastTotal = total
            lastHardwareAt = maxOf(at, startedAt)
            return
        }
        if (at < lastHardwareAt) return
        if (previous == total) return
        val from = lastHardwareAt
        lastTotal = total
        lastHardwareAt = at
        if (total < previous) return
        val count = total - previous
        // The cumulative sensor may report a whole batch, with the timestamp of its last step.
        val plausible = ((at - from) / Exercise.WALK.minimumUnitMillis + 1).coerceAtMost(240)
        if (count.toLong() <= plausible) pending.addLast(StepBatch(from, at, count))
    }

    fun detected(at: Long) {
        if (source != StepSource.DETECTOR || at < startedAt) return
        if (at - lastHardwareAt < Exercise.WALK.minimumUnitMillis) return
        lastHardwareAt = at
        pending.addLast(StepBatch(at - 450, at, 1))
    }

    fun settle(now: Long) {
        while (pending.firstOrNull()?.let { now - it.to >= 450 } == true) {
            val batch = pending.removeFirst()
            val interval = maxOf(startedAt, batch.from - 220)..(batch.to + 350)
            val support = motion.count { it in interval }
            val shocks = sharpMotion.count { it in interval }
            if (support >= (batch.count + 2) / 3 && shocks <= if (batch.count < 4) 0 else 1) {
                steps += batch.count
            }
        }
        trimHistory(now)
    }

    private fun recordStride(at: Long) {
        val interval = if (lastStride < 0) 0 else at - lastStride
        if (interval in 1 until Exercise.WALK.minimumUnitMillis) return
        val regular = previousInterval == 0L || abs(interval - previousInterval) <= maxOf(350L, previousInterval / 2)
        if (interval !in Exercise.WALK.minimumUnitMillis..1900 || !regular) {
            cadenceRun = 1
            previousInterval = 0
        } else {
            cadenceRun++
            previousInterval = interval
            if (cadenceRun == 3) steps += 3
            if (cadenceRun > 3) steps++
        }
        lastStride = at
    }

    private fun rejectShake(at: Long) {
        if (sharpMotion.lastOrNull()?.let { at - it >= 130 } != false) sharpMotion.addLast(at)
        trimHistory(at)
        filtered = 0f
        resetCadence()
    }

    private fun resetCadence() {
        peakAt = -1
        lastStride = -1
        previousInterval = 0
        cadenceRun = 0
    }

    private fun trimHistory(now: Long) {
        while (motion.firstOrNull()?.let { now - it > 60_000 } == true) motion.removeFirst()
        while (sharpMotion.firstOrNull()?.let { now - it > 60_000 } == true) sharpMotion.removeFirst()
    }
}
