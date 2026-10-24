package com.fitlens.companion.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * The menstrual cycle (#222): period days are body values named [NAME] (read from Health Connect, or typed in the Body
 * tracker), and the phases between them are worked out here, to be shaded on body and exercise graphs.
 *
 * Period days no more than [MERGE_GAP] days apart are one period. Each cycle runs from one period's first day to the
 * day before the next. Ovulation is estimated [LUTEAL_DAYS] days before the next period (the luteal phase is the
 * steadiest part of a cycle), shown as the day either side of it too; follicular is from the period's end up to it,
 * luteal from it to the next period. The cycle still running is estimated from the typical length of the recent ones
 * ([DEFAULT_LENGTH] when there are none) and shown up to today. A cycle shorter than [MIN_LENGTH] or longer than
 * [MAX_LENGTH] days (usually a period not logged) shows only its period. It's a guide, not a prediction. Plain Kotlin,
 * tested in `CycleTest`.
 */
object Cycle {
    const val NAME = "Period"
    const val UNIT = "flow"

    /** A period longer than this is cut short when read (a record left open by mistake). */
    const val MAX_PERIOD_DAYS = 15
    const val MERGE_GAP = 2
    const val LUTEAL_DAYS = 14
    const val DEFAULT_LENGTH = 28
    const val MIN_LENGTH = 21
    const val MAX_LENGTH = 45

    /** How many recent cycles the running one's length is taken from. */
    private const val RECENT = 6

    enum class Phase { PERIOD, FOLLICULAR, OVULATION, LUTEAL }

    /** One phase from [from] to [to], both included. */
    data class Span(val phase: Phase, val from: LocalDate, val to: LocalDate)

    fun isPeriod(name: String): Boolean = name.trim().equals(NAME, ignoreCase = true)

    /** The days with a Period value above zero, from any source. */
    fun periodDays(records: List<MRecord>): Set<LocalDate> =
        records.filter { isPeriod(it.name) && it.value > 0 }.mapNotNull { Dates.parse(it.date) }.toSet()

    /** [days] as periods, first day to last, in order; days up to [MERGE_GAP] apart join one period. */
    fun periods(days: Collection<LocalDate>): List<Pair<LocalDate, LocalDate>> {
        val sorted = days.distinct().sorted()
        val out = mutableListOf<Pair<LocalDate, LocalDate>>()
        sorted.forEach { d ->
            val last = out.lastOrNull()
            if (last != null && ChronoUnit.DAYS.between(last.second, d) <= MERGE_GAP + 1) out[out.lastIndex] = last.first to d
            else out += d to d
        }
        return out
    }

    /** The typical cycle length: the median of the last [RECENT] complete cycles that look whole, or [DEFAULT_LENGTH]. */
    fun typicalLength(periods: List<Pair<LocalDate, LocalDate>>): Int {
        val lengths = periods.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.first, b.first).toInt() }
            .filter { it in MIN_LENGTH..MAX_LENGTH }
            .takeLast(RECENT)
            .sorted()
        return if (lengths.isEmpty()) DEFAULT_LENGTH else lengths[lengths.size / 2]
    }

    /** Every phase of the cycles in [days], up to [today]. */
    fun phases(days: Collection<LocalDate>, today: LocalDate): List<Span> {
        val runs = periods(days.filter { !it.isAfter(today) })
        if (runs.isEmpty()) return emptyList()
        val typical = typicalLength(runs)
        val out = mutableListOf<Span>()
        runs.forEachIndexed { i, (start, end) ->
            out += Span(Phase.PERIOD, start, end)
            val next = runs.getOrNull(i + 1)?.first
            val nextStart = next ?: start.plusDays(typical.toLong())
            val length = ChronoUnit.DAYS.between(start, nextStart)
            if (length !in MIN_LENGTH..MAX_LENGTH) return@forEachIndexed
            // The running cycle stops being estimated once it's well past its expected length.
            if (next == null && today.isAfter(start.plusDays(MAX_LENGTH.toLong()))) return@forEachIndexed
            val ovulation = nextStart.minusDays(LUTEAL_DAYS.toLong())
            val ovFrom = maxOf(ovulation.minusDays(1), end.plusDays(1))
            val ovTo = ovulation.plusDays(1)
            val last = nextStart.minusDays(1)
            fun add(phase: Phase, from: LocalDate, to: LocalDate) {
                val until = if (next == null) minOf(to, today) else to
                if (!until.isBefore(from)) out += Span(phase, from, until)
            }
            add(Phase.FOLLICULAR, end.plusDays(1), ovFrom.minusDays(1))
            add(Phase.OVULATION, ovFrom, ovTo)
            add(Phase.LUTEAL, ovTo.plusDays(1), last)
        }
        return out
    }
}
