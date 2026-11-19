package com.fitblock.app

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.exp

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
    private var gravity = 9.81f
    private var amplitude = .7f
    var steps = 0
        private set

    fun acceleration(at: Long, x: Float, y: Float, z: Float) {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
        if (at < startedAt || at <= lastSample) return
        val delta = if (lastSample < 0) 30L else (at - lastSample).coerceAtMost(650)
        if (lastSample >= 0 && at - lastSample > 650) {
            filtered = 0f
            resetCadence()
        }
        lastSample = at
        val magnitude = sqrt(x * x + y * y + z * z)
        val deviation = magnitude - gravity
        if (!deviation.isFinite()) return
        if (deviation > 17f || deviation < -8f) {
            rejectShake(at)
            return
        }
        // Time-based filters remain stable across 20–100 Hz sensors and batching.
        val smoothing = exp(-delta / 65.0).toFloat()
        val baseline = exp(-delta / 5000.0).toFloat()
        gravity = gravity * baseline + magnitude * (1 - baseline)
        filtered = filtered * smoothing + deviation * (1 - smoothing)
        amplitude = amplitude * .98f + abs(filtered) * .02f
        if (abs(filtered) >= .22f && (motion.lastOrNull()?.let { at - it >= 150 } != false)) {
            motion.addLast(at)
        }
        trimHistory(at)
        if (source != StepSource.ACCELEROMETER || at - (sharpMotion.lastOrNull() ?: -10_000L) < 650) return
        if (peakAt >= 0 && at - peakAt > 480) peakAt = -1
        val threshold = (amplitude * .65f).coerceIn(.38f, 1.1f)
        if (peakAt < 0 && filtered >= threshold) peakAt = at
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
