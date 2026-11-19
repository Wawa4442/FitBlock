package com.fitblock.app

import org.json.JSONArray
import org.json.JSONObject

data class WalkDay(val steps: Int = 0, val activeMillis: Long = 0, val distanceMeters: Double = 0.0)

object WalkPersistence {
    fun routeJson(points: List<RoutePoint>) = JSONArray().apply {
        points.forEach { put(JSONArray().put(it.latitude).put(it.longitude).put(it.at).put(it.accuracy).put(it.segment)) }
    }

    fun routeFrom(json: JSONArray): List<RoutePoint> = (0 until json.length()).map { index ->
        val p = json.getJSONArray(index)
        RoutePoint(p.getDouble(0), p.getDouble(1), p.getLong(2), p.getDouble(3).toFloat(), p.getInt(4))
    }

    fun json(session: WalkProgress): JSONObject {
        val t = session.ticket
        val days = JSONObject()
        session.days.forEach { (date, day) -> days.put(date, JSONObject().put("steps", day.steps)
            .put("walkMs", day.activeMillis).put("distanceMeters", day.distanceMeters)) }
        return JSONObject().put("id", t.id).put("owner", t.owner).put("goal", t.goal)
            .put("reward", t.rewardMinutes).put("started", t.started).put("steps", session.steps)
            .put("activeMs", session.activeMillis).put("routeEnabled", session.routeEnabled)
            .put("distanceMeters", session.distanceMeters).put("route", routeJson(session.route))
            .put("days", days)
    }

    fun read(store: Store): WalkProgress? = runCatching {
        val raw = store.profilePreferences().getString("pendingWalk", null) ?: return null
        val o = JSONObject(raw)
        if (o.getString("owner") != store.profileId()) return null
        val days = o.getJSONObject("days")
        WalkProgress(WorkoutTicket(o.getString("id"), Exercise.WALK, o.getInt("goal"), o.getInt("reward"),
            o.getLong("started"), o.getString("owner")), o.getInt("steps"), WalkPhase.PAUSED,
            "Caminata recuperada. Reanuda para continuar; el tiempo de cierre no se cuenta.",
            o.getLong("activeMs"), o.getBoolean("routeEnabled"), routeFrom(o.getJSONArray("route")),
            o.getDouble("distanceMeters"), days.keys().asSequence().associateWith { date ->
                val d = days.getJSONObject(date)
                WalkDay(d.getInt("steps"), d.getLong("walkMs"), d.getDouble("distanceMeters"))
            })
    }.getOrNull()

    fun save(store: Store, session: WalkProgress) = store.profilePreferences().edit()
        .putString("pendingWalk", json(session).toString()).commit()

    fun clear(store: Store) { store.profilePreferences().edit().remove("pendingWalk").commit() }
}
