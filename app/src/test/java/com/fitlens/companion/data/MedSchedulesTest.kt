package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Medication schedules (#201, #202, #204): phases, calendar months and years, repeats, pauses, dose days and changes. */
class MedSchedulesTest {

    private fun dose(id: Long, time: String = "08:00", days: MedDays = MedDays(), amount: Double = 5.0) =
        MedDose(id, "Creatine", amount, "g", time, days)

    private fun schedule(vararg phases: MedPhase, repeats: Boolean = false) = MedSchedule(1, "Cycle", repeats = repeats, phases = phases.toList())

    private fun run(start: String, vararg pauses: MedPause, ended: String? = null) = MedActivation(1, 1, start, pauses.toList(), ended)

    private fun on(length: Int, unit: PhaseUnit, vararg doses: MedDose) = MedPhase(10L + length, true, length, unit, doses.toList())
    private fun off(length: Int, unit: PhaseUnit) = MedPhase(20L + length, false, length, unit)

    @Test
    fun twoWeeksOnOneWeekOffRepeats() {
        val s = schedule(on(2, PhaseUnit.WEEKS, dose(1)), off(1, PhaseUnit.WEEKS), repeats = true)
        val a = run("2026-10-01")
        assertNull(MedSchedules.phaseOn(s, a, "2026-09-30"))
        assertEquals(1, MedSchedules.dosesOn(s, a, "2026-10-01").size)
        assertEquals(1, MedSchedules.dosesOn(s, a, "2026-10-14").size)
        // Day 15 is the first day off.
        val off = MedSchedules.phaseOn(s, a, "2026-10-15")!!
        assertFalse(off.phase.on)
        assertEquals(1L, off.day)
        assertEquals(7L, off.days)
        assertEquals("2026-10-21", off.lastDay)
        assertTrue(MedSchedules.dosesOn(s, a, "2026-10-15").isEmpty())
        // Day 22 starts the second cycle.
        val again = MedSchedules.phaseOn(s, a, "2026-10-22")!!
        assertTrue(again.phase.on)
        assertEquals(2, again.cycle)
        assertEquals(1L, again.day)
        assertEquals(14L, again.days)
    }

    @Test
    fun aScheduleThatDoesntRepeatEnds() {
        val s = schedule(on(3, PhaseUnit.DAYS, dose(1)))
        val a = run("2026-10-01")
        assertEquals(1, MedSchedules.dosesOn(s, a, "2026-10-03").size)
        assertNull(MedSchedules.phaseOn(s, a, "2026-10-04"))
    }

    @Test
    fun monthsAreCalendarMonthsThroughMonthEndsAndLeapYears() {
        val s = schedule(on(1, PhaseUnit.MONTHS, dose(1)), off(1, PhaseUnit.MONTHS), repeats = true)
        // From 31 January 2028 (a leap year), one month on ends with 28 February, the last day of the month.
        val a = run("2028-01-31")
        val first = MedSchedules.phaseOn(s, a, "2028-02-28")!!
        assertTrue(first.phase.on)
        assertEquals(29L, first.days)
        assertEquals("2028-02-28", first.lastDay)
        val second = MedSchedules.phaseOn(s, a, "2028-02-29")!!
        assertFalse(second.phase.on)
        assertEquals(1L, second.day)
    }

    @Test
    fun yearsCountLeapDays() {
        val s = schedule(on(1, PhaseUnit.YEARS, dose(1)))
        val a = run("2027-03-01")
        assertEquals(366L, MedSchedules.phaseOn(s, a, "2027-03-01")!!.days)
        assertNotNullOn(s, a, "2028-02-29")
        assertNull(MedSchedules.phaseOn(s, a, "2028-03-01"))
    }

    private fun assertNotNullOn(s: MedSchedule, a: MedActivation, date: String) =
        assertTrue(date, MedSchedules.phaseOn(s, a, date) != null)

    @Test
    fun weekdaysAndEveryNDays() {
        val mwf = MedDays(weekdays = setOf(1, 3, 5))
        val alternate = MedDays(every = 2)
        val s = schedule(on(4, PhaseUnit.WEEKS, dose(1, days = mwf), dose(2, "20:00", alternate)))
        val a = run("2026-10-19") // a Monday
        assertEquals(listOf(1L, 2L), MedSchedules.dosesOn(s, a, "2026-10-19").map { it.dose.id })
        assertEquals(emptyList<Long>(), MedSchedules.dosesOn(s, a, "2026-10-20").map { it.dose.id })
        assertEquals(listOf(1L, 2L), MedSchedules.dosesOn(s, a, "2026-10-21").map { it.dose.id })
        assertEquals(listOf(2L), MedSchedules.dosesOn(s, a, "2026-10-25").map { it.dose.id })
        assertEquals("w:1,3,5", mwf.encode())
        assertEquals(mwf, MedDays.decode("w:5,1,3"))
        assertEquals(alternate, MedDays.decode("n:2"))
        assertTrue(MedDays.decode("rubbish").everyDay)
    }

    @Test
    fun aPauseHoldsTheCycleWhereItStopped() {
        val s = schedule(on(10, PhaseUnit.DAYS, dose(1)), off(5, PhaseUnit.DAYS))
        // Paused for 3 days from the 5th: the 5th to 7th have nothing, and the cycle ends 3 days later.
        val a = run("2026-10-01", MedPause("2026-10-05", "2026-10-08"))
        assertTrue(MedSchedules.dosesOn(s, a, "2026-10-06").isEmpty())
        val back = MedSchedules.phaseOn(s, a, "2026-10-08")!!
        assertEquals(5L, back.day)
        assertTrue(MedSchedules.phaseOn(s, a, "2026-10-13")!!.phase.on)
        assertFalse(MedSchedules.phaseOn(s, a, "2026-10-14")!!.phase.on)
        // Still paused: nothing at all.
        val open = run("2026-10-01", MedPause("2026-10-05", null))
        assertTrue(open.paused)
        assertNull(MedSchedules.phaseOn(s, open, "2026-10-20"))
        assertEquals(listOf(MedPause("2026-10-05", "2026-10-08"), MedPause("2026-10-20", null)),
            MedActivation.decodePauses(MedActivation.encodePauses(listOf(MedPause("2026-10-05", "2026-10-08"), MedPause("2026-10-20", null)))))
    }

    @Test
    fun anEndedRunHasNoDosesFromItsEnd() {
        val s = schedule(on(1, PhaseUnit.YEARS, dose(1)))
        val a = run("2026-10-01", ended = "2026-10-10")
        assertEquals(1, MedSchedules.dosesOn(s, a, "2026-10-09").size)
        assertTrue(MedSchedules.dosesOn(s, a, "2026-10-10").isEmpty())
    }

    @Test
    fun doseChangesApplyForADayOrTheRestOfTheRun() {
        val s = schedule(on(1, PhaseUnit.YEARS, dose(1, amount = 5.0)))
        val a = run("2026-10-01")
        val changes = listOf(
            MedDoseChange(1, 1, 1, "2026-10-10", null, 7.5),
            MedDoseChange(2, 1, 1, "2026-10-12", "2026-10-12", 2.0),
            MedDoseChange(3, 99, 1, "2026-10-01", null, 100.0)
        )
        fun amountOn(date: String) = MedSchedules.dosesOn(s, a, date, changes).single().amount
        assertEquals(5.0, amountOn("2026-10-09"), 0.0)
        assertEquals(7.5, amountOn("2026-10-10"), 0.0)
        assertEquals(2.0, amountOn("2026-10-12"), 0.0)
        assertEquals(7.5, amountOn("2026-10-13"), 0.0)
        // The schedule itself is untouched.
        assertEquals(5.0, s.phases.single().doses.single().amount, 0.0)
    }

    @Test
    fun dueKeysAndTimes() {
        assertEquals("07:05", MedSchedules.cleanTime("7:05"))
        assertEquals("08:00", MedSchedules.cleanTime("later"))
        val s = schedule(on(1, PhaseUnit.WEEKS, dose(1, "21:30"), dose(2, "07:00")))
        val due = MedSchedules.dosesOn(s, run("2026-10-01"), "2026-10-02")
        assertEquals(listOf("2026-10-02 07:00", "2026-10-02 21:30"), due.map { it.due })
        val taken = MRecord(5, "Creatine", "g", "2026-10-02", "07:10:00", 5.0, null, "manual", doseId = 2, due = "2026-10-02 07:00")
        assertEquals(5L, MedSchedules.takenRecord(listOf(taken), due[0])?.id)
        assertNull(MedSchedules.takenRecord(listOf(taken), due[1]))
    }

    @Test
    fun theWeekStartsWhereTheUserSays() {
        assertEquals(listOf(7, 1, 2, 3, 4, 5, 6), MedSchedules.weekOrder(7))
        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7), MedSchedules.weekOrder(1))
    }

    // ---------- Ongoing phases (#228) ----------

    @Test
    fun anOngoingPhaseNeverEnds() {
        val s = schedule(on(1, PhaseUnit.ONGOING, dose(1)))
        assertTrue(s.ongoing)
        val a = run("2026-01-01")
        assertNull(MedSchedules.phaseOn(s, a, "2025-12-31"))
        val st = MedSchedules.phaseOn(s, a, "2026-01-11")!!
        assertEquals(11L, st.day)
        assertNull(st.days)
        assertNull(st.lastDay)
        // Years later it still doses every day.
        assertEquals(1, MedSchedules.dosesOn(s, a, "2031-06-01").size)
        assertEquals(1, MedSchedules.phaseOn(s, a, "2031-06-01")!!.cycle)
    }

    @Test
    fun cyclesThenOngoingNeverRepeat() {
        // Even with repeat switched on, reaching the ongoing phase holds the run there for good.
        val s = schedule(on(2, PhaseUnit.WEEKS, dose(1)), off(1, PhaseUnit.WEEKS), on(1, PhaseUnit.ONGOING, dose(2, "20:00")), repeats = true)
        val a = run("2026-10-01")
        assertFalse(MedSchedules.phaseOn(s, a, "2026-10-15")!!.phase.on)
        assertTrue(MedSchedules.dosesOn(s, a, "2026-10-18").isEmpty())
        val first = MedSchedules.phaseOn(s, a, "2026-10-22")!!
        assertEquals(2, first.index)
        assertEquals(1L, first.day)
        val later = MedSchedules.phaseOn(s, a, "2027-10-22")!!
        assertEquals(2, later.index)
        assertEquals(1, later.cycle)
        assertEquals(listOf(2L), MedSchedules.dosesOn(s, a, "2027-10-22").map { it.dose.id })
    }

    @Test
    fun aPauseInsideAnOngoingPhaseHoldsItsDayCount() {
        val s = schedule(on(1, PhaseUnit.ONGOING, dose(1, days = MedDays(every = 2))))
        val a = run("2026-10-01", MedPause("2026-10-03", "2026-10-06"))
        assertTrue(MedSchedules.dosesOn(s, a, "2026-10-02").isEmpty())
        assertTrue(MedSchedules.dosesOn(s, a, "2026-10-04").isEmpty())
        // Three paused days: the 6th is the phase's third day, a dose day.
        assertEquals(3L, MedSchedules.phaseOn(s, a, "2026-10-06")!!.day)
        assertEquals(1, MedSchedules.dosesOn(s, a, "2026-10-06").size)
    }

    @Test
    fun onlyAnOngoingLastPhaseMakesTheScheduleOngoing() {
        assertFalse(schedule(on(1, PhaseUnit.ONGOING, dose(1)), off(1, PhaseUnit.WEEKS)).ongoing)
        assertFalse(schedule().ongoing)
        assertEquals(PhaseUnit.ONGOING, PhaseUnit.of("o"))
        assertEquals(PhaseUnit.WEEKS, PhaseUnit.of("?"))
    }

    // ---------- Fixing a running schedule (#229) ----------

    @Test
    fun aNewStartDropsEarlierPausesAndTrimsOneThatSpansIt() {
        val a = run("2026-10-01", MedPause("2026-10-03", "2026-10-05"), MedPause("2026-10-10", "2026-10-12"), MedPause("2026-10-20", null))
        val moved = MedSchedules.withStart(a, "2026-10-11")!!
        assertEquals("2026-10-11", moved.start)
        assertEquals(listOf(MedPause("2026-10-11", "2026-10-12"), MedPause("2026-10-20", null)), moved.pauses)
        // Moving it earlier keeps every pause.
        assertEquals(a.pauses, MedSchedules.withStart(a, "2026-09-01")!!.pauses)
        // An ended run can't start on or after the day it ended.
        val ended = run("2026-10-01", ended = "2026-10-15")
        assertNull(MedSchedules.withStart(ended, "2026-10-15"))
        assertEquals("2026-10-14", MedSchedules.withStart(ended, "2026-10-14")!!.start)
        assertNull(MedSchedules.withStart(a, "not a date"))
    }

    @Test
    fun aDoseTakenBeforeTheNewStartStaysAsALoggedValue() {
        val s = schedule(on(1, PhaseUnit.YEARS, dose(1, days = MedDays(every = 2))))
        val taken = MRecord(5, "Creatine", "g", "2026-10-01", "08:05:00", 5.0, null, Sources.FLEXNOTES, doseId = 1, due = "2026-10-01 08:00")
        val moved = MedSchedules.withStart(run("2026-10-01"), "2026-10-02")!!
        val log = MedSchedules.doseLog(MedPlan(listOf(s), listOf(moved)), listOf(taken), "2026-10-01", "2026-10-02", "2026-10-03")
        assertEquals(2, log.size)
        assertEquals(MedSchedules.Outcome.MISSED, log.single { it.due != null }.outcome)
        assertEquals(5L, log.single { it.due == null }.record?.id)
    }

    @Test
    fun restartingEndsTheOldRunTheDayBeforeAndStartsFromDayOne() {
        val a = run("2026-10-01", MedPause("2026-10-05", null))
        val (old, new) = MedSchedules.restarted(a, "2026-10-20")
        assertEquals("2026-10-20", old.endedOn)
        assertEquals(listOf(MedPause("2026-10-05", "2026-10-20")), old.pauses)
        assertEquals("2026-10-20", new!!.start)
        assertEquals(a.scheduleId, new.scheduleId)
        val s = schedule(on(10, PhaseUnit.DAYS, dose(1)), off(5, PhaseUnit.DAYS))
        val plan = MedPlan(listOf(s), listOf(old, new.copy(id = 2)))
        assertEquals(2L, plan.current(1, "2026-10-20")!!.id)
        assertEquals(1L, MedSchedules.phaseOn(s, plan.current(1, "2026-10-20")!!, "2026-10-20")!!.day)
        // On or before its own start there's nothing to keep: the same run, moved, with no pauses.
        val (same, none) = MedSchedules.restarted(a, "2026-09-25")
        assertNull(none)
        assertEquals("2026-09-25", same.start)
        assertTrue(same.pauses.isEmpty())
    }
}
