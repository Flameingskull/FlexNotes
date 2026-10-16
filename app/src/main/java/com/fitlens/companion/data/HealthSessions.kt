package com.fitlens.companion.data

import androidx.health.connect.client.records.ExerciseSessionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * An exercise session read from Health Connect (#190): its record [id], the local [date] it started on, Health
 * Connect's session [type], the [title] its app gave it, how long it lasted, the distance covered in metres when Health
 * Connect has one, and the app that recorded it.
 */
data class HealthSession(
    val id: String,
    val date: String,
    val type: Int,
    val title: String?,
    val durationSec: Int,
    val metres: Double?,
    val app: String?
)

/**
 * Exercise sessions from Health Connect as cardio sets (#190): a watch's run becomes one set (time and distance) on
 * the day log, under the FlexNotes exercise its session type maps to ([targetFor]), matched by name or created in the
 * [CATEGORY] category like the starter exercises. The session's title goes in the exercise comment.
 *
 * Each set keeps its Health Connect record id (`workout_set.external_id`), and each imported id is remembered as an
 * import rule ([RULE]), so a session is added once: a re-sync never adds it again, and a set the user deleted, moved or
 * edited stays as the user left it. A session deleted in Health Connect stays in FlexNotes (imports merge, never wipe).
 * Strength sessions are left out, since those are logged set by set in FlexNotes.
 */
object HealthSessions {
    const val CATEGORY = "Cardio"

    /** Import rule kind for a Health Connect session already added once (its key is the record id). */
    internal const val RULE = "hc_session"

    /** The exercise a session type is kept under. */
    data class Target(val name: String, val type: Int)

    private val run = Target("Outdoor Run", ExerciseTypes.DISTANCE_TIME)
    private val swim = Target("Swimming", ExerciseTypes.DISTANCE_TIME)
    private val stairs = Target("Stair Climber", ExerciseTypes.TIME)
    private val other = Target("Other Activity", ExerciseTypes.TIME)

    /** The exercise for Health Connect session [type], or null for a strength session, which isn't imported. */
    fun targetFor(type: Int): Target? = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
        ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> null
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> run
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> Target("Treadmill Run", ExerciseTypes.DISTANCE_TIME)
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> Target("Cycling", ExerciseTypes.DISTANCE_TIME)
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> Target("Stationary Bike", ExerciseTypes.DISTANCE_TIME)
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> Target("Walking", ExerciseTypes.DISTANCE_TIME)
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> Target("Hiking", ExerciseTypes.DISTANCE_TIME)
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> swim
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> Target("Rowing Machine", ExerciseTypes.DISTANCE_TIME)
        ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> Target("Rowing", ExerciseTypes.DISTANCE_TIME)
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> Target("Elliptical", ExerciseTypes.DISTANCE_TIME)
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING,
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> stairs
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> Target("HIIT", ExerciseTypes.TIME)
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> Target("Yoga", ExerciseTypes.TIME)
        ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> Target("Pilates", ExerciseTypes.TIME)
        else -> other
    }

    /**
     * Adds the [sessions] not added before, and re-reads the workout data when any were. Returns how many sets were
     * added.
     */
    suspend fun add(sessions: List<HealthSession>): Int = withContext(Dispatchers.IO) {
        if (sessions.isEmpty()) return@withContext 0
        val db = Store.db
        val added = write(db, sessions, Settings.currentPortable().distanceUnit)
        if (added > 0 && Store.snapshot.value != null) Store.refresh(*Area.WORKOUT.toTypedArray())
        added
    }

    /**
     * [add]'s write on [db] (tests run it on their own database), in one transaction. Distances are converted from
     * metres to the exercise's own distance unit, else [distanceUnit], the user's.
     */
    internal fun write(db: Db, sessions: List<HealthSession>, distanceUnit: String): Int = db.transaction {
        val w = db.workoutDao
        val known = HashSet<String>().apply {
            addAll(w.externalIds())
            addAll(w.ruleKeys(RULE))
        }
        val fresh = sessions.filter { it.id !in known && it.durationSec > 0 && Dates.parse(it.date) != null }
            .mapNotNull { s -> targetFor(s.type)?.let { s to it } }
            .distinctBy { it.first.id }
        if (fresh.isEmpty()) return@transaction 0
        val exercises = w.exerciseNames().toMutableList()
        val units = w.ownDistanceUnits().associate { it.id to it.distance_unit }
        var categoryId: Long? = null
        fun category(): Long = categoryId ?: run {
            val key = Workouts.nameKey(CATEGORY)
            (w.categoryNames().firstOrNull { Workouts.nameKey(it.name) == key }?.id
                ?: w.addCategory(CATEGORY, 0, w.lastCategoryOrder() + 1, Sources.FLEXNOTES)).also { categoryId = it }
        }
        fresh.sortedWith(compareBy({ it.first.date }, { it.first.id })).forEach { (s, target) ->
            val key = Workouts.nameKey(target.name)
            val exerciseId = exercises.firstOrNull { Workouts.nameKey(it.name) == key }?.id
                ?: w.addExercise(target.name, category(), target.type, null, Sources.FLEXNOTES).also { exercises += IdName(it, target.name) }
            val distance = s.metres?.takeIf { it > 0 }?.let { DistanceUnits.fromMetres(it, units[exerciseId] ?: distanceUnit) } ?: 0.0
            w.addExternalSet(exerciseId, s.date, distance, s.durationSec, HealthConnect.commentFor(listOfNotNull(s.app)), Sources.HEALTH_CONNECT, s.id)
            w.addRule(RULE, s.id, null)
            s.title?.trim()?.takeIf { it.isNotEmpty() }?.let { w.addExerciseCommentIfNone(s.date, exerciseId, it, Sources.HEALTH_CONNECT) }
        }
        fresh.size
    }
}
