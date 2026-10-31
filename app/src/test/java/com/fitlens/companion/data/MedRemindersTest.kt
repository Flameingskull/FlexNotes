package com.fitlens.companion.data

import android.app.Application
import com.fitlens.companion.ui.LongHistoryFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Medication reminders (#203, #327): which reminder is set next, follow-ups, snoozes per dose, doses owed a late
 * notification, and doses already taken or skipped. What is shown and pressed is driven in `MedReminderNotificationsTest`.
 */
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
        // Doses due at the same time share one alarm (each gets its own notification when it rings).
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
        // Stored and read back. A snooze whose time has passed stays until it has rung (#327): it is never dropped unrung.
        val back = MedReminders.Snoozes.decode(snoozes.encode())
        assertEquals(snoozes, back)
        assertEquals(snoozes, back.pruned(at("2026-10-22T22:00")))
        assertTrue(MedReminders.Snoozes.decode("rubbish;=").until.isEmpty())
    }

    @Test
    fun aSnoozeIsPerDoseAndTheOldKeyOnlyFormStillDecodes() {
        val evening = "2026-10-22 21:00"
        // New: "<doseId>@<key>". Old: "<key>", every open dose due then. Both in one stored value, rubbish dropped.
        val stored = "2@$evening=2026-10-22 21:15;$evening=2026-10-22 21:20;x@$evening=2026-10-22 21:15;" +
            "2@nonsense=2026-10-22 21:15;5@$evening=nonsense;=;;rubbish"
        val back = MedReminders.Snoozes.decode(stored)
        assertEquals(setOf("2@$evening", evening), back.until.keys)
        assertEquals(listOf<Long?>(2L, null), back.entries().map { it.doseId })
        // Stored values survive an encode and decode unchanged.
        assertEquals(back, MedReminders.Snoozes.decode(back.encode()))
        // The per-dose one covers its dose only; the old one covers every dose due then.
        val one = MedReminders.Snoozes().with(2L, evening, at("2026-10-22T21:15"))
        assertTrue(one.covers(2L, evening))
        assertFalse(one.covers(3L, evening))
        assertFalse(one.covers(2L, "2026-10-22 08:00"))
        assertTrue(MedReminders.Snoozes().with(evening, at("2026-10-22T21:15")).covers(3L, evening))
    }

    @Test
    fun aSnoozedDoseLeavesOutOfItsTimesRingUntilItsSnoozeRings() {
        // Two doses at 08:00 (Creatine id 1, Vitamin D id 3); only Creatine is snoozed.
        val snoozes = MedReminders.Snoozes().with(1L, "2026-10-22 08:00", at("2026-10-22T08:15"))
        assertEquals(at("2026-10-22T08:15"), MedReminders.next(plan(), emptyList(), at("2026-10-22T08:05"), 30, snoozes)!!.at)
        // With both snoozed and 08:00 past, the next reminder is the snooze.
        val both = snoozes.with(3L, "2026-10-22 08:00", at("2026-10-22T08:15"))
        val ring = MedReminders.next(plan(), emptyList(), at("2026-10-22T08:05"), 30, both)!!
        assertEquals(at("2026-10-22T08:15"), ring.at)
        assertTrue(ring.snoozed)
        // Creatine taken: its snooze is no reminder, and the follow-up of 08:00 is for Vitamin D alone.
        val creatineTaken = listOf(taken(morning, "2026-10-22 08:00"))
        val again = MedReminders.next(plan(), creatineTaken, at("2026-10-22T08:05"), 30, snoozes)!!
        assertEquals(at("2026-10-22T08:30"), again.at)
        assertTrue(again.nudge)
        // Both taken: the snoozes are gone with them, and the evening dose is next.
        val noneLeft = listOf(taken(morning, "2026-10-22 08:00"), taken(alsoMorning, "2026-10-22 08:00"))
        assertEquals(at("2026-10-22T21:00"), MedReminders.next(plan(), noneLeft, at("2026-10-22T08:05"), 30, both)!!.at)
    }

    @Test
    fun aSnoozeWhoseTimeHasPassedRingsNowAndIsNeverDroppedUnrung() {
        val snoozes = MedReminders.Snoozes().with(1L, "2026-10-22 08:00", at("2026-10-22T08:15"))
            .with(3L, "2026-10-22 08:00", at("2026-10-22T08:15"))
        val now = at("2026-10-22T08:16")
        // Snoozes tapped in one minute have one time; the first ring must not lose the others (#327).
        val ring = MedReminders.next(plan(), emptyList(), now, 0, snoozes)!!
        assertEquals(now, ring.at)
        assertTrue(ring.snoozed)
        assertEquals(snoozes, snoozes.pruned(now))
        // Only a snooze whose dose is no longer open, or that is over two days old, is dropped.
        val creatineTaken = listOf(taken(morning, "2026-10-22 08:00"))
        val open = { id: Long?, key: String -> MedReminders.openAt(plan(), creatineTaken, key).any { id == null || it.dose.id == id } }
        assertEquals(setOf("3@2026-10-22 08:00"), snoozes.pruned(now, open).until.keys)
        assertTrue(snoozes.pruned(at("2026-10-25T08:00")).until.isEmpty())
        assertEquals(2, snoozes.pruned(at("2026-10-24T08:15")).until.size)
    }

    @Test
    fun aDoseDuePastTheRungThroughTimeIsOwedItsNotificationUntilTwelveHoursOld() {
        val mark = at("2026-10-22T07:59")
        // 08:00 (two doses) is owed at 08:07; the 21:00 dose isn't due yet.
        val owed = MedReminders.catchUp(plan(), emptyList(), at("2026-10-22T08:07"), mark)
        assertEquals(listOf("Creatine", "Vitamin D"), owed.map { it.dose.medication })
        val ring = MedReminders.next(plan(), emptyList(), at("2026-10-22T08:07"), 0, rungThrough = mark)!!
        assertEquals(at("2026-10-22T08:07"), ring.at)
        assertEquals("2026-10-22 08:00", ring.key)
        assertFalse(ring.nudge)
        // Not after the mark: already notified. Taken, skipped or snoozed: not owed.
        assertTrue(MedReminders.catchUp(plan(), emptyList(), at("2026-10-22T08:07"), at("2026-10-22T08:00")).isEmpty())
        assertEquals(
            listOf("Vitamin D"),
            MedReminders.catchUp(plan(), listOf(taken(morning, "2026-10-22 08:00")), at("2026-10-22T08:07"), mark).map { it.dose.medication }
        )
        assertEquals(
            listOf("Creatine"),
            MedReminders.catchUp(plan(), emptyList(), at("2026-10-22T08:07"), mark, MedReminders.Snoozes().with(3L, "2026-10-22 08:00", at("2026-10-22T08:20")))
                .map { it.dose.medication }
        )
        assertEquals(
            listOf("Vitamin D"),
            MedReminders.catchUp(plan(mapOf(1L to setOf("2026-10-22 08:00"))), emptyList(), at("2026-10-22T08:07"), mark).map { it.dose.medication }
        )
        // Twelve hours old is still owed, a minute older isn't; with no mark known nothing is.
        assertEquals(2, MedReminders.catchUp(plan(), emptyList(), at("2026-10-22T20:00"), mark).size)
        assertTrue(MedReminders.catchUp(plan(), emptyList(), at("2026-10-22T20:01"), mark).isEmpty())
        assertEquals(at("2026-10-22T21:00"), MedReminders.next(plan(), emptyList(), at("2026-10-22T20:01"), 0, rungThrough = mark)!!.at)
        assertEquals(at("2026-10-22T21:00"), MedReminders.next(plan(), emptyList(), at("2026-10-22T08:07"), 0)!!.at)
    }

    @Test
    fun followUpsThatCameDueSinceTheLastRingAreOwed() {
        val mark = at("2026-10-22T08:00")
        // At 08:30 both 08:00 doses' 30-minute follow-ups are due; at 08:29 they are not.
        assertEquals(listOf("Creatine", "Vitamin D"), MedReminders.followUps(plan(), emptyList(), at("2026-10-22T08:30"), mark, 30).map { it.dose.medication })
        assertTrue(MedReminders.followUps(plan(), emptyList(), at("2026-10-22T08:29"), mark, 30).isEmpty())
        // Rung through 08:30 already: not owed again. No follow-up chosen: none.
        assertTrue(MedReminders.followUps(plan(), emptyList(), at("2026-10-22T08:31"), at("2026-10-22T08:30"), 30).isEmpty())
        assertTrue(MedReminders.followUps(plan(), emptyList(), at("2026-10-22T08:30"), mark, 0).isEmpty())
        // Taken or snoozed doses have none; one older than the twelve-hour limit has none.
        assertEquals(
            listOf("Vitamin D"),
            MedReminders.followUps(plan(), listOf(taken(morning, "2026-10-22 08:00")), at("2026-10-22T08:30"), mark, 30).map { it.dose.medication }
        )
        val snoozed = MedReminders.Snoozes().with(3L, "2026-10-22 08:00", at("2026-10-22T08:45"))
        assertEquals(listOf("Creatine"), MedReminders.followUps(plan(), emptyList(), at("2026-10-22T08:30"), mark, 30, snoozed).map { it.dose.medication })
        assertTrue(MedReminders.followUps(plan(), emptyList(), at("2026-10-22T20:31"), mark, 30).isEmpty())
        // The alarm is set for a follow-up that is already due, not passed over.
        val ring = MedReminders.next(plan(), emptyList(), at("2026-10-22T08:31"), 30, rungThrough = mark)!!
        assertEquals(at("2026-10-22T08:31"), ring.at)
        assertTrue(ring.nudge)
    }

    @Test
    fun aDoseDueRightNowIsOwedItsNotificationNotPassedOver() {
        // The alarm for 08:00 is not yet delivered when the alarm is set again at 08:00:20.
        val now = at("2026-10-22T08:00:20")
        val ring = MedReminders.next(plan(), emptyList(), now, 30, rungThrough = at("2026-10-22T07:59"))!!
        assertEquals(now, ring.at)
        assertEquals("2026-10-22 08:00", ring.key)
    }

    @Test
    fun overYearsOfRecordsTheNextReminderComesFromTheFullPlan() {
        val f = LongHistoryFixture
        val snap = f.snapshot()
        val plan = snap.medPlan
        // A thousand more taken Metformin doses on top of the fixture's own values.
        val end = LocalDate.parse(f.END)
        val many = (15L..1014L).map { k ->
            val date = end.minusDays(k).format(Dates.ISO)
            MRecord(100_000L + k, f.METFORMIN, "mg", date, "08:03:00", 500.0, null, Sources.FLEXNOTES, 1L, "$date 08:00")
        }
        val records = snap.medicationView.records + many
        assertTrue(records.size > 1000)
        val ring = MedReminders.next(plan, records, at("${f.END}T07:59"), 30)!!
        assertEquals(f.KEY_0800, ring.key)
        assertEquals(3, MedReminders.openAt(plan, records, f.KEY_0800).size)
        // At 10:00 on the last day, with first notifications rung through 07:59, the morning's four doses are owed and
        // the earlier days' second Metformin, never taken in years, is not.
        val owed = MedReminders.catchUp(plan, records, at("${f.END}T10:00"), at("${f.END}T07:59"))
        assertEquals(listOf(f.KEY_0800, f.KEY_0800, f.KEY_0800, f.KEY_0805), owed.map { it.due })
        assertEquals(f.KEY_0800, MedReminders.next(plan, records, at("${f.END}T10:00"), 30, rungThrough = at("${f.END}T07:59"))!!.key)
    }

    @Test
    fun anInjectedDoseTakenFromTheReminderGoesToTheNextSite() {
        val injected = MedPlan(
            listOf(schedule), listOf(MedActivation(1, 1, "2026-10-01")),
            info = mapOf("Creatine" to MedInfo("Creatine", injected = true))
        )
        val open = MedReminders.openAt(injected, emptyList(), "2026-10-22 08:00")
        val sites = MedReminders.sitesFor(injected, emptyList(), open)
        // Creatine is injected and goes to the first site; Vitamin D isn't, so it gets none.
        assertEquals(open.map { if (it.dose.medication == "Creatine") InjectionSite.ABDOMEN_UPPER_LEFT else null }, sites)
        val used = listOf(taken(morning, "2026-10-21 08:00").copy(site = InjectionSite.ABDOMEN_UPPER_LEFT.key))
        assertEquals(InjectionSite.ABDOMEN_UPPER_RIGHT, MedReminders.sitesFor(injected, used, open).filterNotNull().single())
        // Two doses of it at once rotate rather than share a site.
        val creatine = open.first { it.dose.medication == "Creatine" }
        assertEquals(
            listOf(InjectionSite.ABDOMEN_UPPER_LEFT, InjectionSite.ABDOMEN_UPPER_RIGHT),
            MedReminders.sitesFor(injected, emptyList(), listOf(creatine, creatine))
        )
    }
}
