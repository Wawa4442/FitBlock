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

data class WorkoutTicket(val id: String, val exercise: Exercise, val goal: Int, val rewardMinutes: Int, val started: Long, val owner: String)

class Store private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("fitblock_private", Context.MODE_PRIVATE)
    private var ticket: WorkoutTicket? = null
    private var clockSavedAt = 0L
    fun profileId(): String = Accounts.get(context).current()?.id ?: "guest"
    internal fun profilePreferences() = Accounts.get(context).current()?.let {
        context.getSharedPreferences("fitblock_user_${it.id}", Context.MODE_PRIVATE)
    } ?: prefs
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
        get() = Accounts.get(context).current()?.name ?: prefs.getString("name", "").orEmpty()
        set(v) {
            if (Accounts.get(context).current() == null) prefs.edit().putString("name", v.take(40)).commit()
            else Accounts.get(context).rename(v)
        }
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
        val high = profilePreferences().getString("day_high", today)!!
        val day = maxOf(today, high) // Moving the clock backwards never resets the quota.
        if (day != high || !profilePreferences().contains("day_high")) profilePreferences().edit().putString("day_high", day).commit()
        return day
    }
    private fun statsObject(): JSONObject = runCatching { JSONObject(profilePreferences().getString("stats", "{}")!!) }.getOrDefault(JSONObject())
    fun dayStats(day: String = currentDay()): JSONObject = statsObject().optJSONObject(day) ?: JSONObject()
    private fun updateDay(change: (JSONObject) -> Unit) {
        val all = changedDay(change)
        profilePreferences().edit().putString("stats", all.toString()).apply()
    }
    private fun changedDay(change: (JSONObject) -> Unit): JSONObject {
        val all = statsObject(); val day = currentDay(); val obj = all.optJSONObject(day) ?: JSONObject()
        change(obj); all.put(day, obj)
        all.keys().asSequence().toList().sortedDescending().drop(90).forEach { all.remove(it) }
        return all
    }
    private fun clockCheck() {
        val prefs = profilePreferences()
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
        val profile = profilePreferences()
        return Policy.remaining(profile.getLong("balance", 0), profile.getLong("expiry", 0), SystemClock.elapsedRealtime())
    }
    fun expiresIn(): Long = max(0, profilePreferences().getLong("expiry", 0) - SystemClock.elapsedRealtime())
    fun used(pkg: String): Long = dayStats().optJSONObject("apps")?.optLong(pkg) ?: 0
    fun inSchedule(): Boolean { val d = LocalDateTime.now(); return schedule.active(d.dayOfWeek.value, d.hour * 60 + d.minute) }
    fun emergencyActive(): Boolean { clockCheck(); return SystemClock.elapsedRealtime() < profilePreferences().getLong("emergency_until", 0) }
    fun blocked(pkg: String): Boolean = Policy.blocked(enabled, pkg in selected, inSchedule(), balance(), used(pkg), appLimit(pkg) * 60_000L, emergencyActive())
    @Synchronized fun consume(pkg: String, milliseconds: Long) {
        if (!enabled || pkg !in selected || !inSchedule() || emergencyActive()) return
        val available = balance(); val charged = min(milliseconds.coerceIn(0, 2000), min(available, max(0, appLimit(pkg) * 60_000L - used(pkg))))
        if (charged <= 0) return
        profilePreferences().edit().putLong("balance", available - charged).apply()
        updateDay { obj ->
            val apps = obj.optJSONObject("apps") ?: JSONObject()
            apps.put(pkg, apps.optLong(pkg) + charged); obj.put("apps", apps)
            obj.put("used", obj.optLong("used") + charged)
        }
    }
    fun requiredGoal(e: Exercise): Int = Policy.goal(baseGoal(e), dayStats().optInt("sessions"), progressive, profilePreferences().getBoolean("debt", false))
    @Synchronized fun startWorkout(e: Exercise): WorkoutTicket {
        val t = WorkoutTicket(UUID.randomUUID().toString(), e, requiredGoal(e), rewardMinutes, SystemClock.elapsedRealtime(), profileId())
        ticket = t; return t
    }
    @Synchronized fun cancelWorkout(id: String) { if (ticket?.id == id) ticket = null }
    @Synchronized fun isWorkoutCurrent(id: String): Boolean = ticket?.let { it.id == id && it.owner == profileId() } == true
    @Synchronized fun hasWorkout(): Boolean = ticket?.let { isWorkoutCurrent(it.id) } == true
    @Synchronized fun completeWorkout(id: String, verified: Int): Boolean {
        val t = ticket ?: return false
        val duration = SystemClock.elapsedRealtime() - t.started
        val minimum = t.goal * t.exercise.minimumUnitMillis
        if (t.id != id || t.owner != profileId() || verified < t.goal || duration < minimum) return false
        val next = balance() + t.rewardMinutes * 60_000L
        val stats = changedDay { obj ->
            obj.put("sessions", obj.optInt("sessions") + 1)
            obj.put("earned", obj.optInt("earned") + t.rewardMinutes)
            obj.put("exerciseMs", obj.optLong("exerciseMs") + duration)
            val key = if (t.exercise == Exercise.WALK) "steps" else if (t.exercise == Exercise.STRETCH) "stretchSeconds" else "reps"
            obj.put(key, obj.optInt(key) + verified)
        }
        val record = JSONObject().put("id", t.id).put("sport", t.exercise.name.lowercase(java.util.Locale.ROOT))
            .put("title", t.exercise.title).put("date", currentDay()).put("endedAt", System.currentTimeMillis())
            .put("durationMs", duration).put("amount", verified).put("unit", t.exercise.unit)
            .put("verified", true).put("goalMet", true).put("reward", t.rewardMinutes)
        if (!profilePreferences().edit().putLong("balance", next)
                .putLong("expiry", SystemClock.elapsedRealtime() + validityMinutes * 60_000L)
                .putBoolean("debt", false).putString("stats", stats.toString())
                .putString("records", appendRecord(record).toString()).commit()) return false
        ticket = null
        audit("Actividad completada", "${t.exercise.title}: $verified ${t.exercise.unit}. +${t.rewardMinutes} min.")
        AwardCatalog.refresh(this)
        return true
    }
    @Synchronized fun emergency(): Boolean {
        if (dayStats().optInt("emergencies") >= 1) return false
        clockCheck()
        profilePreferences().edit().putLong("emergency_until", SystemClock.elapsedRealtime() + 120_000).putBoolean("debt", true).commit()
        updateDay { it.put("emergencies", it.optInt("emergencies") + 1) }
        audit("Acceso de emergencia", "2 minutos. La siguiente meta aumenta un 50 %.")
        return true
    }
    fun blockedAttempt() { updateDay { it.put("blocked", it.optInt("blocked") + 1) } }
    fun audit(title: String, detail: String) {
        val list = events(); val next = JSONArray()
        next.put(JSONObject().put("time", System.currentTimeMillis()).put("title", title).put("detail", detail))
        for (i in 0 until min(49, list.length())) next.put(list.getJSONObject(i))
        profilePreferences().edit().putString("events", next.toString()).commit()
    }
    fun events(): JSONArray = runCatching { JSONArray(profilePreferences().getString("events", "[]")) }.getOrDefault(JSONArray())
    fun streak(): Int {
        val days = activeDates()
        var day = LocalDate.now()
        var count = 0
        if (day !in days) day = day.minusDays(1)
        while (day in days) { count++; day = day.minusDays(1) }
        return count
    }
    fun totalSessions(): Int { val s = statsObject(); return s.keys().asSequence().sumOf { s.getJSONObject(it).optInt("sessions") } }
    fun activeDates(): Set<LocalDate> {
        val stats = statsObject()
        val days = stats.keys().asSequence().filter {
            stats.getJSONObject(it).let { day -> day.optInt("sessions") + day.optInt("manualSessions") > 0 }
        }.toMutableSet()
        val history = records()
        for (index in 0 until history.length()) {
            val record = history.getJSONObject(index)
            if (record.optBoolean("goalMet")) days.add(record.getString("date"))
        }
        return days.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
    }
    fun bestStreak(): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in activeDates().sorted()) {
            run = if (previous?.plusDays(1) == day) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }
    fun totalActivities(): Int {
        val counts = mutableMapOf<String, Int>()
        val all = statsObject()
        all.keys().forEach { day -> counts[day] = all.getJSONObject(day).let { it.optInt("sessions") + it.optInt("manualSessions") } }
        val history = records()
        val recordedCounts = mutableMapOf<String, Int>()
        for (index in 0 until history.length()) {
            val record = history.getJSONObject(index)
            if (record.optBoolean("goalMet")) {
                val day = record.getString("date")
                recordedCounts[day] = (recordedCounts[day] ?: 0) + 1
            }
        }
        recordedCounts.forEach { (day, amount) -> counts[day] = maxOf(counts[day] ?: 0, amount) }
        return counts.values.sum()
    }
    fun records(): JSONArray = JSONArray(profilePreferences().getString("records", "[]"))
    private fun appendRecord(record: JSONObject): JSONArray {
        val old = records()
        return JSONArray().apply {
            put(record)
            for (index in 0 until old.length()) put(old.getJSONObject(index))
        }
    }
    @Synchronized internal fun recordSport(id: String, sport: Sport, elapsed: Long, goal: Long): Boolean {
        if (sport.exercise != null || elapsed < 1000 || goal < 60_000) return false
        val previous = records()
        if ((0 until previous.length()).any { previous.getJSONObject(it).optString("id") == id }) return true
        val completed = elapsed >= goal
        val stats = changedDay {
            it.put("recordedSessions", it.optInt("recordedSessions") + 1)
            it.put("sportMs", it.optLong("sportMs") + elapsed)
            if (completed) it.put("manualSessions", it.optInt("manualSessions") + 1)
        }
        val record = JSONObject().put("id", id).put("sport", sport.id).put("title", sport.title)
            .put("date", currentDay()).put("endedAt", System.currentTimeMillis()).put("durationMs", elapsed)
            .put("amount", elapsed / 1000).put("unit", "segundos").put("verified", false)
            .put("goalMet", completed).put("reward", 0)
        val saved = profilePreferences().edit().putString("stats", stats.toString())
            .putString("records", appendRecord(record).toString()).commit()
        if (saved) AwardCatalog.refresh(this)
        return saved
    }
    fun totalAmount(metric: String): Long {
        val counts = mutableMapOf<String, Long>()
        val stats = statsObject()
        stats.keys().forEach { day -> counts[day] = stats.getJSONObject(day).optLong(metric) }
        val records = records()
        val recent = mutableMapOf<String, Long>()
        for (index in 0 until records.length()) {
            val record = records.getJSONObject(index)
            val key = when (record.optString("sport")) {
                "walk" -> "steps"
                "stretch" -> "stretchSeconds"
                "squat", "pushup", "jack" -> "reps"
                else -> ""
            }
            if (record.optBoolean("verified") && key == metric) {
                val day = record.getString("date")
                recent[day] = (recent[day] ?: 0) + record.optLong("amount")
            }
        }
        recent.forEach { (day, count) -> counts[day] = maxOf(counts[day] ?: 0, count) }
        return counts.values.sum()
    }
    fun favoriteSports(): Set<String> = profilePreferences().getStringSet("favoriteSports", emptySet())!!.toSet()
    fun setFavoriteSport(id: String, favorite: Boolean) {
        val next = favoriteSports().toMutableSet().apply { if (favorite) add(id) else remove(id) }
        profilePreferences().edit().putStringSet("favoriteSports", next).apply()
    }
    fun sportGoal(id: String) = profilePreferences().getInt("sportGoal_$id", 10).coerceIn(1, 300)
    fun setSportGoal(id: String, minutes: Int) { profilePreferences().edit().putInt("sportGoal_$id", minutes.coerceIn(1, 300)).apply() }
    internal fun claimGuestHistory(accountId: String) {
        if (prefs.contains("guest_claimed")) return
        val target = context.getSharedPreferences("fitblock_user_$accountId", Context.MODE_PRIVATE)
        val edit = target.edit()
        val keys = listOf("stats", "records", "events", "day_high", "balance", "expiry", "debt",
            "emergency_until", "clock_elapsed", "clock_wall", "boot", "favoriteSports",
            "unlockedAwards", "claimedAwards", "groupDraft") + prefs.all.keys.filter { it.startsWith("sportGoal_") }
        for (key in keys) {
            when (val value = prefs.all[key]) {
                is String -> edit.putString(key, value)
                is Long -> edit.putLong(key, value)
                is Int -> edit.putInt(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        if (!edit.commit()) throw AccountException("No se pudo asociar el historial. Intenta de nuevo.")
        val guest = prefs.edit().putString("guest_claimed", accountId)
        keys.forEach { guest.remove(it) }
        guest.commit()
    }
    fun export(): String = JSONObject().put("app", "FitBlock 0.3.0").put("stats", statsObject())
        .put("records", records()).put("events", events()).toString(2)
    fun clear() {
        ticket = null
        Accounts.get(context).clearAll()
        prefs.edit().clear().commit()
    }
    companion object {
        @Volatile private var instance: Store? = null
        fun get(context: Context): Store = instance ?: synchronized(this) { instance ?: Store(context.applicationContext).also { instance = it } }
    }
}
