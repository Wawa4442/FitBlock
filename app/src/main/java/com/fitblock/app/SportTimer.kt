package com.fitblock.app

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import org.json.JSONObject
import java.util.UUID

data class SportClock(
    val id: String,
    val sportId: String,
    val goalMillis: Long,
    val accumulated: Long,
    val anchor: Long,
    val running: Boolean,
    val boot: Int
) {
    fun elapsed(now: Long): Long = accumulated + if (running) (now - anchor).coerceAtLeast(0) else 0
    fun pause(now: Long) = copy(accumulated = elapsed(now), anchor = now, running = false)
    fun resume(now: Long) = copy(anchor = now, running = true)
}

class SportTimer private constructor(private val context: Context) {
    private val store get() = Store.get(context)
    private val boot get() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)

    @Synchronized fun current(): SportClock? {
        val raw = store.profilePreferences().getString(KEY, null) ?: return null
        val json = JSONObject(raw)
        val clock = SportClock(json.getString("id"), json.getString("sport"), json.getLong("goal"),
            json.getLong("accumulated"), json.getLong("anchor"), json.getBoolean("running"), json.getInt("boot"))
        if (clock.boot == boot) return clock
        return clock.copy(running = false, anchor = SystemClock.elapsedRealtime(), boot = boot).also { save(it) }
    }

    @Synchronized fun begin(sport: Sport, minutes: Int): SportClock {
        require(sport.exercise == null)
        if (store.hasWorkout()) throw IllegalStateException("Termina la actividad pendiente antes de iniciar otro deporte.")
        current()?.let { return it }
        val clock = SportClock(UUID.randomUUID().toString(), sport.id, minutes.coerceIn(1, 300) * 60_000L,
            0, SystemClock.elapsedRealtime(), true, boot)
        save(clock)
        return clock
    }

    @Synchronized fun pause() { current()?.let { save(it.pause(SystemClock.elapsedRealtime())) } }
    @Synchronized fun resume() { current()?.let { save(it.resume(SystemClock.elapsedRealtime())) } }
    @Synchronized fun discard() { store.profilePreferences().edit().remove(KEY).commit() }

    @Synchronized fun finish(): Boolean {
        val clock = current() ?: return false
        val paused = clock.pause(SystemClock.elapsedRealtime())
        save(paused)
        val sport = SportCatalog.get(clock.sportId) ?: return false
        if (!store.recordSport(clock.id, sport, paused.accumulated, clock.goalMillis)) return false
        discard()
        return true
    }

    private fun save(clock: SportClock) {
        val json = JSONObject().put("id", clock.id).put("sport", clock.sportId).put("goal", clock.goalMillis)
            .put("accumulated", clock.accumulated).put("anchor", clock.anchor)
            .put("running", clock.running).put("boot", clock.boot)
        check(store.profilePreferences().edit().putString(KEY, json.toString()).commit()) {
            "No se pudo guardar la sesión. Revisa el espacio del teléfono."
        }
    }

    companion object {
        private const val KEY = "active_sport"
        @Volatile private var instance: SportTimer? = null
        fun get(context: Context): SportTimer = instance ?: synchronized(this) {
            instance ?: SportTimer(context.applicationContext).also { instance = it }
        }
    }
}
