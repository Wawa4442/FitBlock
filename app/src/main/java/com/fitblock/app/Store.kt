package com.fitblock.app

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.*

data class WorkoutTicket(val id: String, val exercise: Exercise, val goal: Int, val rewardMinutes: Int, val started: Long)

class Store private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("fitblock_private", Context.MODE_PRIVATE)
    private var ticket: WorkoutTicket? = null
    private var clockSavedAt = 0L
    var consent: Boolean
        get() = prefs.getBoolean("consent", false)
        set(v) { prefs.edit().putBoolean("consent", v).commit() }
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false) && consent
        set(v) { prefs.edit().putBoolean("enabled", v).commit() }
    var progressive: Boolean
        get() = prefs.getBoolean("progressive", false)
        set(v) { prefs.edit().putBoolean("progressive", v).commit() }
    var name: String
        get() = prefs.getString("name", "") ?: ""
        set(v) { prefs.edit().putString("name", v.take(40)).commit() }
    var rewardMinutes: Int
        get() = prefs.getInt("reward", 10).coerceIn(1, 60)
        set(v) { prefs.edit().putInt("reward", v.coerceIn(1, 60)).commit() }
    var validityMinutes: Int
        get() = prefs.getInt("validity", 60).coerceIn(5, 240)
        set(v) { prefs.edit().putInt("validity", v.coerceIn(5, 240)).commit() }
    var dailyLimit: Int
        get() = prefs.getInt("limit", 60).coerceIn(1, 240)
        set(v) { prefs.edit().putInt("limit", v.coerceIn(1, 240)).commit() }
    var selected: Set<String>
        get() = prefs.getStringSet("selected", emptySet())!!.toSet()
        set(v) { prefs.edit().putStringSet("selected", v.toSet()).commit() }
    var schedule: Schedule
        get() = Schedule(prefs.getInt("start", 0), prefs.getInt("end", 0), (prefs.getStringSet("days", (1..7).map { "$it" }.toSet()) ?: emptySet()).mapNotNull { it.toIntOrNull() }.toSet())
        set(v) { prefs.edit().putInt("start", v.start).putInt("end", v.end).putStringSet("days", v.days.map { "$it" }.toSet()).commit() }
    fun baseGoal(e: Exercise) = prefs.getInt("goal_${e.name}", e.base).coerceIn(e.goalRange)
    fun setGoal(e: Exercise, value: Int) {
        prefs.edit().putInt("goal_${e.name}", value.coerceIn(e.goalRange)).commit()
    }
    fun appLimit(pkg: String) = prefs.getInt("cap_$pkg", dailyLimit).coerceIn(1, 240)
    fun setAppLimit(pkg: String, minutes: Int) { prefs.edit().putInt("cap_$pkg", minutes.coerceIn(1, 240)).commit() }
    private fun currentDay(): String {
        val today = LocalDate.now().toString()
        val high = prefs.getString("day_high", today)!!
        val day = maxOf(today, high) // Moving the clock backwards never resets the quota.
        if (day != high || !prefs.contains("day_high")) prefs.edit().putString("day_high", day).commit()
        return day
    }
    private fun statsObject(): JSONObject = runCatching { JSONObject(prefs.getString("stats", "{}")!!) }.getOrDefault(JSONObject())
    fun dayStats(day: String = currentDay()): JSONObject = statsObject().optJSONObject(day) ?: JSONObject()
    private fun updateDay(change: (JSONObject) -> Unit) {
        val all = statsObject(); val day = currentDay(); val obj = all.optJSONObject(day) ?: JSONObject()
        change(obj); all.put(day, obj)
        all.keys().asSequence().toList().sortedDescending().drop(90).forEach { all.remove(it) }
        prefs.edit().putString("stats", all.toString()).apply()
    }
    private fun clockCheck() {
        val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)
        val now = SystemClock.elapsedRealtime(); val wall = System.currentTimeMillis()
        val oldElapsed = prefs.getLong("clock_elapsed", now); val oldWall = prefs.getLong("clock_wall", wall)
        val changed = boot != prefs.getInt("boot", boot) || now < oldElapsed || abs((wall - oldWall) - (now - oldElapsed)) > 120_000
        val edit = prefs.edit().putInt("boot", boot).putLong("clock_elapsed", now).putLong("clock_wall", wall)
        if (changed) edit.putLong("balance", 0).putLong("expiry", 0).putLong("emergency_until", 0)
        if (changed || now - clockSavedAt > 30_000 || !prefs.contains("boot")) {
            edit.apply(); clockSavedAt = now
        }
        if (changed) audit("Reloj o reinicio", "Los pases temporales se invalidaron; tu configuración se conserva.")
    }
    @Synchronized fun balance(): Long {
        clockCheck()
        return Policy.remaining(prefs.getLong("balance", 0), prefs.getLong("expiry", 0), SystemClock.elapsedRealtime())
    }
    fun expiresIn(): Long = max(0, prefs.getLong("expiry", 0) - SystemClock.elapsedRealtime())
    fun used(pkg: String): Long = dayStats().optJSONObject("apps")?.optLong(pkg) ?: 0
    fun inSchedule(): Boolean { val d = LocalDateTime.now(); return schedule.active(d.dayOfWeek.value, d.hour * 60 + d.minute) }
    fun emergencyActive(): Boolean { clockCheck(); return SystemClock.elapsedRealtime() < prefs.getLong("emergency_until", 0) }
    fun blocked(pkg: String): Boolean = Policy.blocked(enabled, pkg in selected, inSchedule(), balance(), used(pkg), appLimit(pkg) * 60_000L, emergencyActive())
    @Synchronized fun consume(pkg: String, milliseconds: Long) {
        if (!enabled || pkg !in selected || !inSchedule() || emergencyActive()) return
        val available = balance(); val charged = min(milliseconds.coerceIn(0, 2000), min(available, max(0, appLimit(pkg) * 60_000L - used(pkg))))
        if (charged <= 0) return
        prefs.edit().putLong("balance", available - charged).apply()
        updateDay { obj ->
            val apps = obj.optJSONObject("apps") ?: JSONObject()
            apps.put(pkg, apps.optLong(pkg) + charged); obj.put("apps", apps)
            obj.put("used", obj.optLong("used") + charged)
        }
    }
    fun requiredGoal(e: Exercise): Int = Policy.goal(baseGoal(e), dayStats().optInt("sessions"), progressive, prefs.getBoolean("debt", false))
    @Synchronized fun startWorkout(e: Exercise): WorkoutTicket {
        val t = WorkoutTicket(UUID.randomUUID().toString(), e, requiredGoal(e), rewardMinutes, SystemClock.elapsedRealtime())
        ticket = t; return t
    }
    @Synchronized fun cancelWorkout(id: String) { if (ticket?.id == id) ticket = null }
    @Synchronized fun isWorkoutCurrent(id: String): Boolean = ticket?.id == id
    @Synchronized fun completeWorkout(id: String, verified: Int): Boolean {
        val t = ticket ?: return false
        val duration = SystemClock.elapsedRealtime() - t.started
        val minimum = t.goal * t.exercise.minimumUnitMillis
        if (t.id != id || verified < t.goal || duration < minimum) return false
        ticket = null // A session can only mint one reward.
        val next = balance() + t.rewardMinutes * 60_000L
        prefs.edit().putLong("balance", next).putLong("expiry", SystemClock.elapsedRealtime() + validityMinutes * 60_000L).putBoolean("debt", false).commit()
        updateDay { obj ->
            obj.put("sessions", obj.optInt("sessions") + 1)
            obj.put("earned", obj.optInt("earned") + t.rewardMinutes)
            obj.put("exerciseMs", obj.optLong("exerciseMs") + duration)
            val key = if (t.exercise == Exercise.WALK) "steps" else if (t.exercise == Exercise.STRETCH) "stretchSeconds" else "reps"
            obj.put(key, obj.optInt(key) + verified)
        }
        audit("Actividad completada", "${t.exercise.title}: $verified ${t.exercise.unit}. +${t.rewardMinutes} min.")
        return true
    }
    @Synchronized fun emergency(): Boolean {
        if (dayStats().optInt("emergencies") >= 1) return false
        clockCheck()
        prefs.edit().putLong("emergency_until", SystemClock.elapsedRealtime() + 120_000).putBoolean("debt", true).commit()
        updateDay { it.put("emergencies", it.optInt("emergencies") + 1) }
        audit("Acceso de emergencia", "2 minutos. La siguiente meta aumenta un 50 %.")
        return true
    }
    fun blockedAttempt() { updateDay { it.put("blocked", it.optInt("blocked") + 1) } }
    fun audit(title: String, detail: String) {
        val list = events(); val next = JSONArray()
        next.put(JSONObject().put("time", System.currentTimeMillis()).put("title", title).put("detail", detail))
        for (i in 0 until min(49, list.length())) next.put(list.getJSONObject(i))
        prefs.edit().putString("events", next.toString()).commit()
    }
    fun events(): JSONArray = runCatching { JSONArray(prefs.getString("events", "[]")) }.getOrDefault(JSONArray())
    fun streak(): Int {
        var day = LocalDate.now(); var n = 0
        if (dayStats(day.toString()).optInt("sessions") == 0) day = day.minusDays(1)
        while (n < 90 && dayStats(day.toString()).optInt("sessions") > 0) { n++; day = day.minusDays(1) }
        return n
    }
    fun totalSessions(): Int { val s = statsObject(); return s.keys().asSequence().sumOf { s.getJSONObject(it).optInt("sessions") } }
    fun export(): String = JSONObject().put("app", "FitBlock 0.2.0").put("stats", statsObject()).put("events", events()).toString(2)
    fun clear() { ticket = null; prefs.edit().clear().commit() }
    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store = instance ?: synchronized(this) { instance ?: Store(context.applicationContext).also { instance = it } }
    }
}
