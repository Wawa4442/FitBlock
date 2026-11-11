package com.fitblock.app

import kotlin.math.*

enum class Exercise(
    val title: String,
    val unit: String,
    val base: Int,
    val camera: Boolean,
    val minimumUnitMillis: Long
) {
    SQUAT("Sentadillas", "repeticiones", 10, true, 480),
    PUSHUP("Flexiones", "repeticiones", 8, true, 420),
    JACK("Saltos de tijera", "repeticiones", 15, true, 340),
    STRETCH("Estiramiento de brazos", "segundos", 30, true, 1000),
    WALK("Caminata", "pasos", 100, false, 230);

    val goalRange: IntRange get() = base..if (this == WALK) 5000 else 300
}

data class Schedule(val start: Int = 0, val end: Int = 0, val days: Set<Int> = (1..7).toSet()) {
    // ISO weekdays. An overnight period belongs to the day on which it begins.
    fun active(day: Int, minute: Int): Boolean = when {
        start == end -> day in days
        start < end -> day in days && minute >= start && minute < end
        minute >= start -> day in days
        minute < end -> (if (day == 1) 7 else day - 1) in days
        else -> false
    }
}

object Policy {
    fun goal(base: Int, sessions: Int, progressive: Boolean, debt: Boolean): Int =
        (base * (1.0 + if (progressive) min(sessions, 4) * .1 else 0.0) * if (debt) 1.5 else 1.0).roundToInt()
    fun remaining(balance: Long, expires: Long, now: Long): Long = if (now >= expires) 0 else max(0, balance)
    fun blocked(enabled: Boolean, selected: Boolean, inSchedule: Boolean, balance: Long, used: Long, cap: Long, emergency: Boolean): Boolean =
        enabled && selected && inSchedule && !emergency && (balance <= 0 || used >= cap)
}

/** Recognizes both endpoints even when a natural repetition only reaches each briefly. */
class RepGate(private val minimumCycle: Long) {
    private var neutralAt = -1L
    private var activeAt = -1L
    private var lastValidAt = -1L
    val hasStart get() = neutralAt >= 0
    val awaitingReturn get() = activeAt >= 0

    fun reset() {
        neutralAt = -1
        activeAt = -1
        lastValidAt = -1
    }

    fun update(now: Long, neutral: Boolean, active: Boolean, valid: Boolean): Boolean {
        if (lastValidAt >= 0 && now - lastValidAt > MAX_GAP) reset()
        if (!valid) return false
        lastValidAt = now
        if (hasStart && now - neutralAt > 12_000) {
            neutralAt = -1
            activeAt = -1
        }
        if (neutral) {
            val completed = awaitingReturn && now - neutralAt >= minimumCycle && now - activeAt >= 65
            neutralAt = now
            activeAt = -1
            return completed
        }
        if (active && hasStart && !awaitingReturn && now - neutralAt >= 65) activeAt = now
        return false
    }

    companion object {
        const val MAX_GAP = 450L
    }
}
