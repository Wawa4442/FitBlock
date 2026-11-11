package com.fitblock.app

data class Award(val id: String, val title: String, val description: String, val current: Long, val target: Long) {
    val reached get() = current >= target
}

object AwardCatalog {
    fun progress(store: Store): List<Award> {
        val activities = store.totalActivities().toLong()
        val best = store.bestStreak().toLong()
        val records = store.records()
        val sports = (0 until records.length()).map { records.getJSONObject(it) }
            .filter { it.optBoolean("goalMet") }.map { it.getString("sport") }.distinct().size.toLong()
        return listOf(
            Award("first", "Primer paso", "Completa tu primera actividad", activities, 1),
            Award("three_days", "Tres días para ti", "Consigue una racha de 3 días", best, 3),
            Award("week", "Una semana activa", "Consigue una racha de 7 días", best, 7),
            Award("month", "Constancia de un mes", "Consigue una racha de 30 días", best, 30),
            Award("ten", "En movimiento", "Completa 10 actividades", activities, 10),
            Award("steps", "Mil pasos", "Completa 1000 pasos verificados", store.totalAmount("steps"), 1000),
            Award("reps", "Cada repetición suma", "Completa 100 repeticiones verificadas", store.totalAmount("reps"), 100),
            Award("explorer", "Explorador deportivo", "Completa metas de 3 actividades distintas", sports, 3)
        )
    }

    fun refresh(store: Store): Set<String> {
        val prefs = store.profilePreferences()
        val unlocked = prefs.getStringSet("unlockedAwards", emptySet())!!.toMutableSet()
        unlocked.addAll(progress(store).filter { it.reached }.map { it.id })
        prefs.edit().putStringSet("unlockedAwards", unlocked).apply()
        return unlocked
    }

    fun claimed(store: Store): Set<String> = store.profilePreferences().getStringSet("claimedAwards", emptySet())!!.toSet()

    fun claim(store: Store, id: String): Boolean {
        if (id !in refresh(store)) return false
        val claims = claimed(store) + id
        return store.profilePreferences().edit().putStringSet("claimedAwards", claims).commit()
    }
}
