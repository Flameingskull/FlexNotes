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

/**
 * One built-in Session exercise (#249). [activity] is its [Energy.Activity] key (#244), so its calories use the
 * sport's METs; null when no activity fits (a Gym Session's sets already count their own calories).
 */
data class SportExercise(
    val name: String,
    val kind: String,
    val fields: Int = SessionKinds.defaults(kind),
    val activity: String? = null,
    /** [ExerciseTypes.SESSION], or [ExerciseTypes.ROUNDS] for the sports counted in rounds (#246, #247). */
    val type: Int = ExerciseTypes.SESSION
) {
    /** The group the optional picker files it under (#247): its activity's category, else its kind of sport. */
    val category: Energy.ActivityCategory?
        get() = Energy.Activity.of(activity)?.category
}

/** One entry of the optional "Add sport exercises" picker (#247): a sport and whether the library already has it. */
data class SportPick(val sport: SportExercise, val inLibrary: Boolean)

/**
 * The Session exercises every library gets once, on the update that brings them (#249, owner 2026-10-26), filed under
 * [CATEGORY]. Only names that aren't already there are added, nothing the user has is changed, and one the user later
 * deletes stays deleted: [Workouts.seedSportsOnce] runs once per database, remembered in `meta`.
 */
object SportLibrary {
    const val CATEGORY = "Sports & martial arts"
    const val SEEDED_KEY = "sports_seeded_v1"

    /** The `meta` mark of the once-only pass that gives the sports seeded in 1.0.176 their activity (#244). */
    const val ACTIVITY_KEY = "sports_activity_v1"

    val exercises: List<SportExercise> = listOf(
        SportExercise("Gym Session", SessionKinds.GYM),
        SportExercise("Brazilian Jiu-Jitsu", SessionKinds.COMBAT, activity = "bjj"),
        SportExercise("Muay Thai", SessionKinds.COMBAT, activity = "muay_thai"),
        SportExercise("Boxing", SessionKinds.COMBAT, activity = "boxing"),
        SportExercise("Kickboxing", SessionKinds.COMBAT, activity = "muay_thai"),
        SportExercise("MMA", SessionKinds.COMBAT, activity = "mma"),
        SportExercise("Wrestling", SessionKinds.COMBAT, activity = "wrestling"),
        SportExercise("Judo", SessionKinds.COMBAT, activity = "judo"),
        SportExercise("Karate", SessionKinds.COMBAT, activity = "karate_taekwondo"),
        SportExercise("Tennis", SessionKinds.RACKET, activity = "court_sports"),
        SportExercise("Football", SessionKinds.TEAM, activity = "team_sports"),
        SportExercise("Basketball", SessionKinds.TEAM, activity = "team_sports"),
        SportExercise("Squash / Padel", SessionKinds.RACKET, activity = "court_sports"),
        SportExercise("Cardio Session", SessionKinds.CARDIO),
        SportExercise("Swimming Session", SessionKinds.CARDIO, activity = "swimming"),
        SportExercise("Yoga / Mobility Session", SessionKinds.MIND_BODY, activity = "yoga")
    )

    /**
     * More sports to offer, never added on their own (#247): Session sports that have an activity (#244) and the
     * Rounds starters (#246) for sparring, bag work, pad work and rolls. [exercises] is untouched, because seeding
     * runs from it once. Each has an activity that exists in [Energy.Activity], so its calories use that sport's METs.
     */
    val extras: List<SportExercise> = listOf(
        SportExercise("Sparring Rounds", SessionKinds.COMBAT, activity = "boxing", type = ExerciseTypes.ROUNDS),
        SportExercise("Bag Work", SessionKinds.COMBAT, activity = "boxing", type = ExerciseTypes.ROUNDS),
        SportExercise("Pad Work", SessionKinds.COMBAT, activity = "boxing", type = ExerciseTypes.ROUNDS),
        SportExercise("BJJ Rolls", SessionKinds.COMBAT, activity = "bjj", type = ExerciseTypes.ROUNDS),
        SportExercise("Wrestling Live Goes", SessionKinds.COMBAT, activity = "wrestling", type = ExerciseTypes.ROUNDS),
        SportExercise("Taekwondo", SessionKinds.COMBAT, activity = "karate_taekwondo"),
        SportExercise("Grappling Drills", SessionKinds.COMBAT, activity = "grappling_drills"),
        SportExercise("Fencing", SessionKinds.COMBAT, activity = "fencing"),
        SportExercise("Volleyball", SessionKinds.TEAM, activity = "team_sports"),
        SportExercise("Rugby", SessionKinds.TEAM, activity = "team_sports"),
        SportExercise("Hockey", SessionKinds.TEAM, activity = "team_sports"),
        SportExercise("Badminton", SessionKinds.RACKET, activity = "court_sports"),
        SportExercise("Table Tennis", SessionKinds.RACKET, activity = "court_sports"),
        SportExercise("CrossFit", SessionKinds.GYM, activity = "crossfit_hyrox"),
        SportExercise("Hyrox", SessionKinds.GYM, activity = "crossfit_hyrox"),
        SportExercise("HIIT Class", SessionKinds.CARDIO, activity = "hiit"),
        SportExercise("Circuit Training", SessionKinds.GYM, activity = "circuit"),
        SportExercise("Pilates", SessionKinds.MIND_BODY, activity = "yoga")
    )

    private fun key(name: String): String = name.trim().replace(Regex("\\s+"), " ").lowercase()

    /**
     * The picker's list (#247): every extra sport in order, each marked when [libraryNames] already has one of that
     * name (ignoring case and spacing), so it is shown as "Already in your library" and can't be added twice.
     */
    fun picks(libraryNames: Collection<String>): List<SportPick> {
        val have = libraryNames.map { key(it) }.toSet()
        return extras.map { SportPick(it, key(it.name) in have) }
    }

    /** The sports to add from [chosen] names: the extras picked, minus any the library already has. */
    fun toAdd(chosen: Collection<String>, libraryNames: Collection<String>): List<SportExercise> {
        val picked = chosen.map { key(it) }.toSet()
        return picks(libraryNames).filter { !it.inLibrary && key(it.sport.name) in picked }.map { it.sport }
    }

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
