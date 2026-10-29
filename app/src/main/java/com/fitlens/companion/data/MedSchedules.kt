package com.fitlens.companion.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.Serializable

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

/**
 * How long a phase lasts. Stored as its [code]. [ONGOING] (#228) has no end: only the last phase of a schedule can be
 * ongoing, and once a run reaches it the schedule never finishes or repeats (its stored length is ignored).
 */
enum class PhaseUnit(val code: String) {
    DAYS("d"), WEEKS("w"), MONTHS("m"), YEARS("y"), ONGOING("o");

    /** [n] units after [date]; an ongoing phase never ends, so [LocalDate.MAX] (never stepped past). */
    fun add(date: LocalDate, n: Long): LocalDate = when (this) {
        DAYS -> date.plusDays(n)
        WEEKS -> date.plusWeeks(n)
        MONTHS -> date.plusMonths(n)
        YEARS -> date.plusYears(n)
        ONGOING -> LocalDate.MAX
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
@Serializable
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

/**
 * One dose of an on phase. An [id] of 0 is one not saved yet. [time] is `HH:mm`. A taper or step-up (#234) changes
 * [amount] by [step] (negative tapers down) every [stepEvery] days of the phase, stopping at [stepLimit]: a floor when
 * tapering (0 when none is set), a ceiling when stepping up (none when not set). The count starts again each phase.
 */
@Serializable
data class MedDose(
    val id: Long,
    val medication: String,
    val amount: Double,
    val unit: String,
    val time: String = "08:00",
    val days: MedDays = MedDays(),
    val comment: String? = null,
    val step: Double = 0.0,
    val stepEvery: Int = 0,
    val stepLimit: Double? = null
) {
    val steps: Boolean get() = step != 0.0 && stepEvery > 0

    /** The amount on day [dayInPhase] of the phase (0 on its first day), after any taper or step-up. */
    fun amountOn(dayInPhase: Long): Double {
        if (!steps || dayInPhase < 0) return amount
        val raw = amount + step * (dayInPhase / stepEvery)
        val bounded = if (step < 0) maxOf(raw, minOf(stepLimit ?: 0.0, amount))
            else stepLimit?.let { minOf(raw, maxOf(it, amount)) } ?: raw
        // Steps like 0.1 add up with float error; amounts are kept to six decimals.
        return Math.round(bounded * 1_000_000.0) / 1_000_000.0
    }
}

/** One phase: on (with [doses]) or off, [length] [unit]s long. An [id] of 0 is one not saved yet. */
@Serializable
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
) {
    /** Whether the last phase is ongoing (#228): the schedule then never ends, and "repeat" has no meaning. */
    val ongoing: Boolean get() = phases.lastOrNull()?.unit == PhaseUnit.ONGOING
}

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
    val changes: List<MedDoseChange> = emptyList(),
    /** Each medication's supply and injection details (#209, #210), by name; see [infoOf]. */
    val info: Map<String, MedInfo> = emptyMap(),
    /** The symptoms the user rates and their ratings (#207). */
    val symptoms: List<Symptom> = emptyList(),
    val ratings: List<SymptomRating> = emptyList()
) {
    /** [medication]'s details, or empty ones when none were set. */
    fun infoOf(medication: String): MedInfo = info[medication] ?: MedInfo.none(medication)

    val schedulesById: Map<Long, MedSchedule> = schedules.associateBy { it.id }
    val activationsBySchedule: Map<Long, List<MedActivation>> = activations.groupBy { it.scheduleId }
    /** Every dose with its schedule, by the dose's id. */
    val dosesById: Map<Long, Pair<MedSchedule, MedDose>> =
        schedules.flatMap { s -> s.phases.flatMap { p -> p.doses.map { it.id to (s to it) } } }.toMap()

    /** The dose changes of [medication] (#204), newest first. */
    fun changesOf(medication: String): List<MedDoseChange> =
        changes.filter { dosesById[it.doseId]?.second?.medication == medication }.sortedWith(compareByDescending<MedDoseChange> { it.fromDate }.thenByDescending { it.id })

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
    /** The amount after any taper (#234) and dose change (#204). */
    val amount: Double,
    /** The change that set [amount], if one did. */
    val change: MedDoseChange? = null,
    /** What the schedule (with any taper, #234) gives that day, before a dose change. */
    val planned: Double = amount
) {
    /** `yyyy-MM-dd HH:mm`: with the dose's id, what a taken value and a skip are keyed by. */
    val due: String get() = "$date $time"
}

/**
 * Where an activation is on a date: its phase, the day within it (1-based) and how long it lasts. An ongoing phase
 * (#228) has no length or last day: [days] and [lastDay] are null.
 */
data class PhaseState(
    val index: Int,
    val phase: MedPhase,
    val day: Long,
    val days: Long?,
    /** The phase's last day, counting no further pauses. */
    val lastDay: String?,
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
     * run its course without repeating. Pauses push the cycle back by their length. An ongoing phase (#228) holds the
     * run for good once it's reached.
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
                if (p.unit == PhaseUnit.ONGOING) {
                    val inPhase = ChronoUnit.DAYS.between(phaseStart, reached)
                    return PhaseState(schedule.phases.indexOf(p).takeIf { it >= 0 } ?: i, p, inPhase + 1, null, null, cycle)
                }
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
                val planned = d.amountOn(state.day - 1)
                DueDose(a, schedule, d, date, cleanTime(d.time), change?.amount ?: planned, change, planned)
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

    // ---------- The dose log and adherence (#205) ----------

    /** What became of a scheduled dose: taken, skipped, missed (its day is over), or still due today. */
    enum class Outcome { TAKEN, SKIPPED, MISSED, DUE }

    /** One line of the dose log: a scheduled dose and what became of it, or a value logged by hand ([due] null). */
    data class LogEntry(
        val date: String,
        val medication: String,
        val due: DueDose?,
        val record: MRecord?,
        val outcome: Outcome?
    )

    /** [missedAfter] meaning a dose counts as missed only once its day is over (#318). */
    const val MISSED_END_OF_DAY = -1

    /** The window choices in minutes ([MISSED_END_OF_DAY] last), as offered in Settings > Medications (#318). */
    val MISSED_CHOICES: List<Int> = listOf(30, 60, 120, 240, MISSED_END_OF_DAY)

    /** How long after the due time an untaken dose counts as missed unless the user chooses (#318). */
    const val MISSED_DEFAULT_MINUTES = 60

    /**
     * Whether a dose due on [dueDate] at [dueTime] (`HH:mm`) that was neither taken nor skipped counts as missed at [now]:
     * [afterMinutes] after its due time have passed (crossing midnight correctly), or, for [MISSED_END_OF_DAY], its
     * date is over (#318). Taken and skipped doses are never missed, so callers check those first.
     */
    fun isMissed(dueDate: String, dueTime: String, now: LocalDateTime, afterMinutes: Int): Boolean {
        val day = Dates.parse(dueDate) ?: return false
        if (afterMinutes < 0) return day.isBefore(now.toLocalDate())
        val time = try { LocalTime.parse(dueTime) } catch (e: Exception) { return false }
        return day.atTime(time).plusMinutes(afterMinutes.toLong()).isBefore(now)
    }

    /**
     * Every scheduled dose from [from] to [to] (inclusive, not after [today]) with its outcome, plus every value of
     * [names] logged by hand, oldest first. [names] empty means every medication.
     */
    fun doseLog(
        plan: MedPlan, records: List<MRecord>, from: String, to: String, today: String, names: Set<String> = emptySet(),
        missedAfter: Int = MISSED_END_OF_DAY, now: LocalDateTime? = null
    ): List<LogEntry> {
        fun wanted(n: String) = names.isEmpty() || n in names
        val out = ArrayList<LogEntry>()
        val linked = HashSet<Long>()
        val last = minOf(to, today)
        var day = Dates.parse(from) ?: return emptyList()
        val end = Dates.parse(last) ?: return emptyList()
        while (!day.isAfter(end)) {
            val date = day.format(Dates.ISO)
            dueOn(plan, date).filter { wanted(it.dose.medication) }.forEach { d ->
                val r = takenRecord(records, d)
                if (r != null) linked += r.id
                val outcome = when {
                    r != null -> Outcome.TAKEN
                    plan.isSkipped(d.dose.id, d.due) -> Outcome.SKIPPED
                    date < today -> Outcome.MISSED
                    // Today's dose past the user's window (#318); without a clock ([now] null) only a finished day is missed.
                    now != null && isMissed(date, d.time, now, missedAfter) -> Outcome.MISSED
                    else -> Outcome.DUE
                }
                out += LogEntry(date, d.dose.medication, d, r, outcome)
            }
            day = day.plusDays(1)
        }
        records.filter { it.id !in linked && wanted(it.name) && it.date.take(10) in from..to }
            .forEach { out += LogEntry(it.date.take(10), it.name, null, it, null) }
        return out.sortedWith(compareBy({ it.date }, { it.due?.time ?: it.record?.time?.take(5).orEmpty() }, { it.medication.lowercase() }))
    }

    /** A medication's adherence over a range: doses scheduled, taken, skipped and missed, and the share taken. */
    data class Adherence(val medication: String, val scheduled: Int, val taken: Int, val skipped: Int, val missed: Int) {
        /** Taken out of the doses whose time has come (today's still due aren't counted), 0–100, or null with none. */
        val percent: Int? get() = (taken + skipped + missed).takeIf { it > 0 }?.let { Math.round(taken * 100.0 / it).toInt() }
    }

    /** Adherence per medication from a [doseLog], in name order. Values logged by hand don't count. */
    fun adherence(log: List<LogEntry>): List<Adherence> =
        log.filter { it.due != null }.groupBy { it.medication }.map { (name, entries) ->
            Adherence(
                name, entries.size,
                entries.count { it.outcome == Outcome.TAKEN },
                entries.count { it.outcome == Outcome.SKIPPED },
                entries.count { it.outcome == Outcome.MISSED }
            )
        }.sortedBy { it.medication.lowercase() }

    // ---------- Loading ----------

    fun load(r: MedDao): MedPlan {
        val doses = HashMap<Long, MutableList<MedDose>>()
        r.doses().forEach {
            doses.getOrPut(it.phase_id) { ArrayList() }.add(
                MedDose(it.id, it.medication, it.amount, it.unit, it.time, MedDays.decode(it.days), it.comment, it.step, it.step_days, it.step_limit)
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
        val info = r.infos().associate {
            it.medication to MedInfo(it.medication, it.supply, it.supply_from, it.warn_days, it.expires, it.injected != 0, it.as_needed != 0, it.max_per_day)
        }
        val symptoms = r.symptoms().map { Symptom(it.id, it.name, it.sort_order) }
        val ratings = r.ratings().map { SymptomRating(it.id, it.symptom_id, it.date, it.rating, it.comment) }
        return MedPlan(schedules, activations, skipped, changes, info, symptoms, ratings)
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
        // An ongoing schedule never reaches the end of its cycle, so it can't repeat (#228).
        val repeats = if (schedule.repeats && !schedule.ongoing) 1 else 0
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
                // A step needs both an amount and an interval (#234); anything else is a plain dose.
                val step = if (d.steps) d.step else 0.0
                val stepDays = if (d.steps) d.stepEvery.coerceIn(1, 365) else 0
                val limit = if (d.steps) d.stepLimit else null
                if (d.id > 0) r.updateDose(d.id, phaseId, j, d.medication.trim(), d.amount, d.unit.trim(), time, d.days.encode(), comment, step, stepDays, limit)
                else r.addDose(MedDoseRow(0L, phaseId, j, d.medication.trim(), d.amount, d.unit.trim(), time, d.days.encode(), comment, step, stepDays, limit))
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

    /**
     * [a] counted from [start] instead (#229), or null when that's not allowed: on or after the day the run ended.
     * Pauses that ended by the new start go; one that spans it now begins on it. Doses already taken stay as values
     * (those that no longer fall on a dose day show as logged by hand); dose changes stay as they are.
     */
    fun withStart(a: MedActivation, start: String): MedActivation? {
        if (Dates.parse(start) == null || (a.endedOn != null && start >= a.endedOn)) return null
        val pauses = a.pauses
            .filter { it.to == null || it.to > start }
            .map { if (it.from < start) MedPause(start, it.to) else it }
        return a.copy(start = start, pauses = pauses)
    }

    /**
     * Starting [a]'s schedule again from day 1 on [date] (#229): the old run, ended the day before [date] so its history
     * and adherence stay, and the new run from [date] (id 0, not saved). On or before [a]'s own start there's nothing
     * to keep, so it's [a] moved to [date] with no pauses, and no new run.
     */
    fun restarted(a: MedActivation, date: String): Pair<MedActivation, MedActivation?> {
        if (date <= a.start) return a.copy(start = date, pauses = emptyList(), endedOn = null) to null
        val end = minOf(date, a.endedOn ?: date)
        val pauses = a.pauses.map { if (it.to == null) MedPause(it.from, maxOf(end, it.from)) else it }.filter { it.from < end }
        return a.copy(pauses = pauses, endedOn = end) to MedActivation(0L, a.scheduleId, date)
    }

    /** Moves [a]'s start to [start] (#229, see [withStart]). False when that's not allowed. Undo is [restore]. */
    suspend fun setStart(a: MedActivation, start: String): Boolean {
        val moved = withStart(a, start) ?: return false
        restore(moved)
        return true
    }

    /**
     * Starts [a]'s schedule again from day 1 on [date] (#229, see [restarted]), in one transaction. Returns the new
     * run's id, or [a]'s own when it was moved instead. Undo: [undoRestart].
     */
    suspend fun restart(a: MedActivation, date: String): Long = write { r ->
        val (old, new) = restarted(a, date)
        r.updateActivation(old.id, old.start, MedActivation.encodePauses(old.pauses), old.endedOn)
        if (new == null) old.id else r.addActivation(MedActivationRow(0L, new.scheduleId, new.start, "", null))
    }

    /** Undoes [restart]: removes the new run [newId] (unless it was [before] itself) and puts [before] back. */
    suspend fun undoRestart(before: MedActivation, newId: Long): Unit = write { r ->
        if (newId != before.id) {
            r.deleteChangesOfActivation(newId)
            r.deleteActivation(newId)
        }
        r.updateActivation(before.id, before.start, MedActivation.encodePauses(before.pauses), before.endedOn)
    }

    // ---------- Past runs (#231) ----------

    /** One run for the schedule's RUNS list: its last day (null while it runs), the days it ran, and % taken. */
    data class RunSummary(val run: MedActivation, val lastDay: String?, val days: Long, val adherence: Int?)

    /** [a]'s last day with doses, or null while it runs. */
    fun lastDayOf(a: MedActivation): String? = a.endedOn?.let { Dates.parse(it)?.minusDays(1)?.format(Dates.ISO) }

    /** [a]'s dates, length (to [today] at most) and the share of its doses taken, counting only doses whose day came. */
    fun runSummary(
        plan: MedPlan, records: List<MRecord>, a: MedActivation, today: String,
        missedAfter: Int = MISSED_END_OF_DAY, now: LocalDateTime? = null
    ): RunSummary {
        val last = lastDayOf(a)
        val to = minOf(last ?: today, today)
        val first = Dates.parse(a.start)
        val end = Dates.parse(to)
        val days = if (first == null || end == null) 0L else (ChronoUnit.DAYS.between(first, end) + 1).coerceAtLeast(0L)
        val s = plan.schedulesById[a.scheduleId]
        val adherence = if (s == null || days <= 0L) null else {
            val only = MedPlan(listOf(s), listOf(a), plan.skipped, plan.changes)
            val counted = doseLog(only, records, a.start, to, today, missedAfter = missedAfter, now = now).filter { it.due != null && it.outcome != Outcome.DUE }
            if (counted.isEmpty()) null else Math.round(counted.count { it.outcome == Outcome.TAKEN } * 100.0 / counted.size).toInt()
        }
        return RunSummary(a, last, days, adherence)
    }

    /** Whether [moved] would overlap another run of its schedule in [runs]: two at once would double every dose. */
    fun overlaps(runs: List<MedActivation>, moved: MedActivation): Boolean {
        val never = "9999-12-31"
        return runs.any { b ->
            b.id != moved.id && b.scheduleId == moved.scheduleId &&
                moved.start < (b.endedOn ?: never) && b.start < (moved.endedOn ?: never)
        }
    }

    /** [a] ending after [lastDay] (its last day with doses), or null when that's before it starts. */
    fun withLastDay(a: MedActivation, lastDay: String): MedActivation? {
        val day = Dates.parse(lastDay) ?: return null
        if (lastDay < a.start) return null
        val end = day.plusDays(1).format(Dates.ISO)
        val pauses = a.pauses.filter { it.from < end }.map { if (it.to == null || it.to > end) MedPause(it.from, end) else it }
        return a.copy(pauses = pauses, endedOn = end)
    }

    /** Puts back a run removed with [deleteActivation], with the same id and its [changes], for Undo (#231). */
    suspend fun restoreRun(a: MedActivation, changes: List<MedDoseChange>): Unit = write { r ->
        r.addActivation(MedActivationRow(a.id, a.scheduleId, a.start, MedActivation.encodePauses(a.pauses), a.endedOn))
        changes.forEach { r.addChange(MedDoseChangeRow(0L, a.id, it.doseId, it.fromDate, it.toDate, it.amount)) }
    }

    /** Puts [a] back as it was, for Undo after a pause, resume, end or a changed start. */
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
     * logged on another day), with the injection [site] when there is one (#210). Taking it again does nothing; a skip
     * is cleared. Returns the value's id.
     */
    suspend fun take(due: DueDose, time: String, comment: String? = null, site: InjectionSite? = null): Long = write { r ->
        r.takenRecord(due.dose.id, due.due)?.let { return@write it }
        r.clearStatus(due.dose.id, due.due)
        r.ensureMedication(due.dose.medication, due.dose.unit)
        r.addTaken(due.dose.medication, due.dose.unit, due.date, time, due.amount, comment, due.dose.id, due.due, site?.key)
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

    // ---------- Simple setup (#232) ----------

    /** What a new schedule starts with from the simple setup: its phases, whether it repeats, and its first dose's days. */
    data class Starter(val phases: List<MedPhase>, val repeats: Boolean, val days: MedDays)

    /** Every day, set weekdays or every N days ([days]), with no end: one ongoing on phase. */
    fun ongoingStarter(days: MedDays): Starter = Starter(listOf(MedPhase(0L, true, 1, PhaseUnit.ONGOING)), false, days)

    /** [onLength] [onUnit]s of doses every day, then [offLength] [offUnit]s off, repeating. Lengths are kept to 1–999. */
    fun cycleStarter(onLength: Int, onUnit: PhaseUnit, offLength: Int, offUnit: PhaseUnit): Starter {
        fun fixed(u: PhaseUnit) = if (u == PhaseUnit.ONGOING) PhaseUnit.WEEKS else u
        return Starter(
            listOf(
                MedPhase(0L, true, onLength.coerceIn(1, 999), fixed(onUnit)),
                MedPhase(0L, false, offLength.coerceIn(1, 999), fixed(offUnit))
            ),
            true, MedDays()
        )
    }

    /** The weekday numbers in the user's week order, starting on [weekStart] (1 = Monday). */
    fun weekOrder(weekStart: Int): List<Int> = (0 until 7).map { ((weekStart - 1 + it) % 7) + 1 }

    /** Short weekday name for an ISO number. */
    fun weekdayShort(day: Int): String =
        DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
}
