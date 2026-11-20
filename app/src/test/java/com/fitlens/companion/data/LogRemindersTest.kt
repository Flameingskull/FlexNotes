package com.fitlens.companion.data

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** Logging reminders (#219): storing them, and which one rings next on which day. 2026-10-24 is a Saturday. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LogRemindersTest {

    private fun at(text: String) = LocalDateTime.parse(text)

    @Test
    fun encodesAndDecodesEveryKind() {
        val set = LogReminderSet()
            .with(LogReminder(LogKind.WEIGH_IN, true, "07:15", setOf(1, 2, 3, 4, 5)))
            .with(LogReminder(LogKind.PHOTOS, false, "09:00", setOf(7)))
        val text = set.encode()
        assertEquals("weigh=1|07:15|1,2,3,4,5|w|2||1|1;photos=0|09:00|7|w|2||1|1", text)
        assertEquals(set, LogReminderSet.decode(text))
    }

    @Test
    fun unreadablePartsFallBackToDefaults() {
        val set = LogReminderSet.decode("weigh=1|25:99|1;nonsense;workout=1|18:00|0,9,3")
        assertEquals(LogReminder.default(LogKind.WEIGH_IN), set.of(LogKind.WEIGH_IN))
        assertEquals(setOf(3), set.of(LogKind.WORKOUT).days)
        assertFalse(LogReminderSet.decode(null).all.any { it.on })
    }

    @Test
    fun nothingRingsWhileEverythingIsOff() {
        assertNull(LogReminders.next(LogReminderSet(), at("2026-10-24T10:00")))
    }

    @Test
    fun ringsLaterTodayOrOnTheNextChosenDay() {
        val weigh = LogReminder(LogKind.WEIGH_IN, true, "07:30", (1..7).toSet())
        assertEquals(at("2026-10-24T07:30"), LogReminders.nextFor(weigh, at("2026-10-24T06:00")))
        assertEquals(at("2026-10-25T07:30"), LogReminders.nextFor(weigh, at("2026-10-24T07:30")))
        // Mondays only, asked on a Saturday: the coming Monday.
        val monday = weigh.copy(days = setOf(1))
        assertEquals(at("2026-10-26T07:30"), LogReminders.nextFor(monday, at("2026-10-24T08:00")))
        // A Saturday reminder already past today comes back a week later.
        assertEquals(at("2026-10-31T07:30"), LogReminders.nextFor(weigh.copy(days = setOf(6)), at("2026-10-24T08:00")))
        assertNull(LogReminders.nextFor(weigh.copy(days = emptySet()), at("2026-10-24T08:00")))
    }

    @Test
    fun theEarliestWinsAndMatchingTimesRingTogether() {
        val set = LogReminderSet()
            .with(LogReminder(LogKind.WEIGH_IN, true, "07:30", (1..7).toSet()))
            .with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(7)))
            .with(LogReminder(LogKind.MEASUREMENTS, true, "08:00", setOf(7)))
            .with(LogReminder(LogKind.WORKOUT, false, "06:00", (1..7).toSet()))
        val first = LogReminders.next(set, at("2026-10-24T09:00"))!!
        assertEquals(at("2026-10-25T07:30"), first.at)
        assertEquals(listOf(LogKind.WEIGH_IN), first.kinds)
        val second = LogReminders.next(set, first.at)!!
        assertEquals(at("2026-10-25T08:00"), second.at)
        assertEquals(setOf(LogKind.PHOTOS, LogKind.MEASUREMENTS), second.kinds.toSet())
        assertEquals("2026-10-25 08:00", second.key)
    }

    @Test
    fun dueAtMatchesDayAndTime() {
        val set = LogReminderSet()
            .with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(7)))
            .with(LogReminder(LogKind.WORKOUT, true, "08:00", setOf(1)))
        assertEquals(listOf(LogKind.PHOTOS), LogReminders.dueAt(set, "2026-10-25 08:00"))
        assertTrue(LogReminders.dueAt(set, "2026-10-25 08:01").isEmpty())
        assertTrue(LogReminders.dueAt(set, "not a time").isEmpty())
    }

    @Test
    fun remindersStoredBeforeTheNewPatternsReadBackUnchanged() {
        // Exactly as 1.0.197 stored them (#332): three fields per reminder.
        val set = LogReminderSet.decode("weigh=1|07:30|1,2,3,4,5;measure=1|08:00|7;photos=0|09:00|6")
        val weigh = set.of(LogKind.WEIGH_IN)
        assertEquals(LogReminder(LogKind.WEIGH_IN, true, "07:30", setOf(1, 2, 3, 4, 5)), weigh)
        assertEquals(LogPattern.WEEKDAYS, weigh.pattern)
        assertNull(weigh.start)
        assertEquals(true, weigh.quiet)
        assertEquals(LogReminder(LogKind.MEASUREMENTS, true, "08:00", setOf(7)), set.of(LogKind.MEASUREMENTS))
        assertEquals(LogReminder(LogKind.PHOTOS, false, "09:00", setOf(6)), set.of(LogKind.PHOTOS))
        assertNull(set.armedFor)
        // They ring when they always did: weekdays at 07:30, asked on Saturday 24 October, is Monday 26 October.
        assertEquals(at("2026-10-26T07:30"), LogReminders.nextFor(weigh, at("2026-10-24T08:00")))
        assertEquals(at("2026-10-25T08:00"), LogReminders.nextFor(set.of(LogKind.MEASUREMENTS), at("2026-10-24T08:00")))
        // Saved again, they read back the same.
        assertEquals(set, LogReminderSet.decode(set.encode()))
    }

    @Test
    fun theNewFieldsAndTheArmedTimeRoundTrip() {
        val r = LogReminder(LogKind.PHOTOS, true, "08:00", setOf(7), LogPattern.EVERY_WEEKS, 2, "2026-11-22", 1, false)
        val set = LogReminderSet().with(r).copy(armedFor = "2026-11-22 08:00")
        val text = set.encode()
        assertEquals("photos=1|08:00|7|k|2|2026-11-22|1|0;armed=2026-11-22 08:00", text)
        assertEquals(set, LogReminderSet.decode(text))
        // An unreadable pattern, every, start or day of the month falls back to something that still rings.
        val odd = LogReminderSet.decode("photos=1|08:00|7|x|0|not-a-date|99|1").of(LogKind.PHOTOS)
        assertEquals(LogPattern.WEEKDAYS, odd.pattern)
        assertEquals(1, odd.every)
        assertNull(odd.start)
        assertEquals(31, odd.monthDay)
    }
    @Test
    fun everyTwoWeeksRingsOnExactlyThoseDatesAcrossAMonthAndYearEnd() {
        // Every 2 weeks on Sundays from Sunday 22 November 2026.
        val r = LogReminder(LogKind.PHOTOS, true, "08:00", setOf(7), LogPattern.EVERY_WEEKS, 2, "2026-11-22")
        assertEquals(
            listOf("2026-11-22T08:00", "2026-12-06T08:00", "2026-12-20T08:00", "2027-01-03T08:00", "2027-01-17T08:00").map { at(it) },
            LogReminders.upcoming(r, at("2026-11-20T12:00"), 5)
        )
        // The Sundays between never ring.
        val set = LogReminderSet().with(r)
        assertTrue(LogReminders.dueAt(set, "2026-11-29 08:00").isEmpty())
        assertTrue(LogReminders.dueAt(set, "2026-12-27 08:00").isEmpty())
        assertEquals(listOf(LogKind.PHOTOS), LogReminders.dueAt(set, "2026-12-06 08:00"))
    }
    @Test
    fun everyTwoWeeksKeepsItsLocalTimeAcrossAClockChange() {
        // The UK's clocks go forward on Sunday 28 March 2027; the reminder stays at 08:00 on the phone's clock.
        val r = LogReminder(LogKind.MEASUREMENTS, true, "08:00", setOf(7), LogPattern.EVERY_WEEKS, 2, "2027-03-14")
        val rings = LogReminders.upcoming(r, at("2027-03-13T09:00"), 3)
        assertEquals(listOf("2027-03-14T08:00", "2027-03-28T08:00", "2027-04-11T08:00").map { at(it) }, rings)
        val london = java.time.ZoneId.of("Europe/London")
        // 08:00 before the change is 08:00 UTC; 08:00 on the day of the change is 07:00 UTC.
        assertEquals(java.time.Instant.parse("2027-03-14T08:00:00Z").toEpochMilli(), ExactAlarms.epochMillis(rings[0], london, 0L))
        assertEquals(java.time.Instant.parse("2027-03-28T07:00:00Z").toEpochMilli(), ExactAlarms.epochMillis(rings[1], london, 0L))
    }
    @Test
    fun theThirtyFirstRingsOnTheLastDayOfAShorterMonth() {
        val r = LogReminder(LogKind.MEASUREMENTS, true, "08:00", emptySet(), LogPattern.MONTH_DAY, start = "2027-01-01", monthDay = 31)
        assertEquals(
            listOf("2027-01-31T08:00", "2027-02-28T08:00", "2027-03-31T08:00", "2027-04-30T08:00").map { at(it) },
            LogReminders.upcoming(r, at("2026-12-31T09:00"), 4)
        )
        // A leap year's February ends on the 29th.
        assertEquals(at("2028-02-29T08:00"), LogReminders.nextFor(r, at("2028-02-01T00:00")))
    }

    @Test
    fun everyThreeDaysCountsFromItsStartAndWaitsForIt() {
        val r = LogReminder(LogKind.WEIGH_IN, true, "07:00", emptySet(), LogPattern.EVERY_DAYS, 3, "2026-10-30")
        // Nothing before the start; then 30 October, 2 November and 5 November, across the month end.
        assertEquals(
            listOf("2026-10-30T07:00", "2026-11-02T07:00", "2026-11-05T07:00").map { at(it) },
            LogReminders.upcoming(r, at("2026-10-24T10:00"))
        )
        // A weekday reminder with a start date waits for it too: Mondays from Monday 9 November.
        val mondays = LogReminder(LogKind.WORKOUT, true, "17:30", setOf(1), start = "2026-11-09")
        assertEquals(at("2026-11-09T17:30"), LogReminders.nextFor(mondays, at("2026-10-24T10:00")))
    }
    @Test
    fun theDatesShownAreTheOnesThatRing() {
        val set = LogReminderSet()
            .with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(7), LogPattern.EVERY_WEEKS, 4, "2026-11-01"))
            .with(LogReminder(LogKind.MEASUREMENTS, true, "08:00", emptySet(), LogPattern.MONTH_DAY, start = "2026-11-01", monthDay = 31))
        var now = at("2026-10-31T12:00")
        val shown = set.all.filter { it.on }.associate { it.kind to LogReminders.upcoming(it, now, 4) }
        // Follow the alarm chain: each ring's kinds are what dueAt says is due then.
        val rung = HashMap<LogKind, MutableList<LocalDateTime>>()
        repeat(12) {
            val ring = LogReminders.next(set, now) ?: return@repeat
            assertEquals(ring.kinds.toSet(), LogReminders.dueAt(set, ring.key).toSet())
            ring.kinds.forEach { k -> rung.getOrPut(k) { ArrayList() }.add(ring.at) }
            now = ring.at
        }
        shown.forEach { (kind, dates) -> assertEquals(dates, rung.getValue(kind).take(dates.size)) }
        assertEquals(4, shown.getValue(LogKind.MEASUREMENTS).size)
    }
    @Test
    fun aReminderWhoseAlarmNeverRangIsOwedForTwelveHours() {
        val set = LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(7))).copy(armedFor = "2026-10-25 08:00")
        // The phone was off over Sunday 08:00 and came back at 09:30: that reminder is owed.
        val owed = LogReminders.owed(set, at("2026-10-25T09:30")).single()
        assertEquals("2026-10-25 08:00", owed.key)
        assertEquals(listOf(LogKind.PHOTOS), owed.kinds)
        // Not when it is the alarm ringing now, not before its time, and not once it is more than 12 hours old.
        val none = emptyList<LogReminders.Ring>()
        assertEquals(none, LogReminders.owed(set, at("2026-10-25T09:30"), rang = "2026-10-25 08:00"))
        assertEquals(none, LogReminders.owed(set, at("2026-10-25T07:59")))
        assertEquals(none, LogReminders.owed(set, at("2026-10-25T20:01")))
        // Nothing is owed when no alarm was noted (stored before #332).
        assertEquals(none, LogReminders.owed(set.copy(armedFor = null), at("2026-10-25T09:30")))
    }
    @Test
    fun pickedMeasurementsRoundTripWhateverTheirNames() {
        // A name may hold the characters the stored text is split on (#332).
        val picks = listOf("Waist circumference", "Arm, left", "Hips|cm;2=x", "Body fat %")
        val r = LogReminder(LogKind.MEASUREMENTS, true, "08:00", setOf(7), measures = picks)
        val text = LogReminderSet().with(r).encode()
        assertEquals("measure=1|08:00|7|w|2||1|1|Waist+circumference,Arm%2C+left,Hips%7Ccm%3B2%3Dx,Body+fat+%25", text)
        assertEquals(r, LogReminderSet.decode(text).of(LogKind.MEASUREMENTS))
        // Stored by 1.0.197, or with no picks: none picked, and nothing extra is written.
        assertEquals(emptyList<String>(), LogReminderSet.decode("measure=1|08:00|7").of(LogKind.MEASUREMENTS).measures)
        assertEquals("measure=1|08:00|7|w|2||1|1", LogReminderSet().with(r.copy(measures = emptyList())).encode())
        // A part that can't be read is dropped and the rest kept.
        val odd = LogReminderSet.decode("measure=1|08:00|7|w|2||1|1|Waist,%E0%A,,Waist").of(LogKind.MEASUREMENTS)
        assertEquals(listOf("Waist"), odd.measures)
    }
    /** A snapshot whose body area is Bodyweight, Waist, Chest, a switched-off Hips and a medication, with [records]. */
    private fun bodySnap(vararg records: MRecord): Snapshot {
        val defs = listOf(
            MeasurementDef("Bodyweight", "kg", 0, 0, 0.0, true),
            MeasurementDef("Waist", "cm", 1, 0, 0.0, true),
            MeasurementDef("Chest", "cm", 2, 0, 0.0, true),
            MeasurementDef("Hips", "cm", 3, 0, 0.0, false),
            MeasurementDef("Metformin", "mg", 4, 0, 0.0, true, medication = true)
        )
        val base = com.fitlens.companion.ui.LongHistoryFixture.snapshot()
        return Snapshot(base.library, base.setPart, base.notes, BodyPart(defs, records.toList(), "kg"), base.photoPart, "kg", java.io.File("photos"))
    }

    /** A value for [name] on Saturday 24 October, from [source]. */
    private fun rec(name: String, source: String = Sources.FLEXNOTES) = MRecord(0L, name, "cm", "2026-10-24", "07:30:00", 80.0, null, source)

    /** Picked in lower case, and one that no longer exists. */
    private val picked = LogReminder(LogKind.MEASUREMENTS, true, "08:00", setOf(6), measures = listOf("chest", "Waist", "Gone"))

    @Test
    fun aMeasurementsReminderIsOfferedTheBodyTrackersMeasurementsButTheWeight() {
        // Not the weight (its own reminder), a switched-off one or a medication.
        assertEquals(listOf("Waist", "Chest"), LogReminders.measureChoices(bodySnap()))
        // A pick the Body tracker doesn't show is left out; the rest in its order, under its names.
        assertEquals(listOf("Waist", "Chest"), LogReminders.measuresFor(bodySnap(), picked))
    }
    @Test
    fun aMeasurementsReminderCountsOnlyThePickedMeasurementsTheUserLogged() {
        val day = "2026-10-24"
        // The weight and another value don't count: both are still to log.
        val others = bodySnap(rec("Bodyweight"), rec("Hips"))
        assertEquals(listOf("Waist", "Chest"), LogReminders.measuresLeft(others, picked, day))
        assertEquals(false, LogReminders.isLogged(others, picked, day))
        // One logged: the other is left. Both: the day is logged.
        assertEquals(listOf("Chest"), LogReminders.measuresLeft(bodySnap(rec("Waist")), picked, day))
        assertEquals(true, LogReminders.isLogged(bodySnap(rec("Waist"), rec("Chest")), picked, day))
        // None picked: nothing to count, so never logged.
        assertEquals(false, LogReminders.isLogged(bodySnap(rec("Waist"), rec("Chest")), picked.copy(measures = emptyList()), day))
        // A value read from Health Connect counts like a typed one (owner, 2026-11-19): with Waist from it, Chest is left.
        assertEquals(listOf("Chest"), LogReminders.measuresLeft(bodySnap(rec("Waist", Sources.HEALTH_CONNECT)), picked, day))
        assertEquals(true, LogReminders.isLogged(bodySnap(rec("Waist", Sources.HEALTH_CONNECT), rec("Chest")), picked, day))
        // The other kinds are unchanged: a weigh-in reminder counts the weight, typed in or from Health Connect.
        val weigh = LogReminder(LogKind.WEIGH_IN, true, "07:30", setOf(6))
        assertEquals(true, LogReminders.isLogged(bodySnap(rec("Bodyweight")), weigh, day))
        assertEquals(true, LogReminders.isLogged(bodySnap(rec("Bodyweight", Sources.HEALTH_CONNECT)), weigh, day))
    }
    @Test
    fun aCatchUpOwesEveryKindMissedNotOnlyTheLast() {
        // Sunday 25 October: a weigh-in at 07:30 and photos at 08:00, the phone off from 07:00 until 09:00.
        val set = LogReminderSet()
            .with(LogReminder(LogKind.WEIGH_IN, true, "07:30", (1..7).toSet()))
            .with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(7)))
            .copy(armedFor = "2026-10-25 07:30")
        val owed = LogReminders.owed(set, at("2026-10-25T09:00"))
        assertEquals(listOf("2026-10-25 07:30", "2026-10-25 08:00"), owed.map { it.key })
        assertEquals(listOf(listOf(LogKind.WEIGH_IN), listOf(LogKind.PHOTOS)), owed.map { it.kinds })
        // The alarm ringing now, for 07:30, owes only what came after it.
        assertEquals(listOf("2026-10-25 08:00"), LogReminders.owed(set, at("2026-10-25T09:00"), rang = "2026-10-25 07:30").map { it.key })
    }
    @Test
    fun aCatchUpNeverOwesYesterday() {
        // A workout reminder at 17:30 on Monday 26 October, the phone off until 05:00 on Tuesday: 11.5 hours, but yesterday.
        val workout = LogReminder(LogKind.WORKOUT, true, "17:30", (1..7).toSet())
        val set = LogReminderSet().with(workout).copy(armedFor = "2026-10-26 17:30")
        assertEquals(emptyList<LogReminders.Ring>(), LogReminders.owed(set, at("2026-10-27T05:00")))
        // One due at midnight is still owed that morning.
        val midnight = LogReminderSet().with(workout.copy(time = "00:00")).copy(armedFor = "2026-10-27 00:00")
        assertEquals(listOf("2026-10-27 00:00"), LogReminders.owed(midnight, at("2026-10-27T05:00")).map { it.key })
    }
    @Test
    fun everyWeeksIsClampedToAYear() {
        // Every 60 weeks is read back as every 52, so it still rings: Sunday 1 November 2026, then 52 weeks later.
        val r = LogReminderSet.decode("photos=1|08:00|7|k|60|2026-11-01|1|1").of(LogKind.PHOTOS)
        assertEquals(52, r.every)
        assertEquals(listOf("2026-11-01T08:00", "2027-10-31T08:00").map { at(it) }, LogReminders.upcoming(r, at("2026-10-31T12:00"), 2))
        // Every 365 days is still allowed.
        assertEquals(365, LogReminderSet.decode("weigh=1|07:00|1|d|365|2026-11-01|1|1").of(LogKind.WEIGH_IN).every)
        // Made in the app with 60 weeks (not read back), it rings every 52 weeks too.
        assertEquals(at("2027-10-31T08:00"), LogReminders.upcoming(r.copy(every = 60), at("2026-10-31T12:00"), 2).getOrNull(1))
    }
    @Test
    fun aCountingPatternWithoutAStartDoesNotRing() {
        // Every 3 days, or every 2 weeks, needs a date to count from: without one it never rings (never counted from 1970).
        val days = LogReminder(LogKind.WEIGH_IN, true, "07:00", emptySet(), LogPattern.EVERY_DAYS, 3)
        val weeks = LogReminder(LogKind.PHOTOS, true, "08:00", setOf(7), LogPattern.EVERY_WEEKS, 2)
        assertEquals(false, days.schedulable)
        assertEquals(false, weeks.schedulable)
        assertNull(LogReminders.nextFor(days, at("2026-10-24T10:00")))
        assertNull(LogReminders.next(LogReminderSet().with(days).with(weeks), at("2026-10-24T10:00")))
        // Given a start, it rings; weekdays and a day of the month need none.
        assertEquals(at("2026-10-25T07:00"), LogReminders.nextFor(days.copy(start = "2026-10-25"), at("2026-10-24T10:00")))
        assertEquals(true, LogReminder(LogKind.PHOTOS, true, "08:00", setOf(7)).schedulable)
        assertEquals(true, LogReminder(LogKind.MEASUREMENTS, true, "08:00", emptySet(), LogPattern.MONTH_DAY, monthDay = 1).schedulable)
    }
    // END OF TESTS
}
