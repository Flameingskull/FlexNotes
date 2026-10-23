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

/** Medication reminders (#203): which reminder is set next, follow-ups, snoozes, and doses already taken or skipped. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MedRemindersTest {

    private val morning = MedDose(1, "Creatine", 5.0, "g", "08:00")
    private val evening = MedDose(2, "Magnesium", 200.0, "mg", "21:00")
    private val alsoMorning = MedDose(3, "Vitamin D", 1000.0, "IU", "08:00")
    private val schedule = MedSchedule(1, "Daily", phases = listOf(MedPhase(10, true, 1, PhaseUnit.YEARS, listOf(morning, evening, alsoMorning))))

    private fun plan(skipped: Map<Long, Set<String>> = emptyMap(), ended: String? = null) =
        MedPlan(listOf(schedule), listOf(MedActivation(1, 1, "2026-10-01", endedOn = ended)), skipped)

    private fun at(text: String) = LocalDateTime.parse(text)

    private fun taken(dose: MedDose, due: String) = MRecord(9, dose.medication, dose.unit, due.take(10), "08:01:00", dose.amount, null, "manual", dose.id, due)

    @Test
    fun theNextReminderIsTheNextOpenDoseTime() {
        val ring = MedReminders.next(plan(), emptyList(), at("2026-10-22T07:00"), nudgeMinutes = 0)!!
        assertEquals(at("2026-10-22T08:00"), ring.at)
        assertEquals("2026-10-22 08:00", ring.key)
        assertFalse(ring.nudge)
        // Doses due at the same time share one reminder.
        assertEquals(2, MedReminders.openAt(plan(), emptyList(), "2026-10-22 08:00").size)
    }

    @Test
    fun aFollowUpComesWhileADoseIsOpen() {
        val ring = MedReminders.next(plan(), emptyList(), at("2026-10-22T08:05"), nudgeMinutes = 30)!!
        assertEquals(at("2026-10-22T08:30"), ring.at)
        assertTrue(ring.nudge)
        // Once both 08:00 doses are taken, the next reminder is the evening one.
        val records = listOf(taken(morning, "2026-10-22 08:00"), taken(alsoMorning, "2026-10-22 08:00"))
        val next = MedReminders.next(plan(), records, at("2026-10-22T08:05"), nudgeMinutes = 30)!!
        assertEquals(at("2026-10-22T21:00"), next.at)
    }

    @Test
    fun skippedDosesAndEndedRunsAreNotReminded() {
        val skipped = mapOf(1L to setOf("2026-10-22 08:00"), 3L to setOf("2026-10-22 08:00"))
        assertEquals(at("2026-10-22T21:00"), MedReminders.next(plan(skipped), emptyList(), at("2026-10-22T07:00"), 30)!!.at)
        assertNull(MedReminders.next(plan(ended = "2026-10-22"), emptyList(), at("2026-10-22T07:00"), 30))
        assertNull(MedReminders.next(MedPlan.EMPTY, emptyList(), at("2026-10-22T07:00"), 30))
    }

    @Test
    fun aSnoozeReplacesTheFollowUpAndCarriesOverMidnight() {
        val snoozes = MedReminders.Snoozes().with("2026-10-22 21:00", at("2026-10-22T21:15"))
        val ring = MedReminders.next(plan(), emptyList(), at("2026-10-22T21:05"), nudgeMinutes = 30, snoozes = snoozes)!!
        assertEquals(at("2026-10-22T21:15"), ring.at)
        assertTrue(ring.snoozed)
        // After 23:59 the next reminder is tomorrow's first dose.
        assertEquals(at("2026-10-23T08:00"), MedReminders.next(plan(), emptyList(), at("2026-10-22T23:59"), 0)!!.at)
        // Stored and read back; ones already rung are dropped.
        val back = MedReminders.Snoozes.decode(snoozes.encode())
        assertEquals(snoozes, back)
        assertTrue(back.pruned(at("2026-10-22T22:00")).until.isEmpty())
        assertTrue(MedReminders.Snoozes.decode("rubbish;=").until.isEmpty())
    }
}
