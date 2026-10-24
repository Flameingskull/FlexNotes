package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The menstrual cycle's phases (#222): periods from period days, and the phases between them. */
class CycleTest {
    private fun d(month: Int, day: Int): LocalDate = LocalDate.of(2026, month, day)

    private fun days(vararg range: Pair<LocalDate, LocalDate>): List<LocalDate> = range.flatMap { (a, b) ->
        generateSequence(a) { it.plusDays(1) }.takeWhile { !it.isAfter(b) }.toList()
    }

    @Test
    fun nearbyDaysJoinOnePeriod() {
        val runs = Cycle.periods(listOf(d(1, 1), d(1, 2), d(1, 3), d(1, 5), d(1, 29), d(1, 30)))
        assertEquals(listOf(d(1, 1) to d(1, 5), d(1, 29) to d(1, 30)), runs)
    }

    @Test
    fun aWholeCycleHasEveryPhase() {
        val phases = Cycle.phases(days(d(1, 1) to d(1, 5), d(1, 29) to d(1, 30)), today = d(2, 1))
        val first = phases.takeWhile { it.from.isBefore(d(1, 29)) }
        assertEquals(
            listOf(
                Cycle.Span(Cycle.Phase.PERIOD, d(1, 1), d(1, 5)),
                Cycle.Span(Cycle.Phase.FOLLICULAR, d(1, 6), d(1, 13)),
                // Ovulation 14 days before the next period, with the day either side.
                Cycle.Span(Cycle.Phase.OVULATION, d(1, 14), d(1, 16)),
                Cycle.Span(Cycle.Phase.LUTEAL, d(1, 17), d(1, 28))
            ),
            first
        )
    }

    @Test
    fun theRunningCycleIsShownOnlyUpToToday() {
        val phases = Cycle.phases(days(d(1, 1) to d(1, 5), d(1, 29) to d(1, 30)), today = d(2, 1))
        val running = phases.filter { !it.from.isBefore(d(1, 29)) }
        assertEquals(
            listOf(
                Cycle.Span(Cycle.Phase.PERIOD, d(1, 29), d(1, 30)),
                Cycle.Span(Cycle.Phase.FOLLICULAR, d(1, 31), d(2, 1))
            ),
            running
        )
    }

    @Test
    fun aCycleTooLongToBeWholeShowsOnlyItsPeriod() {
        // 60 days apart: a period in between probably wasn't logged.
        val phases = Cycle.phases(days(d(1, 1) to d(1, 4), d(3, 2) to d(3, 4)), today = d(3, 5))
        assertTrue(phases.filter { it.from.isBefore(d(3, 2)) }.all { it.phase == Cycle.Phase.PERIOD })
    }

    @Test
    fun anOldRunningCycleIsNotEstimated() {
        val phases = Cycle.phases(days(d(1, 1) to d(1, 4)), today = d(3, 1))
        assertEquals(listOf(Cycle.Span(Cycle.Phase.PERIOD, d(1, 1), d(1, 4))), phases)
    }

    @Test
    fun typicalLengthIsTheMedianOfWholeCycles() {
        val runs = Cycle.periods(listOf(d(1, 1), d(1, 27), d(2, 26), d(3, 26), d(6, 1)))
        // 26, 30 and 28 days; the 67-day gap is left out.
        assertEquals(28, Cycle.typicalLength(runs))
        assertEquals(Cycle.DEFAULT_LENGTH, Cycle.typicalLength(emptyList()))
    }

    @Test
    fun periodDaysComeFromAnySourceAboveZero() {
        fun r(date: String, value: Double, source: String) = MRecord(0, "period", "flow", date, "00:00:00", value, null, source)
        val found = Cycle.periodDays(
            listOf(r("2026-01-01", 2.0, Sources.HEALTH_CONNECT), r("2026-01-02", 1.0, Sources.FLEXNOTES), r("2026-01-03", 0.0, Sources.FLEXNOTES))
        )
        assertEquals(setOf(d(1, 1), d(1, 2)), found)
    }
}
