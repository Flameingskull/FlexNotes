package com.fitlens.companion.data

import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

/**
 * The database as Room sees it (#36). Room opens `fitlens.db`, runs the upgrades in [Db] and, after every upgrade,
 * checks that each table below matches what it finds: the same columns, types, NOT NULLs, defaults, keys and
 * indices. Room also creates these tables on a fresh install, so the defaults here are the ones a new row gets.
 *
 * Column names are the property names, exactly as the SQL in the rest of the app uses them. Every column the app has
 * ever had is here: the v21 rebuild ([Db.reconcile]) refuses to drop a column it doesn't know, so a column left out
 * of this file stops the upgrade instead of losing data. Adding a column means a new [Db.VERSION] and an upgrade
 * step, the same as before. Room exports this schema to `app/schemas/` on every build (`room.schemaLocation`).
 */
@Database(
    version = Db.VERSION,
    exportSchema = true,
    entities = [
        CategoryRow::class, ExerciseRow::class, WorkoutSetRow::class, MeasurementRow::class, MeasurementRecordRow::class,
        WorkoutCommentRow::class, ExerciseCommentRow::class, WorkoutRestRow::class, WorkoutTimeRow::class,
        ImportRuleRow::class, ExerciseGoalRow::class, ExerciseTypeRow::class,
        SavedWorkoutRow::class, SavedWorkoutExerciseRow::class, SavedWorkoutSetRow::class,
        RoutineRow::class, RoutineDayRow::class, WorkoutOriginRow::class, RoutineDayExerciseRow::class, RoutineDaySetRow::class,
        PhotoRow::class, MetaRow::class,
        MedScheduleRow::class, MedPhaseRow::class, MedDoseRow::class, MedActivationRow::class, MedDoseStatusRow::class,
        MedDoseChangeRow::class, MedInfoRow::class, SymptomRow::class, SymptomRatingRow::class
    ]
)
abstract class FlexNotesDatabase : RoomDatabase() {
    abstract fun snapshotDao(): SnapshotDao
    abstract fun metaDao(): MetaDao
    abstract fun bodyDao(): BodyDao
    abstract fun photoDao(): PhotoDao
    abstract fun goalDao(): GoalDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun routineDao(): RoutineDao
    abstract fun importDao(): ImportDao
    abstract fun maintenanceDao(): MaintenanceDao
    abstract fun medDao(): MedDao
}

// ---- Workout data. `source` is 'fitnotes' (imported) or 'fitlens' (made or edited in FlexNotes) -------------------

@Entity(tableName = "category")
data class CategoryRow(
    @PrimaryKey val id: Long,
    val name: String,
    @ColumnInfo(defaultValue = "0") val colour: Long,
    @ColumnInfo(defaultValue = "0") val sort_order: Int,
    @ColumnInfo(defaultValue = "'fitlens'") val source: String,
    val fitnotes_id: Long?
)

@Entity(tableName = "exercise")
data class ExerciseRow(
    @PrimaryKey val id: Long,
    val name: String,
    @ColumnInfo(defaultValue = "0") val category_id: Long,
    @ColumnInfo(defaultValue = "0") val type: Int,
    val notes: String?,
    @ColumnInfo(defaultValue = "0") val favourite: Int,
    @ColumnInfo(defaultValue = "'fitlens'") val source: String,
    val fitnotes_id: Long?,
    val weight_step: Double?,
    @ColumnInfo(defaultValue = "-1") val default_graph: Int,
    val rest_seconds: Int?,
    val distance_unit: String?,
    val weight_unit: String?,
    /** The exercise's own activity, an `Energy.Activity` key (#174), or null to work its calories out automatically. */
    val activity: String?
)

@Entity(
    tableName = "workout_set",
    indices = [Index(value = ["date"], name = "idx_set_date"), Index(value = ["exercise_id"], name = "idx_set_ex")]
)
data class WorkoutSetRow(
    @PrimaryKey val id: Long,
    val exercise_id: Long,
    val date: String,
    @ColumnInfo(defaultValue = "0") val weight: Double,
    @ColumnInfo(defaultValue = "0") val reps: Int,
    @ColumnInfo(defaultValue = "0") val distance: Double,
    @ColumnInfo(defaultValue = "0") val duration: Long,
    @ColumnInfo(defaultValue = "0") val is_pr: Int,
    val comment: String?,
    @ColumnInfo(defaultValue = "'fitlens'") val source: String,
    val fitnotes_id: Long?,
    @ColumnInfo(defaultValue = "0") val set_type: Int,
    val rpe: Double?,
    @ColumnInfo(defaultValue = "0") val position: Long,
    @ColumnInfo(defaultValue = "0") val superset: Int,
    @ColumnInfo(defaultValue = "0") val done: Int,
    val rest_seconds: Int?,
    val metric: Double?,
    /** A session's hard or live seconds, its intensity, type and cardio zone (#240, #241); null when not recorded. */
    val live_seconds: Int? = null,
    val intensity: Int? = null,
    val session_type: Int? = null,
    val zone: Int? = null,
    /** The Health Connect record a set came from (#190), so a re-sync never adds it twice; null for every other set. */
    val external_id: String? = null
)

@Entity(tableName = "workout_comment")
data class WorkoutCommentRow(
    @PrimaryKey val id: Long,
    val date: String,
    val comment: String,
    @ColumnInfo(defaultValue = "'fitlens'") val source: String
)

/** One note per exercise within a date's workout (#107). */
@Entity(
    tableName = "exercise_comment",
    indices = [Index(value = ["date", "exercise_id"], name = "idx_exercise_comment_day", unique = true)]
)
data class ExerciseCommentRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val date: String,
    val exercise_id: Long,
    val comment: String,
    @ColumnInfo(defaultValue = "'fitlens'") val source: String
)

/** Prescribed rest on a logged date (#138). */
@Entity(tableName = "workout_rest", primaryKeys = ["date", "exercise_id"])
data class WorkoutRestRow(
    val date: String,
    val exercise_id: Long,
    val rest_seconds: Int?,
    val rest_after_seconds: Int?
)

@Entity(tableName = "workout_time")
data class WorkoutTimeRow(
    @PrimaryKey val id: Long,
    val date: String,
    val start: String?,
    val finish: String?,
    @ColumnInfo(defaultValue = "'fitlens'") val source: String
)

/** What the user did in FlexNotes to imported data, so a later FitNotes import respects it (#6). */
@Entity(tableName = "import_rule")
data class ImportRuleRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val kind: String,
    val key: String,
    val target_id: Long?
)

/** Exercise goals (#25). */
@Entity(tableName = "exercise_goal")
data class ExerciseGoalRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val exercise_id: Long,
    val kind: Int,
    val target: Double,
    @ColumnInfo(defaultValue = "0") val sort_order: Int
)

/** User-defined exercise types (#14), with ids from [ExerciseTypes.CUSTOM_BASE]. */
@Entity(tableName = "exercise_type")
data class ExerciseTypeRow(
    @PrimaryKey val id: Long,
    val name: String,
    @ColumnInfo(defaultValue = "0") val uses_weight: Int,
    @ColumnInfo(defaultValue = "0") val uses_reps: Int,
    @ColumnInfo(defaultValue = "0") val uses_distance: Int,
    @ColumnInfo(defaultValue = "0") val uses_time: Int,
    val metric_name: String?,
    val metric_unit: String?
)

// ---- Saved workouts of v7–v12 (#100): empty since v13, kept so an older FlexNotes can still open the file (#77) ------

@Entity(tableName = "saved_workout")
data class SavedWorkoutRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val notes: String?,
    @ColumnInfo(defaultValue = "0") val sort_order: Int
)

@Entity(tableName = "saved_workout_exercise")
data class SavedWorkoutExerciseRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val workout_id: Long,
    val exercise_id: Long,
    @ColumnInfo(defaultValue = "0") val sort_order: Int,
    @ColumnInfo(defaultValue = "0") val fill: Int,
    @ColumnInfo(defaultValue = "0") val superset: Int
)

@Entity(tableName = "saved_workout_set")
data class SavedWorkoutSetRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val item_id: Long,
    @ColumnInfo(defaultValue = "0") val sort_order: Int,
    @ColumnInfo(defaultValue = "0") val weight: Double,
    @ColumnInfo(defaultValue = "0") val reps: Int,
    @ColumnInfo(defaultValue = "0") val distance: Double,
    @ColumnInfo(defaultValue = "0") val duration: Long,
    @ColumnInfo(defaultValue = "0") val set_type: Int
)

// ---- Workouts and routines (#21, #106) ------------------------------------------------------------------------------

@Entity(tableName = "routine")
data class RoutineRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val notes: String?,
    @ColumnInfo(defaultValue = "0") val sort_order: Int,
    /** Training blocks (#218, v29): `TrainingBlocks.encode`, one block per line, or null for none. */
    val blocks: String? = null,
    /** The day the first block starts (ISO). */
    val block_start: String? = null,
    /** 1 when the blocks start again after the last one. */
    @ColumnInfo(defaultValue = "0") val block_repeat: Int = 0
)

@Entity(tableName = "routine_day")
data class RoutineDayRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val routine_id: Long,
    val name: String,
    @ColumnInfo(defaultValue = "0") val workout_id: Long,
    @ColumnInfo(defaultValue = "0") val sort_order: Int
)

@Entity(tableName = "workout_origin")
data class WorkoutOriginRow(
    @PrimaryKey val date: String,
    val workout_id: Long,
    val routine_day_id: Long?
)

@Entity(tableName = "routine_day_exercise")
data class RoutineDayExerciseRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val day_id: Long,
    val exercise_id: Long,
    @ColumnInfo(defaultValue = "0") val sort_order: Int,
    @ColumnInfo(defaultValue = "0") val fill: Int,
    @ColumnInfo(defaultValue = "0") val superset: Int,
    val rest_seconds: Int?,
    val rest_after_seconds: Int?
)

@Entity(tableName = "routine_day_set")
data class RoutineDaySetRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val item_id: Long,
    @ColumnInfo(defaultValue = "0") val sort_order: Int,
    @ColumnInfo(defaultValue = "0") val weight: Double,
    @ColumnInfo(defaultValue = "0") val reps: Int,
    @ColumnInfo(defaultValue = "0") val distance: Double,
    @ColumnInfo(defaultValue = "0") val duration: Long,
    @ColumnInfo(defaultValue = "0") val set_type: Int,
    val rest_seconds: Int?,
    val metric: Double?,
    val live_seconds: Int? = null,
    val intensity: Int? = null,
    val session_type: Int? = null,
    val zone: Int? = null
)

// ---- Body ---------------------------------------------------------------------------------------------------------

@Entity(tableName = "measurement")
data class MeasurementRow(
    @PrimaryKey val name: String,
    @ColumnInfo(defaultValue = "''") val unit: String,
    @ColumnInfo(defaultValue = "999") val sort_order: Int,
    @ColumnInfo(defaultValue = "0") val goal_type: Int,
    @ColumnInfo(defaultValue = "0") val goal_value: Double,
    @ColumnInfo(defaultValue = "1") val enabled: Int,
    @ColumnInfo(defaultValue = "0") val custom: Int,
    val link: String?,
    @ColumnInfo(defaultValue = "0") val edited: Int,
    val display_unit: String?,
    /** A medication (#199), shown only on the Medications screen. */
    @ColumnInfo(defaultValue = "0") val medication: Int = 0,
    /** The reference range (#211), in [unit]: either end may be missing. */
    val ref_low: Double? = null,
    val ref_high: Double? = null,
    /** A lab marker (#211), listed under LAB RESULTS. */
    @ColumnInfo(defaultValue = "0") val lab: Int = 0
)

@Entity(tableName = "mrecord", indices = [Index(value = ["date"], name = "idx_mr_date")])
data class MeasurementRecordRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    @ColumnInfo(defaultValue = "''") val unit: String,
    val date: String,
    @ColumnInfo(defaultValue = "''") val time: String,
    val value: Double,
    val comment: String?,
    val source: String,
    /** The scheduled dose this value was taken for (#202), or null for a value logged by hand. */
    val dose_id: Long? = null,
    /** When that dose was due, `yyyy-MM-dd HH:mm` (#202), so ticking it twice never logs it twice. */
    val due: String? = null,
    /** Where an injected dose went (#210): an `InjectionSites` key, or null. */
    val site: String? = null
)

// ---- Medication schedules (#201, #202, #204) ----------------------------------------------------------------------

/** A dose schedule: on and off phases that run in order, the whole cycle again when [repeats] is 1. */
@Entity(tableName = "med_schedule")
data class MedScheduleRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val notes: String?,
    @ColumnInfo(defaultValue = "0") val repeats: Int,
    @ColumnInfo(defaultValue = "0") val sort_order: Int
)

/** One phase: on ([active] 1, holding doses) or off, [length] days, weeks, months or years ([unit] d, w, m, y). */
@Entity(tableName = "med_phase")
data class MedPhaseRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val schedule_id: Long,
    @ColumnInfo(defaultValue = "0") val position: Int,
    @ColumnInfo(defaultValue = "1") val active: Int,
    @ColumnInfo(defaultValue = "1") val length: Int,
    @ColumnInfo(defaultValue = "'w'") val unit: String
)

/** One dose in an on phase: which medication, how much, at what time and on which days (`MedDays`). */
@Entity(tableName = "med_dose")
data class MedDoseRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val phase_id: Long,
    @ColumnInfo(defaultValue = "0") val position: Int,
    val medication: String,
    @ColumnInfo(defaultValue = "0") val amount: Double,
    @ColumnInfo(defaultValue = "''") val unit: String,
    @ColumnInfo(defaultValue = "'08:00'") val time: String,
    @ColumnInfo(defaultValue = "''") val days: String,
    val comment: String?,
    /** A taper or step-up (#234, v28): [step] added every [step_days] days of the phase, stopping at [step_limit]. */
    @ColumnInfo(defaultValue = "0") val step: Double = 0.0,
    @ColumnInfo(defaultValue = "0") val step_days: Int = 0,
    val step_limit: Double? = null
)

/** A schedule running from [start]; [pauses] lists `from/to` ranges (an open one ends `/`), [ended_on] the first day without doses. */
@Entity(tableName = "med_activation")
data class MedActivationRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val schedule_id: Long,
    val start: String,
    @ColumnInfo(defaultValue = "''") val pauses: String,
    val ended_on: String?
)

/** A dose that was skipped (#202, #203): its dose and due time. A taken dose is its value in `mrecord`. */
@Entity(tableName = "med_dose_status", indices = [Index(value = ["dose_id", "due"], name = "idx_med_status", unique = true)])
data class MedDoseStatusRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val dose_id: Long,
    val due: String,
    @ColumnInfo(defaultValue = "'skipped'") val status: String
)

/** A changed amount (#204) for one activation's dose from [from_date], to [to_date] or the end when null. */
@Entity(tableName = "med_dose_change")
data class MedDoseChangeRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val activation_id: Long,
    val dose_id: Long,
    val from_date: String,
    val to_date: String?,
    val amount: Double
)

/**
 * A medication's own details (#209, #210), one row per medication name, made when one is first set: the [supply] on
 * hand when counted at [supply_from] (`yyyy-MM-dd HH:mm:ss`; values taken after it count down), a warning [warn_days]
 * before it runs out, an [expires] date, and whether doses are [injected] (each then records its site).
 */
@Entity(tableName = "med_info")
data class MedInfoRow(
    @PrimaryKey val medication: String,
    val supply: Double?,
    val supply_from: String?,
    @ColumnInfo(defaultValue = "7") val warn_days: Int,
    val expires: String?,
    @ColumnInfo(defaultValue = "0") val injected: Int,
    /** Taken only when needed (#233, v28), with an optional most doses a day. */
    @ColumnInfo(defaultValue = "0") val as_needed: Int = 0,
    val max_per_day: Int? = null
)

/** A symptom the user rates (#207), in their own list's order. */
@Entity(tableName = "symptom")
data class SymptomRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    @ColumnInfo(defaultValue = "0") val sort_order: Int
)

/** A symptom's rating, 0–10, on a date (#207): one per symptom and day. */
@Entity(tableName = "symptom_rating", indices = [Index(value = ["symptom_id", "date"], name = "idx_symptom_day", unique = true)])
data class SymptomRatingRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val symptom_id: Long,
    val date: String,
    val rating: Int,
    val comment: String?
)

// ---- Photos and settings --------------------------------------------------------------------------------------------

@Entity(
    tableName = "photo",
    indices = [Index(value = ["date"], name = "idx_photo_date"), Index(value = ["hash"], name = "idx_photo_hash", unique = true)]
)
data class PhotoRow(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val file: String,
    val date: String?,
    val taken_at: String?,
    val date_source: String,
    @ColumnInfo(defaultValue = "''") val pose: String,
    val note: String?,
    val original_name: String?,
    val hash: String?,
    @ColumnInfo(defaultValue = "0") val added_at: Long
)

@Entity(tableName = "meta")
data class MetaRow(
    @PrimaryKey val k: String,
    val v: String?
)
