package com.fitlens.companion.data

/**
 * The optional fields a Session exercise records (#249), as bits. Length and how hard the session felt are always
 * recorded, because training load needs them; these four can be left off a sport they don't suit (no "kind of session"
 * for tennis). An exercise with no choice stored ([Exercise.sessionFields] null) records all of them.
 */
object SessionMetrics {
    const val LIVE = 1
    const val INTENSITY = 2
    const val TYPE = 4
    const val ZONE = 8
    const val ALL = LIVE or INTENSITY or TYPE or ZONE

    /** Every metric, in the order the editor lists them. */
    val all = listOf(LIVE, INTENSITY, TYPE, ZONE)

    fun of(fields: Int?): Int = fields?.and(ALL) ?: ALL

    fun has(fields: Int?, metric: Int): Boolean = (of(fields) and metric) != 0
}

/** The kind of sport a Session exercise is (#249), stored by key so a key never changes meaning. */
object SessionKinds {
    const val COMBAT = "combat"
    const val RACKET = "racket"
    const val TEAM = "team"
    const val CARDIO = "cardio"
    const val MIND_BODY = "mind_body"
    const val GYM = "gym"

    /** Every kind, in the order the editor lists them. */
    val all = listOf(COMBAT, RACKET, TEAM, CARDIO, MIND_BODY, GYM)

    fun of(key: String?): String? = key?.takeIf { it in all }

    /** What a sport of each kind usually records, used when no exercise of that kind exists to compare with. */
    fun defaults(kind: String?): Int = when (kind) {
        COMBAT -> SessionMetrics.ALL
        RACKET, TEAM -> SessionMetrics.LIVE or SessionMetrics.INTENSITY or SessionMetrics.ZONE
        CARDIO -> SessionMetrics.ZONE
        MIND_BODY -> 0
        GYM -> SessionMetrics.INTENSITY
        else -> SessionMetrics.ALL
    }
}

/** One built-in Session exercise (#249). */
data class SportExercise(val name: String, val kind: String, val fields: Int = SessionKinds.defaults(kind))

/**
 * The Session exercises every library gets once, on the update that brings them (#249, owner 2026-10-26), filed under
 * [CATEGORY]. Only names that aren't already there are added, nothing the user has is changed, and one the user later
 * deletes stays deleted: [Workouts.seedSportsOnce] runs once per database, remembered in `meta`.
 */
object SportLibrary {
    const val CATEGORY = "Sports & martial arts"
    const val SEEDED_KEY = "sports_seeded_v1"

    val exercises: List<SportExercise> = listOf(
        SportExercise("Gym Session", SessionKinds.GYM),
        SportExercise("Brazilian Jiu-Jitsu", SessionKinds.COMBAT),
        SportExercise("Muay Thai", SessionKinds.COMBAT),
        SportExercise("Boxing", SessionKinds.COMBAT),
        SportExercise("Kickboxing", SessionKinds.COMBAT),
        SportExercise("MMA", SessionKinds.COMBAT),
        SportExercise("Wrestling", SessionKinds.COMBAT),
        SportExercise("Judo", SessionKinds.COMBAT),
        SportExercise("Karate", SessionKinds.COMBAT),
        SportExercise("Tennis", SessionKinds.RACKET),
        SportExercise("Football", SessionKinds.TEAM),
        SportExercise("Basketball", SessionKinds.TEAM),
        SportExercise("Squash / Padel", SessionKinds.RACKET),
        SportExercise("Cardio Session", SessionKinds.CARDIO),
        SportExercise("Swimming Session", SessionKinds.CARDIO),
        SportExercise("Yoga / Mobility Session", SessionKinds.MIND_BODY)
    )

    /** What a new sport of [kind] should record, and the exercises the suggestion was drawn from. */
    data class Suggestion(val fields: Int, val basedOn: List<String>)

    /**
     * Suggests the fields for a new sport of [kind] (#249) from the Session exercises of the same kind already in the
     * library: each field most of them record. With none to compare, the kind's usual fields.
     */
    fun suggest(kind: String?, library: Collection<Exercise>, exceptId: Long = -1L): Suggestion {
        val k = SessionKinds.of(kind) ?: return Suggestion(SessionMetrics.ALL, emptyList())
        val same = library.filter { it.id != exceptId && ExerciseTypes.isSession(it.type) && it.sessionKind == k }
            .sortedBy { it.name.lowercase() }
        if (same.isEmpty()) return Suggestion(SessionKinds.defaults(k), emptyList())
        var fields = 0
        SessionMetrics.all.forEach { m ->
            if (same.count { SessionMetrics.has(it.sessionFields, m) } * 2 >= same.size) fields = fields or m
        }
        return Suggestion(fields, same.map { it.name })
    }
}
