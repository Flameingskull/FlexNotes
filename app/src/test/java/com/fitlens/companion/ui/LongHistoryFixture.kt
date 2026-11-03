package com.fitlens.companion.ui

import com.fitlens.companion.data.BodyPart
import com.fitlens.companion.data.Category
import com.fitlens.companion.data.DateSources
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Exercise
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.LibraryPart
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.MedActivation
import com.fitlens.companion.data.MedDays
import com.fitlens.companion.data.MedDose
import com.fitlens.companion.data.MedInfo
import com.fitlens.companion.data.MedPhase
import com.fitlens.companion.data.MedPlan
import com.fitlens.companion.data.MedRun
import com.fitlens.companion.data.MedSchedule
import com.fitlens.companion.data.NotesPart
import com.fitlens.companion.data.PhaseUnit
import com.fitlens.companion.data.Photo
import com.fitlens.companion.data.PhotoPart
import com.fitlens.companion.data.SET_ORDER
import com.fitlens.companion.data.SetPart
import com.fitlens.companion.data.SetRow
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Sources
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A phone with years of history, for the function tests (#333): what [ScreenFixture] (five weeks) can't show. A graph
 * range of 1 month, 3 months, 6 months or 1 year draws the same on five weeks of data, so only years of it can prove a
 * range includes and leaves out the right entries.
 *
 * Three years and eight weeks of thin weekly data, from [START] (Mon 4 Sep 2023) to [END] (Fri 30 Oct 2026):
 * - **Sets:** bench press every Monday (two sets), easy runs every other Wednesday, and a squat that was last logged on
 *   [OLD_LAST], over two years before the end. Three sparse stretches with nothing logged ([GAPS]). FitNotes sets up to
 *   [FITNOTES_UNTIL], typed ones after it, and both on [END] itself.
 * - **Body values:** Bodyweight every Sunday (with its own gap, [BODY_GAP]) and Waist circumference monthly, FitNotes
 *   then typed, and the medication doses taken in the last fortnight.
 * - **Photos:** one on the 15th of every month, from [FIRST_PHOTO] to [LAST_PHOTO].
 * - **Medication:** one ongoing schedule from [MED_START] ([MED_RUNS], [MED_INFOS]). On Fridays, [END] included, three
 *   doses are due at 08:00 ([KEY_0800]): two doses of Metformin and an injected Semaglutide; Vitamin D follows at
 *   08:05 ([KEY_0805]). None is taken on [END], so all four are open.
 *
 * Everything is fixed (made-up data, [clock] stands still at 10:00 on [END]), so a test can say exactly what a range
 * should include. Build one with [snapshot]; for a real database use `ReminderHarness.loadPlan(MED_RUNS, MED_INFOS)`.
 */
internal object LongHistoryFixture {

    private val end: LocalDate = LocalDate.parse("2026-10-30")

    /** The first logged day: Mon 4 Sep 2023. */
    const val START = "2023-09-04"

    /** The day the phone says it is: Fri 30 Oct 2026. */
    const val END = "2026-10-30"

    /** A fixed clock at [time] (`HH:mm`) on [date], in UTC. */
    fun clockAt(time: String, date: String = END): Clock = Clock.fixed(Instant.parse("${date}T$time:00Z"), ZoneOffset.UTC)

    /** The phone's clock: 10:00 on [END]. */
    val clock: Clock = clockAt("10:00")

    // ---- dates a graph range should include or leave out ---------------------------------------------------------

    private fun back(months: Long = 0, years: Long = 0): String = end.minusMonths(months).minusYears(years).format(Dates.ISO)

    /** Where each graph range starts, counted back from [END]: an entry on this date is inside that range. */
    val ONE_MONTH_AGO: String = back(1)
    val THREE_MONTHS_AGO: String = back(3)
    val SIX_MONTHS_AGO: String = back(6)
    val ONE_YEAR_AGO: String = back(years = 1)
    val TWO_YEARS_AGO: String = back(years = 2)

    /** Three stretches with no sets (a holiday, a break, an injury), each from a Monday to a Monday. */
    val GAPS: List<ClosedRange<String>> = listOf("2024-03-04".."2024-05-27", "2025-06-02".."2025-08-25", "2026-01-05".."2026-02-23")

    /** A stretch with no Bodyweight values. */
    val BODY_GAP: ClosedRange<String> = "2024-12-02".."2025-02-23"

    /** Sets and values up to this date came from FitNotes; later ones were typed in FlexNotes. */
    const val FITNOTES_UNTIL = "2025-06-30"

    /** The squat's last day, a Monday over two years before [END]. */
    const val OLD_LAST = "2024-06-24"

    /** The notes on the squat's first and last day, the oldest text in the fixture. */
    const val OLD_FIRST_NOTE = "First squat day of the cycle, bar only"
    const val OLD_LAST_NOTE = "Last squat day before the knee problem"

    const val FIRST_PHOTO = "2023-10-15"
    const val LAST_PHOTO = "2026-10-15"

    // ---- names ---------------------------------------------------------------------------------------------------

    const val EX_BENCH = 1L
    const val EX_SQUAT = 2L
    const val EX_RUN = 3L
    const val BENCH = "Barbell Bench Press"
    const val SQUAT = "Barbell Back Squat"
    const val RUN = "Easy run"
    const val BODYWEIGHT = "Bodyweight"
    const val WAIST = "Waist circumference"
    const val METFORMIN = "Metformin"
    const val SEMAGLUTIDE = "Semaglutide"
    const val VITAMIN_D = "Vitamin D"

    // ---- the medication plan -------------------------------------------------------------------------------------

    /** The day the morning schedule starts. */
    const val MED_START = "2025-01-06"

    /** Three doses are due at this time on [END]: two of Metformin and the injected Semaglutide. */
    const val KEY_0800 = "$END 08:00"

    /** Vitamin D, five minutes later. */
    const val KEY_0805 = "$END 08:05"

    private val medDoses = listOf(
        MedDose(1, METFORMIN, 500.0, "mg", "08:00"),
        MedDose(2, METFORMIN, 250.0, "mg", "08:00"),
        // Fridays only (ISO 5), so [END] has it and most days don't.
        MedDose(3, SEMAGLUTIDE, 0.5, "mg", "08:00", MedDays(weekdays = setOf(5))),
        MedDose(4, VITAMIN_D, 1000.0, "IU", "08:05")
    )

    /** The schedule, as ids 1 to 4 for its dose: for `ReminderHarness.loadPlan`, which saves them anew in the same order. */
    val MED_RUNS: List<MedRun> = listOf(
        MedRun(MedSchedule(1, "Morning medication", phases = listOf(MedPhase(1, true, 1, PhaseUnit.ONGOING, medDoses))), MED_START)
    )

    /** Semaglutide is injected, so its Taken button names the suggested site. */
    val MED_INFOS: List<MedInfo> = listOf(MedInfo(SEMAGLUTIDE, injected = true))

    // ---- building the snapshot -----------------------------------------------------------------------------------

    private fun sourceOn(date: String): String = if (date <= FITNOTES_UNTIL) Sources.FITNOTES else Sources.FLEXNOTES

    private fun library(): LibraryPart {
        val categories = listOf(
            Category(1, "Chest", 0xFFE53935.toInt(), 1, Sources.FITNOTES),
            Category(2, "Legs", 0xFF43A047.toInt(), 2, Sources.FITNOTES),
            Category(3, "Cardio", 0xFF8E24AA.toInt(), 3)
        ).associateBy { it.id }
        val exercises = listOf(
            Exercise(EX_BENCH, BENCH, 1, ExerciseTypes.WEIGHT_REPS, null, Sources.FITNOTES),
            Exercise(EX_SQUAT, SQUAT, 2, ExerciseTypes.WEIGHT_REPS, "Taken out of the plan.", Sources.FITNOTES),
            Exercise(EX_RUN, RUN, 3, ExerciseTypes.DISTANCE_TIME, null, Sources.FLEXNOTES)
        ).associateBy { it.id }
        return LibraryPart(categories, exercises, emptyList(), emptyList(), emptyMap())
    }

    private fun sets(): SetPart {
        var next = 1L
        fun row(
            exercise: Long, date: String, kg: Double = 0.0, reps: Int = 0, distance: Double = 0.0, seconds: Int = 0,
            source: String = sourceOn(date)
        ): SetRow {
            val id = next++
            return SetRow(
                id = id, exerciseId = exercise, date = date, weightKg = kg, reps = reps, distance = distance,
                durationSec = seconds, isPr = false, comment = null, source = source, position = id
            )
        }
        val rows = ArrayList<SetRow>()
        var day = LocalDate.parse(START)
        var week = 0
        while (!day.isAfter(end)) {
            val date = day.format(Dates.ISO)
            if (GAPS.none { date in it }) {
                val kg = Math.round((60.0 + 0.2 * week) * 2) / 2.0
                rows += row(EX_BENCH, date, kg - 5.0, 8)
                rows += row(EX_BENCH, date, kg, 5)
                if (date <= OLD_LAST) rows += row(EX_SQUAT, date, 80.0 + 0.5 * week, 5)
                if (week % 2 == 0) {
                    rows += row(EX_RUN, day.plusDays(2).format(Dates.ISO), distance = 5.0 + (week % 6) * 0.2, seconds = 1800 + (week % 5) * 30)
                }
            }
            day = day.plusWeeks(1)
            week++
        }
        // The last day has a FitNotes set and a typed one side by side.
        rows += row(EX_BENCH, END, 70.0, 8, source = Sources.FITNOTES)
        rows += row(EX_BENCH, END, 75.0, 5, source = Sources.FLEXNOTES)
        return SetPart(rows.sortedWith(SET_ORDER), false)
    }

    private fun notes() = NotesPart(
        workoutComments = emptyMap(),
        workoutTimes = emptyMap(),
        exerciseComments = mapOf(
            START to mapOf(EX_SQUAT to OLD_FIRST_NOTE),
            OLD_LAST to mapOf(EX_SQUAT to OLD_LAST_NOTE)
        )
    )

    private fun body(): BodyPart {
        val defs = listOf(
            MeasurementDef(BODYWEIGHT, "kg", 0, 0, 0.0, true),
            MeasurementDef(WAIST, "cm", 1, 0, 0.0, true),
            MeasurementDef(METFORMIN, "mg", 2, 0, 0.0, true, medication = true),
            MeasurementDef(SEMAGLUTIDE, "mg", 3, 0, 0.0, true, medication = true),
            MeasurementDef(VITAMIN_D, "IU", 4, 0, 0.0, true, medication = true)
        )
        var id = 1L
        fun record(
            name: String, unit: String, date: String, time: String, value: Double, source: String,
            doseId: Long? = null, due: String? = null
        ) = MRecord(id++, name, unit, date, time, value, null, source, doseId, due)

        val records = ArrayList<MRecord>()
        // Bodyweight every Sunday, drifting down, with a stretch of none.
        var sunday = LocalDate.parse(START).plusDays(6)
        var week = 0
        while (!sunday.isAfter(end)) {
            val date = sunday.format(Dates.ISO)
            if (date !in BODY_GAP) {
                val kg = Math.round((88.0 - 0.02 * week + ((week % 4) - 1.5) * 0.2) * 10) / 10.0
                records += record(BODYWEIGHT, "kg", date, "07:30:00", kg, sourceOn(date))
            }
            sunday = sunday.plusWeeks(1)
            week++
        }
        // Waist on the first of each month.
        var first = LocalDate.parse("2023-10-01")
        var month = 0
        while (!first.isAfter(end)) {
            val date = first.format(Dates.ISO)
            records += record(WAIST, "cm", date, "07:35:00", Math.round((92.0 - 0.1 * month) * 10) / 10.0, sourceOn(date))
            first = first.plusMonths(1)
            month++
        }
        // The last fortnight's doses, taken a few minutes after they were due: Metformin 500 mg and Vitamin D every day.
        // The second Metformin and Semaglutide (on its Friday a week ago) are left, as are all of [END]'s.
        for (k in 1L..14L) {
            val date = end.minusDays(k).format(Dates.ISO)
            records += record(METFORMIN, "mg", date, "08:03:00", 500.0, Sources.FLEXNOTES, doseId = 1, due = "$date 08:00")
            records += record(VITAMIN_D, "IU", date, "08:07:00", 1000.0, Sources.FLEXNOTES, doseId = 4, due = "$date 08:05")
        }
        val lastFriday = end.minusDays(7).format(Dates.ISO)
        records += record(SEMAGLUTIDE, "mg", lastFriday, "08:04:00", 0.5, Sources.FLEXNOTES, doseId = 3, due = "$lastFriday 08:00")
        val plan = MedPlan(
            MED_RUNS.map { it.schedule },
            listOf(MedActivation(1, 1, MED_START)),
            info = MED_INFOS.associateBy { it.medication }
        )
        return BodyPart(defs, records, "kg", plan = plan)
    }

    private fun photos(): PhotoPart {
        val poses = listOf("Front", "Side", "Back")
        val list = ArrayList<Photo>()
        var day = LocalDate.parse(FIRST_PHOTO)
        var n = 1L
        while (!day.isAfter(end)) {
            val date = day.format(Dates.ISO)
            list += Photo(n, "photo_$n.jpg", date, "${date}T07:40:00", DateSources.EXIF, poses[(n % 3).toInt()], null, "IMG_$n.jpg")
            day = day.plusMonths(1)
            n++
        }
        return PhotoPart(list)
    }

    /** The whole phone: sets, notes, body values, medication and photos, as [Snapshot] holds them. Built fresh each call. */
    fun snapshot(): Snapshot = Snapshot(library(), sets(), notes(), body(), photos(), "kg", File("photos"))
}
