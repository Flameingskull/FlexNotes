package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

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

    // ---------- Earlier runs (#231) ----------

    @Test
    fun runsOfOneScheduleMayNotOverlap() {
        val old = MedActivation(1, 1, "2026-09-01", endedOn = "2026-09-15")
        val now = MedActivation(2, 1, "2026-09-15")
        assertFalse(MedSchedules.overlaps(listOf(old, now), old))
        // Moving the old run's last day past the new start overlaps; another schedule's run never does.
        assertTrue(MedSchedules.overlaps(listOf(old, now), MedSchedules.withLastDay(old, "2026-09-20")!!))
        assertFalse(MedSchedules.overlaps(listOf(MedActivation(3, 2, "2026-09-01")), MedSchedules.withLastDay(old, "2026-09-20")!!))
        assertTrue(MedSchedules.overlaps(listOf(old, now), MedSchedules.withStart(now, "2026-09-10")!!))
    }

    @Test
    fun aRunsLastDayCanMoveButNotBeforeItStarts() {
        val a = run("2026-10-01", MedPause("2026-10-05", "2026-10-12"), ended = "2026-10-20")
        assertEquals("2026-10-19", MedSchedules.lastDayOf(a))
        val shorter = MedSchedules.withLastDay(a, "2026-10-07")!!
        assertEquals("2026-10-08", shorter.endedOn)
        assertEquals(listOf(MedPause("2026-10-05", "2026-10-08")), shorter.pauses)
        assertNull(MedSchedules.withLastDay(a, "2026-09-30"))
        assertNull(MedSchedules.lastDayOf(run("2026-10-01")))
    }

    @Test
    fun aRunSummaryCountsItsDaysAndDosesTaken() {
        val s = schedule(on(1, PhaseUnit.ONGOING, dose(1)))
        val a = run("2026-10-01", ended = "2026-10-05")
        val taken = (1..3).map { d ->
            MRecord(d.toLong(), "Creatine", "g", "2026-10-0$d", "08:00:00", 5.0, null, Sources.FLEXNOTES, doseId = 1, due = "2026-10-0$d 08:00")
        }
        val summary = MedSchedules.runSummary(MedPlan(listOf(s), listOf(a)), taken, a, "2026-10-24")
        assertEquals("2026-10-04", summary.lastDay)
        assertEquals(4L, summary.days)
        assertEquals(75, summary.adherence)
    }

    @Test
    fun theSimpleSetupBuildsOngoingAndCycleSchedules() {
        // Every Monday, Wednesday and Friday with no end (#232): one ongoing phase, never repeating.
        val weekly = MedSchedules.ongoingStarter(MedDays(weekdays = setOf(1, 3, 5)))
        assertEquals(1, weekly.phases.size)
        assertEquals(PhaseUnit.ONGOING, weekly.phases.single().unit)
        assertFalse(weekly.repeats)
        val s = schedule(weekly.phases.single().copy(doses = listOf(dose(1, days = weekly.days))))
        val a = run("2026-10-05")
        assertEquals(1, MedSchedules.dosesOn(s, a, "2027-10-04").size) // a Monday, a year on
        assertTrue(MedSchedules.dosesOn(s, a, "2027-10-05").isEmpty()) // a Tuesday
        // Eight weeks on, four off, repeating; an ongoing unit can't sneak into a cycle.
        val cycle = MedSchedules.cycleStarter(8, PhaseUnit.WEEKS, 4, PhaseUnit.ONGOING)
        assertTrue(cycle.repeats)
        assertEquals(listOf(true, false), cycle.phases.map { it.on })
        assertEquals(listOf(PhaseUnit.WEEKS, PhaseUnit.WEEKS), cycle.phases.map { it.unit })
        assertTrue(cycle.days.everyDay)
        assertEquals(1, MedSchedules.cycleStarter(0, PhaseUnit.DAYS, 1, PhaseUnit.DAYS).phases.first().length)
    }

    @Test
    fun aTaperStepsDownEveryWeekToItsFloor() {
        // 20 mg, down 5 mg every 7 days, to 10 mg at the lowest (#234).
        val d = MedDose(1, "Prednisolone", 20.0, "mg", step = -5.0, stepEvery = 7, stepLimit = 10.0)
        assertEquals(20.0, d.amountOn(0), 0.0)
        assertEquals(20.0, d.amountOn(6), 0.0)
        assertEquals(15.0, d.amountOn(7), 0.0)
        assertEquals(10.0, d.amountOn(14), 0.0)
        assertEquals(10.0, d.amountOn(100), 0.0)
        // With no floor it stops at 0, never below.
        assertEquals(0.0, d.copy(stepLimit = null).amountOn(70), 0.0)
        // A step-up with no ceiling keeps rising; small steps don't pile up float error.
        val up = MedDose(2, "Vitamin D", 0.1, "mg", step = 0.1, stepEvery = 1)
        assertEquals(0.3, up.amountOn(2), 0.0)
        assertEquals(0.2, up.copy(stepLimit = 0.2).amountOn(5), 0.0)
        // No interval or no step means no taper.
        assertEquals(20.0, d.copy(stepEvery = 0).amountOn(30), 0.0)
        assertFalse(d.copy(step = 0.0).steps)
    }

    @Test
    fun dueDosesUseTheTaperedAmountCountedFromThePhaseAndPauses() {
        val tapering = MedDose(1, "Prednisolone", 20.0, "mg", step = -5.0, stepEvery = 7, stepLimit = 5.0)
        val s = schedule(MedPhase(10L, true, 1, PhaseUnit.ONGOING, listOf(tapering)))
        val a = run("2026-10-01")
        assertEquals(20.0, MedSchedules.dosesOn(s, a, "2026-10-07").single().amount, 0.0)
        val second = MedSchedules.dosesOn(s, a, "2026-10-08").single()
        assertEquals(15.0, second.amount, 0.0)
        assertEquals(15.0, second.planned, 0.0)
        // A three-day pause holds the taper back by three days.
        val paused = run("2026-10-01", MedPause("2026-10-03", "2026-10-06"))
        assertEquals(20.0, MedSchedules.dosesOn(s, paused, "2026-10-10").single().amount, 0.0)
        assertEquals(15.0, MedSchedules.dosesOn(s, paused, "2026-10-11").single().amount, 0.0)
        // A dose change still wins for its day, and the planned amount is kept beside it.
        val change = MedDoseChange(1, 1, 1, "2026-10-08", "2026-10-08", 12.5)
        val changed = MedSchedules.dosesOn(s, a, "2026-10-08", listOf(change)).single()
        assertEquals(12.5, changed.amount, 0.0)
        assertEquals(15.0, changed.planned, 0.0)
    }

    // ---------- When a dose counts as missed (#318) ----------

    private fun at(date: String, time: String) = LocalDateTime.parse("${date}T$time:00")

    @Test
    fun aDoseIsMissedOnlyOncePastItsWindow() {
        // Due 08:00 with a 60 minute window: still due inside it, missed after it.
        assertFalse(MedSchedules.isMissed("2026-10-29", "08:00", at("2026-10-29", "08:01"), 60))
        assertFalse(MedSchedules.isMissed("2026-10-29", "08:00", at("2026-10-29", "09:00"), 60))
        assertTrue(MedSchedules.isMissed("2026-10-29", "08:00", at("2026-10-29", "09:01"), 60))
        assertFalse(MedSchedules.isMissed("2026-10-29", "08:00", at("2026-10-29", "07:00"), 30))
        assertTrue(MedSchedules.isMissed("2026-10-29", "08:00", at("2026-10-29", "08:31"), 30))
    }

    @Test
    fun aLateDoseCrossesMidnightCorrectly() {
        // Due 23:30 with a 2 hour window: missed from 01:30 the next morning, not before.
        assertFalse(MedSchedules.isMissed("2026-10-29", "23:30", at("2026-10-30", "01:00"), 120))
        assertTrue(MedSchedules.isMissed("2026-10-29", "23:30", at("2026-10-30", "01:31"), 120))
        assertFalse(MedSchedules.isMissed("2026-10-29", "23:30", at("2026-10-29", "23:59"), 120))
    }

    @Test
    fun endOfDayMeansTheDueDateIsOver() {
        val end = MedSchedules.MISSED_END_OF_DAY
        assertFalse(MedSchedules.isMissed("2026-10-29", "08:00", at("2026-10-29", "23:59"), end))
        assertTrue(MedSchedules.isMissed("2026-10-29", "08:00", at("2026-10-30", "00:00"), end))
        assertFalse(MedSchedules.isMissed("not a date", "08:00", at("2026-10-30", "00:00"), end))
        assertFalse(MedSchedules.isMissed("2026-10-29", "oops", at("2026-10-30", "12:00"), 60))
    }

    @Test
    fun theDoseLogFollowsTheWindowForTodayAndIgnoresItWithoutAClock() {
        val s = schedule(on(1, PhaseUnit.YEARS, dose(1, time = "08:00")))
        val plan = MedPlan(listOf(s), listOf(run("2026-10-29")))
        fun outcome(now: LocalDateTime?, after: Int) =
            MedSchedules.doseLog(plan, emptyList(), "2026-10-29", "2026-10-29", "2026-10-29", missedAfter = after, now = now).single().outcome
        // No clock: today's dose is Due, as before.
        assertEquals(MedSchedules.Outcome.DUE, outcome(null, 60))
        assertEquals(MedSchedules.Outcome.DUE, outcome(at("2026-10-29", "08:30"), 60))
        assertEquals(MedSchedules.Outcome.MISSED, outcome(at("2026-10-29", "09:30"), 60))
        // End of the day keeps it Due all day.
        assertEquals(MedSchedules.Outcome.DUE, outcome(at("2026-10-29", "23:00"), MedSchedules.MISSED_END_OF_DAY))
        // A taken dose never turns Missed.
        val taken = MRecord(5, "Creatine", "g", "2026-10-29", "08:05:00", 5.0, null, Sources.FLEXNOTES, doseId = 1, due = "2026-10-29 08:00")
        val log = MedSchedules.doseLog(plan, listOf(taken), "2026-10-29", "2026-10-29", "2026-10-29", missedAfter = 30, now = at("2026-10-29", "23:00"))
        assertEquals(MedSchedules.Outcome.TAKEN, log.single { it.due != null }.outcome)
    }

    @Test
    fun theMissedChoicesIncludeTheDefault() {
        assertTrue(MedSchedules.MISSED_DEFAULT_MINUTES in MedSchedules.MISSED_CHOICES)
        assertEquals(MedSchedules.MISSED_END_OF_DAY, MedSchedules.MISSED_CHOICES.last())
    }
}
