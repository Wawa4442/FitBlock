package com.fitblock.app

import java.text.Normalizer
import java.util.Locale

enum class SportCategory(val title: String) {
    VERIFIED("Verificados"),
    CARDIO("Cardio y aire libre"),
    STRENGTH("Fuerza y gimnasio"),
    MIND_BODY("Cuerpo y mente"),
    TEAM("Deportes de equipo"),
    RACKET("Raqueta y precisión"),
    COMBAT("Combate"),
    WATER("Acuáticos"),
    WINTER("Invierno y aventura"),
    DANCE("Baile")
}

data class Sport(
    val id: String,
    val title: String,
    val category: SportCategory,
    val exercise: Exercise? = null
) {
    val tracking: String get() = when {
        exercise == Exercise.WALK -> "Pasos verificados"
        exercise != null -> "Cámara"
        else -> "Cronómetro"
    }
}

object SportCatalog {
    val all: List<Sport> = buildList {
        Exercise.entries.forEach { add(Sport(it.name.lowercase(Locale.ROOT), it.title, SportCategory.VERIFIED, it)) }
        fun sports(category: SportCategory, vararg names: Pair<String, String>) {
            names.forEach { (id, title) -> add(Sport(id, title, category)) }
        }
        sports(SportCategory.CARDIO,
            "running" to "Correr al aire libre", "treadmill" to "Correr en caminadora",
            "trail_running" to "Carrera de montaña", "track_running" to "Carrera en pista",
            "hiking" to "Senderismo", "nordic_walking" to "Marcha nórdica",
            "cycling" to "Ciclismo", "indoor_cycling" to "Bicicleta estática",
            "mountain_biking" to "Ciclismo de montaña", "hand_cycling" to "Ciclismo de mano",
            "elliptical" to "Elíptica", "stair_climbing" to "Escaleras",
            "jump_rope" to "Saltar la cuerda", "rowing_machine" to "Máquina de remo",
            "wheelchair_walk" to "Silla de ruedas a ritmo de paseo",
            "wheelchair_run" to "Silla de ruedas a ritmo de carrera", "skating" to "Patinaje",
            "skateboarding" to "Skateboarding")
        sports(SportCategory.STRENGTH,
            "strength" to "Entrenamiento de fuerza", "functional" to "Entrenamiento funcional",
            "calisthenics" to "Calistenia", "weightlifting" to "Levantamiento de pesas",
            "cross_training" to "Entrenamiento cruzado", "hiit" to "HIIT",
            "circuit" to "Circuito", "core" to "Entrenamiento de abdomen",
            "mixed_cardio" to "Cardio mixto", "gymnastics" to "Gimnasia",
            "step_training" to "Step", "fitness_gaming" to "Videojuegos de ejercicio")
        sports(SportCategory.MIND_BODY,
            "yoga" to "Yoga", "pilates" to "Pilates", "tai_chi" to "Tai chi",
            "flexibility" to "Flexibilidad", "cooldown" to "Vuelta a la calma",
            "mobility" to "Movilidad", "barre" to "Barre", "breathing" to "Respiración consciente")
        sports(SportCategory.TEAM,
            "soccer" to "Fútbol", "futsal" to "Fútbol de salón",
            "basketball" to "Básquetbol", "volleyball" to "Voleibol",
            "beach_volleyball" to "Voleibol de playa", "baseball" to "Béisbol",
            "softball" to "Softbol", "american_football" to "Fútbol americano",
            "rugby" to "Rugby", "handball" to "Balonmano", "field_hockey" to "Hockey sobre césped",
            "ice_hockey" to "Hockey sobre hielo", "lacrosse" to "Lacrosse",
            "cricket" to "Críquet", "ultimate" to "Ultimate frisbee")
        sports(SportCategory.RACKET,
            "tennis" to "Tenis", "table_tennis" to "Tenis de mesa",
            "badminton" to "Bádminton", "squash" to "Squash", "racquetball" to "Ráquetbol",
            "pickleball" to "Pickleball", "padel" to "Pádel", "golf" to "Golf",
            "archery" to "Tiro con arco", "bowling" to "Boliche", "fencing" to "Esgrima")
        sports(SportCategory.COMBAT,
            "boxing" to "Boxeo", "kickboxing" to "Kickboxing", "martial_arts" to "Artes marciales",
            "wrestling" to "Lucha", "karate" to "Karate", "taekwondo" to "Taekwondo",
            "judo" to "Judo", "jiu_jitsu" to "Jiu-jitsu")
        sports(SportCategory.WATER,
            "pool_swimming" to "Natación en piscina", "open_water_swimming" to "Natación en aguas abiertas",
            "water_fitness" to "Ejercicio acuático", "water_polo" to "Waterpolo",
            "surfing" to "Surf", "paddleboarding" to "Paddle surf",
            "kayaking" to "Kayak", "canoeing" to "Piragüismo",
            "rowing" to "Remo", "sailing" to "Vela", "diving" to "Buceo")
        sports(SportCategory.WINTER,
            "alpine_skiing" to "Esquí alpino", "cross_country_skiing" to "Esquí de fondo",
            "snowboarding" to "Snowboard", "snowshoeing" to "Raquetas de nieve",
            "ice_skating" to "Patinaje sobre hielo", "curling" to "Curling",
            "climbing" to "Escalada", "bouldering" to "Búlder", "equestrian" to "Equitación")
        sports(SportCategory.DANCE,
            "dance" to "Baile", "dance_fitness" to "Baile fitness", "zumba" to "Zumba",
            "social_dance" to "Baile de salón", "ballet" to "Ballet", "aerobics" to "Aeróbicos")
    }

    fun get(id: String): Sport? = all.find { it.id == id }

    fun search(query: String, category: SportCategory? = null): List<Sport> {
        val key = normalized(query)
        return all.filter { (category == null || it.category == category) && normalized(it.title).contains(key) }
    }

    private fun normalized(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
}
