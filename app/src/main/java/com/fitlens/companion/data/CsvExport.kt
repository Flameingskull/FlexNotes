package com.fitlens.companion.data

/**
 * Workouts and body data as CSV for spreadsheets (#31). These files are one-way: FlexNotes can't restore from them,
 * so the `.flexnotes` backup stays the only way back. Columns are fixed and documented in [WORKOUT_COLUMNS] and
 * [BODY_COLUMNS], so a spreadsheet built on one export keeps working with the next.
 *
 * Numbers use a dot for decimals whatever the phone's language, dates are ISO (`yyyy-MM-dd`), and every file
 * starts with a header row.
 */
object CsvExport {
    const val MIME = "text/csv"

    fun workoutColumns(unit: String) =
        listOf("Date", "Exercise", "Category", "Set", "Weight ($unit)", "Reps", "Distance", "Time (seconds)", "PR", "Comment", "set_type", "RPE", "Exercise comment", "Distance unit",
            "Custom metric", "Custom metric value", "Custom metric unit",
            "Hard or live (seconds)", "Intensity", "Session type", "Cardio zone", "Session load")

    val BODY_COLUMNS = listOf("Date", "Time", "Measurement", "Value", "Unit", "Comment")

    /** What the Data tools page shows under the export buttons. */
    val WORKOUT_COLUMNS = workoutColumns("kg or lbs").joinToString(", ")

    /**
     * Every set between [from] and [to] (inclusive ISO dates, null for open-ended), one row per set, in the order
     * they were logged. Weights are shown in [unit] ("kg" or "lbs"); "Set" counts from 1 per exercise per day.
     */
    fun workouts(snap: Snapshot, from: String?, to: String?, unit: String): String {
        val sb = StringBuilder()
        row(sb, workoutColumns(unit))
        val counters = HashMap<Pair<String, Long>, Int>()
        snap.sets.asSequence()
            .filter { inRange(it.date, from, to) }
            .forEach { s ->
                val date = s.date.take(10)
                val n = counters.merge(date to s.exerciseId, 1) { a, b -> a + b } ?: 1
                val ex = snap.exercises[s.exerciseId]
                val category = ex?.let { snap.categories[it.categoryId]?.name }
                val metric = ex?.let { ExerciseTypes.metricOf(it.type) }
                val weight = if (unit == "lbs") s.weightKg * KG_TO_LB else s.weightKg
                row(
                    sb, listOf(
                        date,
                        ex?.name ?: "",
                        category ?: "",
                        n.toString(),
                        if (s.weightKg != 0.0) fmtNum(weight, 2) else "",
                        if (s.reps > 0) s.reps.toString() else "",
                        if (s.distance > 0) fmtNum(s.distance, 2) else "",
                        if (s.durationSec > 0) s.durationSec.toString() else "",
                        if (s.isPr) "Yes" else "",
                        s.comment ?: "",
                        SetTypes.csv(s.setType),
                        s.rpe?.let { fmtNum(it, 1) } ?: "",
                        // The exercise's comment in that day's workout (#107), on every set row of the exercise.
                        snap.exerciseComments[date]?.get(s.exerciseId) ?: "",
                        // Added last (#7) so spreadsheets built on older exports keep their columns.
                        if (s.distance > 0) snap.distanceUnit(s.exerciseId) else "",
                        // A user-defined type's own metric (#14), also added last.
                        if (s.metric != null) metric?.metricName.orEmpty() else "",
                        s.metric?.let { fmtNum(it, 3) } ?: "",
                        if (s.metric != null) metric?.metricUnit.orEmpty() else "",
                        // A session's values (#240, #241), added last for the same reason.
                        s.liveSec?.toString() ?: "",
                        SessionIntensity.valid(s.intensity)?.let { csvIntensity(it) } ?: "",
                        SessionTypes.valid(s.sessionType)?.let { csvSessionType(it) } ?: "",
                        s.zone?.toString() ?: "",
                        if (ex != null && ExerciseTypes.isTimedSession(ex.type)) SessionLoad.of(s.rpe, s.durationSec)?.let { fmtNum(it, 1) } ?: "" else ""
                    )
                )
            }
        return sb.toString()
    }

    /** The CSV words for a session's intensity and type: fixed, so spreadsheets keep working. */
    fun csvIntensity(id: Int): String = when (id) {
        SessionIntensity.TECHNICAL -> "technical"
        SessionIntensity.HARD_LIVE -> "hard_live"
        SessionIntensity.FIGHT_PACE -> "fight_pace"
        else -> "class"
    }

    fun csvSessionType(id: Int): String = when (id) {
        SessionTypes.DRILLING -> "drilling"
        SessionTypes.LIVE_SPARRING -> "live_sparring"
        SessionTypes.CONDITIONING -> "conditioning"
        SessionTypes.MIXED -> "mixed"
        SessionTypes.COMPETITION -> "competition"
        SessionTypes.STRENGTH -> "strength"
        else -> "technical"
    }

    /** Every body tracker value between [from] and [to], one row per value, oldest first. */
    fun body(snap: Snapshot, from: String?, to: String?): String {
        val sb = StringBuilder()
        row(sb, BODY_COLUMNS)
        snap.records.filter { inRange(it.date, from, to) }.forEach { r ->
            row(sb, listOf(r.date.take(10), r.time, r.name, fmtNum(r.value, 3), r.unit, r.comment ?: ""))
        }
        return sb.toString()
    }

    val MEDICATION_COLUMNS = listOf("Date", "Due", "Taken at", "Medication", "Amount", "Unit", "Status", "Schedule", "Dose change", "Comment", "Site")

    /**
     * The Medications dose log (#205) for a doctor: every scheduled dose from [from] to [to] with what became of it
     * (Taken, Skipped, Missed, Due), and every dose logged by hand ("Logged"), for [names] or every medication, with the
     * injection site of a sited dose (#223). Only
     * medication data: no workouts, body values or photos.
     */
    fun medications(snap: Snapshot, from: String, to: String, names: Set<String> = emptySet(), today: String = Dates.today()): String {
        val sb = StringBuilder()
        row(sb, MEDICATION_COLUMNS)
        val meds = snap.medicationView
        MedSchedules.doseLog(snap.medPlan, meds.records, from, to, today, names).forEach { e ->
            val d = e.due
            val r = e.record
            val amount = r?.value ?: d?.amount
            row(
                sb, listOf(
                    e.date,
                    d?.time.orEmpty(),
                    r?.time?.take(5).orEmpty(),
                    e.medication,
                    amount?.let { fmtNum(it, 3) }.orEmpty(),
                    r?.unit ?: d?.dose?.unit.orEmpty(),
                    when (e.outcome) {
                        MedSchedules.Outcome.TAKEN -> "Taken"
                        MedSchedules.Outcome.SKIPPED -> "Skipped"
                        MedSchedules.Outcome.MISSED -> "Missed"
                        MedSchedules.Outcome.DUE -> "Due"
                        null -> "Logged"
                    },
                    d?.schedule?.name.orEmpty(),
                    if (d?.change != null) "Changed from ${fmtNum(d.planned, 3)}" else "",
                    r?.comment ?: d?.dose?.comment.orEmpty(),
                    // Where an injected dose went (#223), added last so older spreadsheets keep their columns.
                    InjectionSite.of(r?.site)?.label.orEmpty()
                )
            )
        }
        return sb.toString()
    }

    /** How many sets and workout days, or body values, an export would hold. For the page's preview line. */
    fun countWorkouts(snap: Snapshot, from: String?, to: String?): Pair<Int, Int> {
        val sets = snap.sets.filter { inRange(it.date, from, to) }
        return sets.size to sets.map { it.date.take(10) }.distinct().size
    }

    fun countBody(snap: Snapshot, from: String?, to: String?): Int = snap.records.count { inRange(it.date, from, to) }

    fun fileName(kind: String, from: String?, to: String?): String {
        val range = when {
            from == null && to == null -> "all"
            else -> "${from ?: "start"}_to_${to ?: Dates.today()}"
        }
        return "FlexNotes_${kind}_$range.csv"
    }

    private fun inRange(date: String, from: String?, to: String?): Boolean {
        val d = date.take(10)
        return (from == null || d >= from) && (to == null || d <= to)
    }

    /** RFC 4180: quote a field when it holds a comma, quote or line break, doubling any quotes inside. */
    private fun row(sb: StringBuilder, fields: List<String>) {
        fields.forEachIndexed { i, f ->
            if (i > 0) sb.append(',')
            if (f.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                sb.append('"').append(f.replace("\"", "\"\"")).append('"')
            } else {
                sb.append(f)
            }
        }
        sb.append("\r\n")
    }

    private const val KG_TO_LB = WeightUnits.LB_PER_KG
}
