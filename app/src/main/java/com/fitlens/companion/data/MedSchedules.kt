package com.fitlens.companion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Medication schedules (#201), built like a workout: a schedule is its **phases** in order, each on (holding doses) or
 * off, lasting days, weeks, months or years, and optionally repeating the whole cycle. A schedule runs once it's
 * **activated** from a start date (#202); several can run at once, each can be paused and ended. Taking a dose writes
 * an ordinary medication value tied to its dose and due time, so ticking twice never logs twice; a skipped dose is a
 * `med_dose_status` row. A **dose change** (#204) alters one activation's amount for a day or the rest of it, never the
 * schedule itself.
 *
 * The calculations ([dosesOn], [phaseOn]) are plain Kotlin over these models, tested in `MedSchedulesTest`. Months
 * and years are calendar ones (`plusMonths` / `plusYears`): a phase runs until the same date a month or year later, or
 * that month's last day when it has no such date. Writes refresh the body area, which holds the medications.
 */

/** How long a phase lasts. Stored as its [code]. */
enum class PhaseUnit(val code: String) {
    DAYS("d"), WEEKS("w"), MONTHS("m"), YEARS("y");

    fun add(date: LocalDate, n: Long): LocalDate = when (this) {
        DAYS -> date.plusDays(n)
        WEEKS -> date.plusWeeks(n)
        MONTHS -> date.plusMonths(n)
        YEARS -> date.plusYears(n)
    }

    companion object {
        fun of(code: String): PhaseUnit = entries.firstOrNull { it.code == code } ?: WEEKS
    }
}

/**
 * Which days of an on phase a dose is taken: every day, chosen weekdays (ISO numbers, Monday = 1), or every [every]
 * days counted from the phase's first day. Stored as text: "" (every day), "w:1,3,5" or "n:2"; anything unreadable
 * is every day.
 */
data class MedDays(val weekdays: Set<Int> = emptySet(), val every: Int = 1) {
    val everyDay: Boolean get() = weekdays.isEmpty() && every <= 1

    fun encode(): String = when {
        weekdays.isNotEmpty() -> "w:" + weekdays.sorted().joinToString(",")
        every > 1 -> "n:$every"
        else -> ""
    }

    /** Whether [date], [dayInPhase] days after its phase began (0 on the first day), is a dose day. */
    fun matches(date: LocalDate, dayInPhase: Long): Boolean = when {
        weekdays.isNotEmpty() -> date.dayOfWeek.value in weekdays
        every > 1 -> dayInPhase % every == 0L
        else -> true
    }

    companion object {
        fun decode(text: String?): MedDays {
            val t = text?.trim().orEmpty()
            return when {
                t.startsWith("w:") -> MedDays(
                    weekdays = t.drop(2).split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
                )
                t.startsWith("n:") -> MedDays(every = t.drop(2).trim().toIntOrNull()?.coerceIn(1, 365) ?: 1)
                else -> MedDays()
            }
        }
    }
}

/** One dose of an on phase. An [id] of 0 is one not saved yet. [time] is `HH:mm`. */
data class MedDose(
    val id: Long,
    val medication: String,
    val amount: Double,
    val unit: String,
    val time: String = "08:00",
    val days: MedDays = MedDays(),
    val comment: String? = null
)

/** One phase: on (with [doses]) or off, [length] [unit]s long. An [id] of 0 is one not saved yet. */
data class MedPhase(
    val id: Long,
    val on: Boolean,
    val length: Int,
    val unit: PhaseUnit,
    val doses: List<MedDose> = emptyList()
)

/** A schedule: its name, notes, phases in order, and whether the whole cycle repeats. */
data class MedSchedule(
    val id: Long,
    val name: String,
    val notes: String? = null,
    val repeats: Boolean = false,
    val sortOrder: Int = 0,
    val phases: List<MedPhase> = emptyList()
)

/** A pause from [from] until [to] (the day doses resume, exclusive), or still paused when [to] is null. */
data class MedPause(val from: String, val to: String?)

/**
 * A running schedule: from [start], with its [pauses], until [endedOn] (the first day without doses) when ended. While
 * paused no doses are due and the cycle doesn't advance: it carries on where it stopped.
 */
data class MedActivation(
    val id: Long,
    val scheduleId: Long,
    val start: String,
    val pauses: List<MedPause> = emptyList(),
    val endedOn: String? = null
) {
    val paused: Boolean get() = pauses.any { it.to == null }

    /** Whether it still runs on or after [date]: not ended by then. */
    fun runsOn(date: String): Boolean = endedOn == null || date < endedOn

    companion object {
        fun encodePauses(pauses: List<MedPause>): String = pauses.joinToString(";") { "${it.from}/${it.to.orEmpty()}" }

        fun decodePauses(text: String?): List<MedPause> =
            text.orEmpty().split(';').mapNotNull { part ->
                val bits = part.split('/')
                val from = bits.getOrNull(0)?.trim()?.takeIf { Dates.parse(it) != null } ?: return@mapNotNull null
                MedPause(from, bits.getOrNull(1)?.trim()?.takeIf { Dates.parse(it) != null })
            }
    }
}

/** A changed amount (#204): [doseId] of [activationId] takes [amount] from [fromDate] to [toDate], or on when null. */
data class MedDoseChange(
    val id: Long,
    val activationId: Long,
    val doseId: Long,
    val fromDate: String,
    val toDate: String?,
    val amount: Double
) {
    fun covers(date: String): Boolean = date >= fromDate && (toDate == null || date <= toDate)
}

/** Everything about medication schedules the screens need, held in the body area (#201, #202). */
class MedPlan(
    val schedules: List<MedSchedule> = emptyList(),
    val activations: List<MedActivation> = emptyList(),
    /** Skipped doses: their `doseId` to the due times skipped. */
    val skipped: Map<Long, Set<String>> = emptyMap(),
    val changes: List<MedDoseChange> = emptyList()
) {
    val schedulesById: Map<Long, MedSchedule> = schedules.associateBy { it.id }
    val activationsBySchedule: Map<Long, List<MedActivation>> = activations.groupBy { it.scheduleId }

    /** The activation of [scheduleId] that is running on [date] (started, not ended), if any. */
    fun current(scheduleId: Long, date: String = Dates.today()): MedActivation? =
        activationsBySchedule[scheduleId].orEmpty().lastOrNull { it.start <= date && it.runsOn(date) }
            ?: activationsBySchedule[scheduleId].orEmpty().lastOrNull { it.start > date && it.runsOn(date) }

    fun isSkipped(doseId: Long, due: String): Boolean = skipped[doseId]?.contains(due) == true

    companion object {
        val EMPTY = MedPlan()
    }
}

/** A dose due on a date: which activation, schedule, phase and dose, at what time, how much, and its [due] key. */
data class DueDose(
    val activation: MedActivation,
    val schedule: MedSchedule,
    val dose: MedDose,
    val date: String,
    /** `HH:mm`. */
    val time: String,
    /** The amount after any dose change (#204). */
    val amount: Double,
    /** The change that set [amount], if one did. */
    val change: MedDoseChange? = null
) {
    /** `yyyy-MM-dd HH:mm`: with the dose's id, what a taken value and a skip are keyed by. */
    val due: String get() = "$date $time"
}

/** Where an activation is on a date: its phase, the day within it (1-based) and how long it lasts. */
data class PhaseState(
    val index: Int,
    val phase: MedPhase,
    val day: Long,
    val days: Long,
    /** The phase's last day, counting no further pauses. */
    val lastDay: String,
    /** Which time round the cycle (1 for the first) when it repeats. */
    val cycle: Int
)

object MedSchedules {
    const val SKIPPED = "skipped"
    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** A time as `HH:mm`, or 08:00 when it can't be read. */
    fun cleanTime(text: String): String =
        runCatching { LocalTime.parse(text.trim()).format(HHMM) }.getOrNull()
            ?: runCatching { LocalTime.parse("0" + text.trim()).format(HHMM) }.getOrNull()
            ?: "08:00"

    /** Days of [a] up to (not including) [date] that fall inside a pause. */
    private fun pausedDaysBefore(a: MedActivation, start: LocalDate, date: LocalDate): Long =
        a.pauses.sumOf { p ->
            val from = maxOf(Dates.parse(p.from) ?: return@sumOf 0L, start)
            val to = minOf(p.to?.let { Dates.parse(it) } ?: date, date)
            if (to > from) ChronoUnit.DAYS.between(from, to) else 0L
        }

    private fun pausedOn(a: MedActivation, date: String): Boolean =
        a.pauses.any { date >= it.from && (it.to == null || date < it.to) }

    /**
     * The phase [a] is in on [date], or null when the schedule hasn't started, has ended, is paused that day, or has
     * run its course without repeating. Pauses push the cycle back by their length.
     */
    fun phaseOn(schedule: MedSchedule, a: MedActivation, date: String): PhaseState? {
        val day = Dates.parse(date) ?: return null
        val start = Dates.parse(a.start) ?: return null
        if (day < start || !a.runsOn(date) || pausedOn(a, date)) return null
        val phases = schedule.phases.filter { it.length > 0 }
        if (phases.isEmpty()) return null
        // The calendar date the cycle has reached, with paused days taken out.
        val reached = day.minusDays(pausedDaysBefore(a, start, day))
        var phaseStart = start
        var cycle = 1
        // Every phase lasts at least a day, so this ends; the cap only guards against absurd dates.
        while (cycle <= 100_000) {
            for ((i, p) in phases.withIndex()) {
                val end = p.unit.add(phaseStart, p.length.toLong())
                if (reached < end) {
                    val inPhase = ChronoUnit.DAYS.between(phaseStart, reached)
                    val length = ChronoUnit.DAYS.between(phaseStart, end)
                    val last = day.plusDays(length - 1 - inPhase)
                    return PhaseState(schedule.phases.indexOf(p).takeIf { it >= 0 } ?: i, p, inPhase + 1, length, last.format(Dates.ISO), cycle)
                }
                phaseStart = end
            }
            if (!schedule.repeats) return null
            cycle++
        }
        return null
    }

    /** The doses [a] has due on [date], earliest first, each with any dose change applied (#204). */
    fun dosesOn(schedule: MedSchedule, a: MedActivation, date: String, changes: List<MedDoseChange> = emptyList()): List<DueDose> {
        val state = phaseOn(schedule, a, date) ?: return emptyList()
        if (!state.phase.on) return emptyList()
        val day = Dates.parse(date) ?: return emptyList()
        return state.phase.doses
            .filter { it.days.matches(day, state.day - 1) }
            .map { d ->
                val change = changeFor(changes, a.id, d.id, date)
                DueDose(a, schedule, d, date, cleanTime(d.time), change?.amount ?: d.amount, change)
            }
            .sortedBy { it.time }
    }

    /**
     * The change that applies to [doseId] of [activationId] on [date]: a one-day change before a lasting one, and of
     * each kind the one made from the latest date (the newest when two start the same day).
     */
    fun changeFor(changes: List<MedDoseChange>, activationId: Long, doseId: Long, date: String): MedDoseChange? =
        changes.filter { it.activationId == activationId && it.doseId == doseId && it.covers(date) }
            // One-day changes first (false sorts first), then the latest start, then the newest.
            .sortedWith(compareBy<MedDoseChange> { it.toDate == null }.thenByDescending { it.fromDate }.thenByDescending { it.id })
            .firstOrNull()

    /** Every dose due on [date] across all running schedules, earliest first. */
    fun dueOn(plan: MedPlan, date: String): List<DueDose> =
        plan.activations.flatMap { a ->
            val s = plan.schedulesById[a.scheduleId] ?: return@flatMap emptyList()
            dosesOn(s, a, date, plan.changes)
        }.sortedWith(compareBy({ it.time }, { it.dose.medication.lowercase() }))

    /** The value a dose was taken as, if it was: matched by dose and due time. */
    fun takenRecord(records: List<MRecord>, due: DueDose): MRecord? =
        records.firstOrNull { it.doseId == due.dose.id && it.due == due.due }

    // ---------- Loading ----------

    fun load(r: MedDao): MedPlan {
        val doses = HashMap<Long, MutableList<MedDose>>()
        r.doses().forEach {
            doses.getOrPut(it.phase_id) { ArrayList() }.add(
                MedDose(it.id, it.medication, it.amount, it.unit, it.time, MedDays.decode(it.days), it.comment)
            )
        }
        val phases = HashMap<Long, MutableList<MedPhase>>()
        r.phases().forEach {
            phases.getOrPut(it.schedule_id) { ArrayList() }.add(
                MedPhase(it.id, it.active != 0, it.length, PhaseUnit.of(it.unit), doses[it.id].orEmpty())
            )
        }
        val schedules = r.schedules().map { MedSchedule(it.id, it.name, it.notes, it.repeats != 0, it.sort_order, phases[it.id].orEmpty()) }
        val activations = r.activations().map {
            MedActivation(it.id, it.schedule_id, it.start, MedActivation.decodePauses(it.pauses), it.ended_on)
        }
        val skipped = HashMap<Long, MutableSet<String>>()
        r.statuses().filter { it.status == SKIPPED }.forEach { skipped.getOrPut(it.dose_id) { HashSet() }.add(it.due) }
        val changes = r.changes().map { MedDoseChange(it.id, it.activation_id, it.dose_id, it.from_date, it.to_date, it.amount) }
        return MedPlan(schedules, activations, skipped, changes)
    }

    // ---------- Writes ----------

    private suspend fun <T> write(block: (MedDao) -> T): T = withContext(Dispatchers.IO) {
        val db = Store.db
        val result = db.transaction { block(db.medDao) }
        // Schedules and taken doses live in the body area, with the medications.
        Store.refresh(Area.BODY)
        result
    }

    /**
     * Creates the schedule (id 0) or updates it. Phases and doses keep their ids when kept, so taken doses, skips and
     * dose changes still point at them; removed ones are deleted with their skips and changes. Returns its id.
     */
    suspend fun save(schedule: MedSchedule): Long = write { r ->
        val name = schedule.name.trim().replace(Regex("\\s+"), " ").ifEmpty { "Schedule" }
        val notes = schedule.notes?.trim()?.ifBlank { null }
        val repeats = if (schedule.repeats) 1 else 0
        val id = if (schedule.id == 0L) {
            r.addSchedule(MedScheduleRow(0L, name, notes, repeats, r.nextScheduleOrder()))
        } else {
            r.updateSchedule(schedule.id, name, notes, repeats)
            schedule.id
        }
        val keptPhases = schedule.phases.map { it.id }.filter { it > 0 }.toSet()
        val keptDoses = schedule.phases.flatMap { p -> p.doses.map { it.id } }.filter { it > 0 }.toSet()
        r.doseIdsOfSchedule(id).filter { it !in keptDoses }.forEach { forgetDose(r, it) }
        r.phaseIds(id).filter { it !in keptPhases }.forEach { r.deletePhase(it) }
        schedule.phases.forEachIndexed { i, p ->
            val length = p.length.coerceIn(1, 999)
            val phaseId = if (p.id > 0) {
                r.updatePhase(p.id, i, if (p.on) 1 else 0, length, p.unit.code)
                p.id
            } else r.addPhase(MedPhaseRow(0L, id, i, if (p.on) 1 else 0, length, p.unit.code))
            // An off phase holds no doses.
            val doses = if (p.on) p.doses else emptyList()
            if (!p.on) p.doses.filter { it.id > 0 }.forEach { forgetDose(r, it.id) }
            doses.forEachIndexed { j, d ->
                val time = cleanTime(d.time)
                val comment = d.comment?.trim()?.ifBlank { null }
                if (d.id > 0) r.updateDose(d.id, phaseId, j, d.medication.trim(), d.amount, d.unit.trim(), time, d.days.encode(), comment)
                else r.addDose(MedDoseRow(0L, phaseId, j, d.medication.trim(), d.amount, d.unit.trim(), time, d.days.encode(), comment))
                // A dose names a medication: make sure it is one, so its values show on Medications.
                r.ensureMedication(d.medication.trim(), d.unit.trim())
            }
        }
        id
    }

    private fun forgetDose(r: MedDao, doseId: Long) {
        r.deleteStatusesOf(doseId)
        r.deleteChangesOf(doseId)
        r.deleteDose(doseId)
    }

    /** A copy of [schedule] with new ids, named [name], not running. Returns the new id. */
    suspend fun copy(schedule: MedSchedule, name: String): Long =
        save(schedule.copy(id = 0L, name = name, phases = schedule.phases.map { p -> p.copy(id = 0L, doses = p.doses.map { it.copy(id = 0L) }) }))

    /** Deletes a schedule, its runs, skips and changes. Doses already taken stay as medication values. */
    suspend fun delete(id: Long): Unit = write { r ->
        r.doseIdsOfSchedule(id).forEach { forgetDose(r, it) }
        r.phaseIds(id).forEach { r.deletePhase(it) }
        r.deleteActivationsOf(id)
        r.deleteSchedule(id)
    }

    /** Starts [scheduleId] from [start] (#202). Returns the new activation's id. */
    suspend fun activate(scheduleId: Long, start: String): Long = write { r ->
        r.addActivation(MedActivationRow(0L, scheduleId, start, "", null))
    }

    /** Pauses [a] from [date]: no doses are due and the cycle waits until it's resumed. */
    suspend fun pause(a: MedActivation, date: String = Dates.today()): Unit = write { r ->
        if (a.paused) return@write
        r.updateActivation(a.id, a.start, MedActivation.encodePauses(a.pauses + MedPause(date, null)), a.endedOn)
    }

    /** Resumes [a] from [date]; the cycle carries on where the pause stopped it. */
    suspend fun resume(a: MedActivation, date: String = Dates.today()): Unit = write { r ->
        val pauses = a.pauses.map { p ->
            if (p.to != null) p
            else MedPause(p.from, maxOf(date, p.from))
        }.filter { it.to != it.from }
        r.updateActivation(a.id, a.start, MedActivation.encodePauses(pauses), a.endedOn)
    }

    /** Ends [a]: [firstDayWithout] is the first day with no doses (tomorrow by default, so today's stay). */
    suspend fun end(a: MedActivation, firstDayWithout: String): Unit = write { r ->
        val pauses = a.pauses.map { if (it.to == null) MedPause(it.from, maxOf(firstDayWithout, it.from)) else it }
        r.updateActivation(a.id, a.start, MedActivation.encodePauses(pauses), firstDayWithout)
    }

    /** Puts [a] back as it was, for Undo after a pause, resume or end. */
    suspend fun restore(a: MedActivation): Unit = write { r ->
        r.updateActivation(a.id, a.start, MedActivation.encodePauses(a.pauses), a.endedOn)
    }

    /** Removes [a] and its dose changes, for Undo after starting a schedule by mistake. */
    suspend fun deleteActivation(a: MedActivation): Unit = write { r ->
        r.deleteChangesOfActivation(a.id)
        r.deleteActivation(a.id)
    }

    /**
     * Marks [due] taken (#202): one medication value, tied to its dose and due time, at [time] (the due time when
     * logged on another day). Taking it again does nothing; a skip is cleared. Returns the value's id.
     */
    suspend fun take(due: DueDose, time: String, comment: String? = null): Long = write { r ->
        r.takenRecord(due.dose.id, due.due)?.let { return@write it }
        r.clearStatus(due.dose.id, due.due)
        r.ensureMedication(due.dose.medication, due.dose.unit)
        r.addTaken(due.dose.medication, due.dose.unit, due.date, time, due.amount, comment, due.dose.id, due.due)
    }

    /** Un-ticks [due]: its value goes. */
    suspend fun untake(due: DueDose): Unit = write { r -> r.deleteTaken(due.dose.id, due.due) }

    /** Marks [due] skipped (#202, #203), removing its value if it was taken. */
    suspend fun skip(due: DueDose): Unit = write { r ->
        r.deleteTaken(due.dose.id, due.due)
        r.setStatus(due.dose.id, due.due, SKIPPED)
    }

    /** Clears a skip. */
    suspend fun unskip(due: DueDose): Unit = write { r -> r.clearStatus(due.dose.id, due.due) }

    /** Records a dose change (#204). Returns its id, for Undo. */
    suspend fun addChange(change: MedDoseChange): Long = write { r ->
        r.addChange(MedDoseChangeRow(0L, change.activationId, change.doseId, change.fromDate, change.toDate, change.amount))
    }

    suspend fun deleteChange(id: Long): Unit = write { r -> r.deleteChange(id) }

    /** The weekday numbers in the user's week order, starting on [weekStart] (1 = Monday). */
    fun weekOrder(weekStart: Int): List<Int> = (0 until 7).map { ((weekStart - 1 + it) % 7) + 1 }

    /** Short weekday name for an ISO number. */
    fun weekdayShort(day: Int): String =
        DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
}
