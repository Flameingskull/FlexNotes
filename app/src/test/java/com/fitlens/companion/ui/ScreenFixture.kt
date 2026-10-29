package com.fitlens.companion.ui

import com.fitlens.companion.data.Category
import com.fitlens.companion.data.DateSources
import com.fitlens.companion.data.Exercise
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.LibraryPart
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.BodyPart
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.NotesPart
import com.fitlens.companion.data.Photo
import com.fitlens.companion.data.PhotoPart
import com.fitlens.companion.data.PlannedExercise
import com.fitlens.companion.data.PlannedSet
import com.fitlens.companion.data.Routine
import com.fitlens.companion.data.RoutineDay
import com.fitlens.companion.data.SessionIntensity
import com.fitlens.companion.data.SessionKinds
import com.fitlens.companion.data.SET_ORDER
import com.fitlens.companion.data.SetPart
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.SetTypes
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Sources
import com.fitlens.companion.data.WorkoutOrigin
import com.fitlens.companion.data.WorkoutTime
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * One realistic [Snapshot] for the whole-screen layout checks (#323): the data a real, busy phone would hold, built to
 * stress layout rather than to look tidy. It has long exercise, category, measurement and workout names; sets
 * imported from FitNotes and typed in FlexNotes on the same day; kilograms, one exercise in pounds, a distance and
 * time exercise and a Session; set comments, a PR, a warm-up, a superset and ticked sets; a workout comment and
 * exercise comments; body values; a readiness check-in; progress photos; and a workout with named days. Earlier weeks
 * of history give the History and Graph tabs points to draw.
 *
 * Everything is fixed: the day is [DATE], Tuesday 27 October 2026, and [clock] stands still at 10:00 that morning, so a
 * golden image never changes with the real date. Made-up data only.
 */
internal object ScreenFixture {

    /** The day every screen is drawn on: Tue 27 Oct 2026. */
    const val DATE = "2026-10-27"

    /** The phone's clock while a screen is drawn: 10:00 on [DATE]. */
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC)

    const val EX_ROMANIAN = 1L
    const val EX_BENCH = 2L
    const val EX_PULLUP = 3L
    const val EX_RAISE = 4L
    const val EX_RUN = 5L
    const val EX_SESSION = 6L

    const val ROMANIAN = "Single-arm Dumbbell Romanian Deadlift (Paused)"
    const val BENCH = "Incline Barbell Bench Press"
    const val PULLUP = "Weighted Pull-up (Neutral Grip)"
    const val ARM = "Left upper arm circumference (relaxed, mid-point)"

    private const val LAST_WEEK = "2026-10-20"
    private val EARLIER = listOf("2026-10-20", "2026-10-13", "2026-10-06", "2026-09-29", "2026-09-22")

    private fun colour(argb: Long): Int = argb.toInt()

    private fun library(): LibraryPart {
        val categories = listOf(
            Category(1, "Chest", colour(0xFFE53935), 1, Sources.FITNOTES),
            Category(2, "Back", colour(0xFF1E88E5), 2, Sources.FITNOTES),
            Category(3, "Legs and posterior chain", colour(0xFF43A047), 3),
            Category(4, "Shoulders", colour(0xFFFFB300), 4),
            Category(5, "Cardio", colour(0xFF8E24AA), 5),
            Category(6, "Conditioning and sports sessions", colour(0xFF00ACC1), 6)
        ).associateBy { it.id }
        val exercises = listOf(
            Exercise(EX_ROMANIAN, ROMANIAN, 3, ExerciseTypes.WEIGHT_REPS, "Keep the hips square.", Sources.FLEXNOTES),
            Exercise(EX_BENCH, BENCH, 1, ExerciseTypes.WEIGHT_REPS, null, Sources.FITNOTES, favourite = true),
            Exercise(EX_PULLUP, PULLUP, 2, ExerciseTypes.WEIGHT_REPS, null, Sources.FITNOTES),
            Exercise(EX_RAISE, "Dumbbell Lateral Raise", 4, ExerciseTypes.WEIGHT_REPS, null, Sources.FLEXNOTES, weightUnit = "lbs"),
            Exercise(EX_RUN, "Treadmill intervals (incline 2%)", 5, ExerciseTypes.DISTANCE_TIME, null, Sources.FLEXNOTES),
            Exercise(EX_SESSION, "Brazilian jiu-jitsu class", 6, ExerciseTypes.SESSION, null, Sources.FLEXNOTES, sessionKind = SessionKinds.COMBAT)
        ).associateBy { it.id }
        val workout = Routine(
            id = 1,
            name = "Upper and lower strength block with a long name",
            days = listOf(
                RoutineDay(
                    1, "Push Day", listOf(
                        PlannedExercise(EX_BENCH, sets = listOf(PlannedSet(weightKg = 80.0, reps = 8), PlannedSet(weightKg = 85.0, reps = 6))),
                        PlannedExercise(EX_RAISE, sets = listOf(PlannedSet(weightKg = 9.0, reps = 12)))
                    )
                ),
                RoutineDay(2, "Pull Day", listOf(PlannedExercise(EX_PULLUP, sets = listOf(PlannedSet(weightKg = 15.0, reps = 6))))),
                RoutineDay(3, "Leg Day", listOf(PlannedExercise(EX_ROMANIAN, sets = listOf(PlannedSet(weightKg = 40.0, reps = 10)))))
            )
        )
        return LibraryPart(categories, exercises, emptyList(), listOf(workout), mapOf(DATE to WorkoutOrigin(DATE, 1, 1)))
    }

    private var nextId = 1000L

    private fun set(
        exercise: Long,
        date: String,
        weightKg: Double = 0.0,
        reps: Int = 0,
        source: String = Sources.FLEXNOTES,
        id: Long = nextId++,
        distance: Double = 0.0,
        durationSec: Int = 0,
        isPr: Boolean = false,
        comment: String? = null,
        setType: Int = SetTypes.WORKING,
        rpe: Double? = null,
        superset: Int = 0,
        done: Boolean = false,
        liveSec: Int? = null,
        intensity: Int? = null,
        zone: Int? = null
    ) = SetRow(
        id, exercise, date, weightKg, reps, distance, durationSec, isPr, comment, source, setType, rpe,
        position = id, superset = superset, done = done, liveSec = liveSec, intensity = intensity, zone = zone
    )

    private fun sets(): SetPart {
        nextId = 1000L
        val today = listOf(
            set(EX_BENCH, DATE, 60.0, 10, Sources.FITNOTES, setType = SetTypes.WARMUP, done = true),
            set(EX_BENCH, DATE, 80.0, 8, Sources.FITNOTES, comment = "Bar speed felt good, no grind", done = true),
            set(EX_BENCH, DATE, 85.0, 6, Sources.FITNOTES, isPr = true, rpe = 8.5, done = true),
            set(EX_BENCH, DATE, 85.0, 5, Sources.FLEXNOTES),
            set(EX_ROMANIAN, DATE, 40.0, 10, comment = "Left side felt tighter, paused for two full seconds at the bottom"),
            set(EX_ROMANIAN, DATE, 40.0, 10),
            set(EX_PULLUP, DATE, 15.0, 6, Sources.FITNOTES, superset = 1),
            set(EX_PULLUP, DATE, 15.0, 6, superset = 1),
            set(EX_RAISE, DATE, 9.0718, 12, superset = 1),
            set(EX_RUN, DATE, distance = 5.2, durationSec = 1860),
            set(
                EX_SESSION, DATE, durationSec = 3600, liveSec = 1800, rpe = 7.0, zone = 3,
                intensity = SessionIntensity.CLASS, comment = "Positional sparring, three rounds"
            )
        )
        // Newest first in EARLIER: the further back, the lighter and shorter.
        val history = EARLIER.flatMapIndexed { i, d ->
            listOf(
                set(EX_BENCH, d, 75.0 - i * 2.5, 8, Sources.FITNOTES),
                set(EX_BENCH, d, 80.0 - i * 2.5, 6, Sources.FITNOTES),
                set(EX_ROMANIAN, d, 37.5 - i * 2.5, 10),
                set(EX_PULLUP, d, 12.5 - i * 1.25, 6, Sources.FITNOTES),
                set(EX_RUN, d, distance = 5.0 - i * 0.3, durationSec = 1800 - i * 20)
            )
        }
        return SetPart((history + today).sortedWith(SET_ORDER), false)
    }

    private fun notes(timerRunning: Boolean) = NotesPart(
        workoutComments = mapOf(
            DATE to listOf(
                "Good session overall. Shoulder felt a little tight in the warm-up, so the first incline set stayed " +
                    "light and I added a mobility block before the pull-ups."
            )
        ),
        workoutTimes = mapOf(
            DATE to listOf(
                WorkoutTime(DATE, "2026-10-27 09:20:00", if (timerRunning) "" else "2026-10-27 10:25:00")
            )
        ),
        exerciseComments = mapOf(
            DATE to mapOf(EX_BENCH to "Elbows tucked in more than usual. Pressing through the heels helped the lockout."),
            LAST_WEEK to mapOf(EX_BENCH to "Try a two second pause on the chest next week.")
        )
    )

    private fun body(): BodyPart {
        val defs = listOf(
            MeasurementDef("Bodyweight", "kg", 0, 0, 0.0, true),
            MeasurementDef(ARM, "cm", 1, 0, 0.0, true),
            MeasurementDef("Mood", "/5", 2, 0, 0.0, true, custom = true),
            MeasurementDef("Stress", "/5", 3, 0, 0.0, true, custom = true),
            MeasurementDef("Energy", "/5", 4, 0, 0.0, true, custom = true)
        )
        var id = 1L
        fun record(name: String, unit: String, date: String, value: Double, source: String = Sources.FLEXNOTES, time: String = "07:30:00") =
            MRecord(id++, name, unit, date, time, value, null, source)
        val records = listOf(
            record("Bodyweight", "kg", DATE, 82.4, Sources.FITNOTES),
            record(ARM, "cm", DATE, 36.5),
            record("Mood", "/5", DATE, 4.0),
            record("Stress", "/5", DATE, 2.0),
            record("Energy", "/5", DATE, 3.0)
        ) + EARLIER.mapIndexed { i, d -> record("Bodyweight", "kg", d, 83.4 + i * 0.2, Sources.FITNOTES) } +
            EARLIER.mapIndexed { i, d -> record(ARM, "cm", d, 35.8 + i * 0.1) }
        return BodyPart(defs, records, "kg")
    }

    private fun photos() = PhotoPart(
        listOf(
            Photo(1, "front.jpg", DATE, "2026-10-27T07:40:00", DateSources.EXIF, "Front", "Morning", "IMG_0001.jpg"),
            Photo(2, "side.jpg", DATE, "2026-10-27T07:41:00", DateSources.EXIF, "Side", null, "IMG_0002.jpg"),
            Photo(3, "back.jpg", LAST_WEEK, "2026-10-20T07:40:00", DateSources.EXIF, "Back", null, "IMG_0003.jpg")
        )
    )

    /** The fixture. With [timerRunning] the day's workout timer has started and not stopped (running since 09:20). */
    fun snapshot(timerRunning: Boolean = false): Snapshot =
        Snapshot(library(), sets(), notes(timerRunning), body(), photos(), "kg", File("photos"))
}
