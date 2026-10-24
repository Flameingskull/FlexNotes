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
        assertEquals("weigh=1|07:15|1,2,3,4,5;photos=0|09:00|7", text)
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
}
