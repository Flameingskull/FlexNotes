package com.fitlens.companion.data

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.fitlens.companion.R
import com.fitlens.companion.ui.LongHistoryFixture
import com.fitlens.companion.ui.TodayWidgetActions
import com.fitlens.companion.ui.TodayWidgets
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

/**
 * One notification per dose (#327), driven the way Android does: a real alarm receiver, real notification buttons, a
 * real Room database, and what is posted, set and recorded is what each test asserts. [LongHistoryFixture.END] is
 * Friday 2026-10-30; its schedule has three doses at 08:00 (Metformin 500 mg, Metformin 250 mg and the injected
 * Semaglutide 0.5 mg) and Vitamin D at 08:05. Tests that look at the 08:00 doses alone take Vitamin D first.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MedReminderNotificationsTest {

    private val f = LongHistoryFixture
    private lateinit var h: ReminderHarness

    @Before
    fun setUp() {
        h = ReminderHarness.start(ApplicationProvider.getApplicationContext(), f.clockAt("08:00"))
    }

    @After
    fun tearDown() = h.stop()

    // ---- helpers ----------------------------------------------------------------------------------------------

    private fun str(id: Int, vararg args: Any): String = h.app.getString(id, *args)

    /** Lets time pass: the phone's clock now reads [time] on [date]. */
    private fun clock(time: String, date: String = f.END) {
        Dates.clockSource = f.clockAt(time, date)
    }

    private fun dt(hour: Int, minute: Int, date: String = f.END): LocalDateTime = LocalDateTime.parse("${date}T%02d:%02d".format(hour, minute))

    /** The epoch time Android knows an alarm clock by, for a minute on the phone's clock. */
    private fun millis(at: LocalDateTime): Long = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** The mark, with the fingerprint of the plan as it is now: so the plan counts as unchanged since that ring. */
    private fun setMark(text: String?) {
        val stamp = stampNow()
        h.setDevice { it.copy(medRungThrough = text, medPlanStamp = stamp) }
    }

    private fun stampNow() = MedReminders.planStamp(h.snapshot().medPlan)

    private fun snoozes() = MedReminders.Snoozes.decode(Settings.current().medSnoozes)

    private fun recordsOf(doseId: Long): List<MRecord> = h.snapshot().medicationView.records.filter { it.doseId == doseId }

    private fun doseId(key: String, medication: String, amount: Double? = null): Long =
        h.dueAt(key).first { it.dose.medication == medication && (amount == null || it.amount == amount) }.dose.id

    private fun tag(doseId: Long, key: String) = MedReminders.tagOf(doseId, key)

    private fun posted(textStart: String): Posted = h.notifications().first { it.text.startsWith(textStart) }

    private fun tags(): Set<String> = h.notifications().mapNotNull { it.tag }.toSet()

    /** The day and time as a title shows them, always one layout: "Fri 30 Oct, 8:00 AM". */
    private fun dayAndTime(hhmm: String, date: String) = "${Dates.day(date)}, ${Dates.time(h.app, hhmm)}"

    private fun dueTitle(hhmm: String, date: String = f.END) = str(R.string.mr_title, dayAndTime(hhmm, date))

    private fun againTitle(hhmm: String, date: String = f.END) = str(R.string.mr_title_again, dayAndTime(hhmm, date))

    private fun snoozeLabel() = str(R.string.mr_snooze, MedReminders.SNOOZE_MINUTES)

    private fun arm() = runBlocking { MedReminders.arm(h.app) }

    /** Delivers the alarm for [key] and waits until [expected] notifications are posted and the next alarm is set. */
    private fun ring(key: String, expected: Int, intent: Intent = h.medAlarm(key)) {
        h.fire(intent) { h.notifications().size == expected && h.nextAlarm() != null }
        h.settle()
    }

    /** What a person swiping a notification away does. */
    private fun swipeAway(posted: Posted) {
        h.app.getSystemService(NotificationManager::class.java).cancel(posted.tag, posted.id)
    }

    private fun load() = h.loadPlan(f.MED_RUNS, f.MED_INFOS)

    /** The three 08:00 doses only: Vitamin D (08:05) is taken ahead so its own alarm is out of the way. */
    private fun loadMorningOnly() {
        load()
        h.take(f.KEY_0805)
    }

    // ---- one notification per dose ----------------------------------------------------------------------------

    @Test
    fun threeDosesDueAtOneMinuteAreThreeNotificationsEachNamingItsOwnMedication() {
        load()
        ring(f.KEY_0800, 3)

        val shown = h.notifications()
        assertEquals(3, shown.size)
        assertEquals(listOf("Metformin 250 mg", "Metformin 500 mg", "Semaglutide 0.5 mg"), shown.map { it.text }.sorted())
        assertEquals(List(3) { dueTitle("08:00") }, shown.map { it.title })
        assertEquals(setOf(
            tag(doseId(f.KEY_0800, "Metformin", 500.0), f.KEY_0800),
            tag(doseId(f.KEY_0800, "Metformin", 250.0), f.KEY_0800),
            tag(doseId(f.KEY_0800, "Semaglutide"), f.KEY_0800)
        ), tags())
        shown.forEach {
            assertEquals(MedReminders.NOTIFICATION_ID, it.id)
            assertEquals(3, it.actions.size)
            // Expanded, the text is the same one dose, in full.
            assertEquals(it.text, it.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString())
            assertEquals(f.KEY_0800, it.notification.extras.getString(MedReminders.EXTRA_AT))
        }
        // Vitamin D is due at 08:05: not yet, and the next alarm is for it.
        assertTrue(shown.none { "Vitamin" in it.text })
        assertEquals(dt(8, 5), h.nextAlarm()!!.at)
        assertEquals(f.KEY_0805, h.nextAlarm()!!.key)
    }

    @Test
    fun takenSkipAndSnoozeEachActOnTheirOneDoseAtTheCurrentTime() {
        loadMorningOnly()
        ring(f.KEY_0800, 3)
        clock("08:03")
        val m500 = doseId(f.KEY_0800, "Metformin", 500.0)
        val m250 = doseId(f.KEY_0800, "Metformin", 250.0)
        val sema = doseId(f.KEY_0800, "Semaglutide")

        // Taken on Metformin 500 logs that dose only, now (08:03), not the time it was due.
        h.press(posted("Metformin 500"), str(R.string.md_take)) { recordsOf(m500).isNotEmpty() && h.notifications().size == 2 }
        h.settle()
        val rec = recordsOf(m500).single()
        assertEquals(f.END, rec.date)
        assertEquals("08:03:00", rec.time)
        assertEquals(f.KEY_0800, rec.due)
        assertEquals(500.0, rec.value, 0.0)
        assertEquals(setOf(m250, sema), h.openAt(f.KEY_0800).map { it.dose.id }.toSet())
        assertEquals(setOf(tag(m250, f.KEY_0800), tag(sema, f.KEY_0800)), tags())

        // Skip on Metformin 250 skips that dose only.
        h.press(posted("Metformin 250"), str(R.string.md_skip)) { h.snapshot().medPlan.isSkipped(m250, f.KEY_0800) && h.notifications().size == 1 }
        h.settle()
        assertTrue(recordsOf(m250).isEmpty())
        assertFalse(h.snapshot().medPlan.isSkipped(m500, f.KEY_0800))
        assertEquals(listOf(sema), h.openAt(f.KEY_0800).map { it.dose.id })
        assertEquals(setOf(tag(sema, f.KEY_0800)), tags())

        // Snooze on Semaglutide snoozes that dose only, for 15 minutes from now, and takes its notification down.
        h.press(posted("Semaglutide"), snoozeLabel()) { snoozes().until.isNotEmpty() && h.notifications().isEmpty() }
        h.settle()
        assertEquals(mapOf(tag(sema, f.KEY_0800) to "${f.END} 08:18"), snoozes().until)
        assertTrue(recordsOf(sema).isEmpty())
        assertEquals(listOf(sema), h.openAt(f.KEY_0800).map { it.dose.id })
        assertEquals(1, recordsOf(m500).size)
    }

    @Test
    fun aSecondPressOfTakenOrAStaleButtonRecordsNothingMore() {
        loadMorningOnly()
        ring(f.KEY_0800, 3)
        clock("08:03")
        val m500 = doseId(f.KEY_0800, "Metformin", 500.0)
        val m250 = doseId(f.KEY_0800, "Metformin", 250.0)
        val first = posted("Metformin 500")
        h.press(first, str(R.string.md_take)) { recordsOf(m500).isNotEmpty() }
        h.settle()

        // Later, the buttons of the same (now stale) notification are pressed again.
        clock("08:10")
        h.press(first, str(R.string.md_take))
        h.press(first, str(R.string.md_skip))
        h.press(first, snoozeLabel())
        // A live button afterwards, so the stale presses have certainly been handled before the checks.
        h.press(posted("Metformin 250"), str(R.string.md_skip)) { h.snapshot().medPlan.isSkipped(m250, f.KEY_0800) }
        h.settle()

        val rec = recordsOf(m500).single()
        assertEquals("08:03:00", rec.time)
        assertFalse(h.snapshot().medPlan.isSkipped(m500, f.KEY_0800))
        assertTrue(snoozes().until.isEmpty())
    }

    // ---- snooze ----------------------------------------------------------------------------------------------

    @Test
    fun snoozingTwoInTheSameMinuteRingsBothWhenTheTimeComes() {
        loadMorningOnly()
        ring(f.KEY_0800, 3)
        clock("08:02")
        val m500 = doseId(f.KEY_0800, "Metformin", 500.0)
        val m250 = doseId(f.KEY_0800, "Metformin", 250.0)
        h.press(posted("Metformin 500"), snoozeLabel()) { snoozes().until.size == 1 }
        h.press(posted("Metformin 250"), snoozeLabel()) { snoozes().until.size == 2 }
        h.waitUntil("the snooze alarm") { h.nextAlarm()?.at == dt(8, 17) && h.notifications().size == 1 }

        // Two entries with one time: neither may be lost when the first rings.
        assertEquals(mapOf(tag(m500, f.KEY_0800) to "${f.END} 08:17", tag(m250, f.KEY_0800) to "${f.END} 08:17"), snoozes().until)
        val alarm = h.nextAlarm()!!
        assertEquals(f.KEY_0800, alarm.key)
        assertTrue(alarm.intent.getBooleanExtra(MedReminders.EXTRA_SNOOZED, false))
        assertFalse(alarm.intent.getBooleanExtra(MedReminders.EXTRA_NUDGE, false))

        clock("08:17")
        h.fire(alarm.intent) { h.notifications().size == 3 }
        h.settle()
        assertEquals(setOf(tag(m500, f.KEY_0800), tag(m250, f.KEY_0800), tag(doseId(f.KEY_0800, "Semaglutide"), f.KEY_0800)), tags())
        assertTrue(snoozes().until.isEmpty())
        // Snoozed doses come back as first reminders, not "still due".
        assertEquals(dueTitle("08:00"), posted("Metformin 500").title)
        assertEquals(dueTitle("08:00"), posted("Metformin 250").title)
    }

    @Test
    fun aSnoozedDoseRingsAloneAndDoesNotBringBackTheOthers() {
        loadMorningOnly()
        ring(f.KEY_0800, 3)
        val m500 = doseId(f.KEY_0800, "Metformin", 500.0)
        // The person swiped the other two away; they are still open, but they have had their reminder.
        swipeAway(posted("Metformin 250"))
        swipeAway(posted("Semaglutide"))
        clock("08:02")
        h.press(posted("Metformin 500"), snoozeLabel()) { snoozes().until.size == 1 }
        h.waitUntil("the snooze alarm") { h.nextAlarm()?.at == dt(8, 17) }

        clock("08:17")
        h.fire(h.nextAlarm()!!.intent) { h.notifications().isNotEmpty() }
        h.settle()
        assertEquals(setOf(tag(m500, f.KEY_0800)), tags())
        assertEquals(3, h.openAt(f.KEY_0800).size)
    }

    @Test
    fun aSnoozeStoredByTheOldVersionStillRingsEveryOpenDoseAtThatTime() {
        loadMorningOnly()
        // As the old version stored it: the due time alone, rung through 08:00.
        h.setDevice { it.copy(medSnoozes = "${f.KEY_0800}=${f.END} 08:15", medRungThrough = "${f.END} 08:00") }
        clock("08:02")
        arm()

        assertEquals("${f.KEY_0800}=${f.END} 08:15", Settings.current().medSnoozes)
        val alarm = h.nextAlarm()!!
        assertEquals(dt(8, 15), alarm.at)
        assertEquals(f.KEY_0800, alarm.key)
        assertTrue(alarm.intent.getBooleanExtra(MedReminders.EXTRA_SNOOZED, false))
        assertTrue(h.notifications().isEmpty())

        clock("08:15")
        h.fire(alarm.intent) { h.notifications().size == 3 }
        h.settle()
        assertEquals(3, tags().size)
        assertNull(Settings.current().medSnoozes)
    }

    // ---- the backstop: an alarm that never came ---------------------------------------------------------------------
    // Alarms ring at their minute (#346). Catching up only covers a phone that was off, or an alarm replaced the moment
    // it was due, never lateness.

    @Test
    fun aRingAlsoNotifiesADoseWhoseAlarmWasNeverDelivered() {
        load()
        setMark("${f.END} 07:59")
        // Vitamin D's own alarm (08:05) never came; the ring at 08:07 for the 08:00 doses also notifies it.
        clock("08:07")
        ring(f.KEY_0800, 4)

        val shown = h.notifications().associate { it.text to it.title }
        assertEquals(4, shown.size)
        // Not "still due": every one is a first reminder, with its own due time.
        assertEquals(dueTitle("08:00"), shown.getValue("Metformin 500 mg"))
        assertEquals(dueTitle("08:00"), shown.getValue("Semaglutide 0.5 mg"))
        assertEquals(dueTitle("08:05"), shown.getValue("Vitamin D 1000 IU"))
        assertEquals("${f.END} 08:07", Settings.current().medRungThrough)
    }

    @Test
    fun anAlarmReplacedTheMomentItWasDueRingsStraightAway() {
        load()
        setMark("${f.END} 07:59")
        // The alarm is set again at 08:03 (a data change), with the 08:00 alarm having been replaced before it rang.
        clock("08:03")
        arm()

        val alarm = h.nextAlarm()!!
        assertEquals(dt(8, 3), alarm.at)
        assertEquals(f.KEY_0800, alarm.key)
        assertTrue(h.notifications().isEmpty())
        h.fire(alarm.intent) { h.notifications().size == 3 }
        h.settle()
        assertEquals(3, h.notifications().size)
        // 08:05 is still to come.
        assertEquals(dt(8, 5), h.nextAlarm()!!.at)
        assertEquals(f.KEY_0805, h.nextAlarm()!!.key)
    }

    @Test
    fun aPhoneOffThroughADoseTimeRingsItWhenItIsBackOn() {
        load()
        // The last ring was last night; the phone was off through 08:00 and 08:05 and starts at 08:30.
        setMark("2026-10-29 21:00")
        clock("08:30")
        h.fire(Intent(h.app, MedBootReceiver::class.java).setAction(Intent.ACTION_BOOT_COMPLETED)) { h.nextAlarm()?.at == dt(8, 30) }
        val alarm = h.nextAlarm()!!
        assertEquals(f.KEY_0800, alarm.key)
        h.fire(alarm.intent) { h.notifications().size == 4 }
        h.settle()
        assertEquals(4, h.notifications().size)
        assertEquals(dueTitle("08:00"), posted("Metformin 500").title)
    }

    @Test
    fun aDoseOverTwelveHoursOldIsNotRungOnlyShownOnMedications() {
        load()
        setMark("2026-10-29 21:00")
        // 20:30: the morning's doses are twelve and a half hours old.
        clock("20:30")
        arm()
        h.settle()

        assertTrue(h.notifications().isEmpty())
        val alarm = h.nextAlarm()!!
        assertEquals(dt(8, 0, "2026-10-31"), alarm.at)
        assertEquals("2026-10-31 08:00", alarm.key)
        assertEquals(4, h.openAt(f.KEY_0800).size + h.openAt(f.KEY_0805).size)
    }

    @Test
    fun withNoMarkYetNothingOldRingsAndTheMarkIsSetToNow() {
        load()
        assertNull(Settings.current().medRungThrough)
        clock("08:30")
        arm()
        h.settle()

        assertEquals("${f.END} 08:30", Settings.current().medRungThrough)
        assertTrue(h.notifications().isEmpty())
        // Only the follow-up for 08:05 (at 08:35) is next: updating the app doesn't produce a burst.
        val alarm = h.nextAlarm()!!
        assertEquals(dt(8, 35), alarm.at)
        assertTrue(alarm.intent.getBooleanExtra(MedReminders.EXTRA_NUDGE, false))
    }

    @Test
    fun aDoseUntickedAfterItsTimeDoesNotNotifyAgain() {
        loadMorningOnly()
        ring(f.KEY_0800, 3)
        // Taken on the Medications screen, then (a mistake) un-ticked ten minutes later.
        h.take(f.KEY_0800)
        clock("08:10")
        arm()
        h.waitUntil("the three cleared") { h.notifications().isEmpty() }
        runBlocking { h.dueAt(f.KEY_0800).forEach { MedSchedules.untake(it) } }
        arm()
        h.settle()

        assertEquals(3, h.openAt(f.KEY_0800).size)
        assertTrue(h.notifications().isEmpty())
        val alarm = h.nextAlarm()!!
        assertEquals(dt(8, 30), alarm.at)
        assertTrue(alarm.intent.getBooleanExtra(MedReminders.EXTRA_NUDGE, false))
    }

    @Test
    fun withTheReminderOffNothingIsOwedWhenItIsSwitchedBackOn() {
        load()
        h.setDevice { it.copy(medReminders = false, medRungThrough = "${f.END} 06:00") }
        clock("08:30")
        arm()
        assertNull(h.nextAlarm())
        assertEquals("${f.END} 08:30", Settings.current().medRungThrough)

        h.setDevice { it.copy(medReminders = true) }
        arm()
        assertTrue(h.notifications().isEmpty())
        assertEquals(dt(8, 35), h.nextAlarm()!!.at)
    }

    @Test
    fun withoutPermissionNothingIsPostedButTheRingStillMovesOn() {
        load()
        h.allowNotifications(false)
        setMark("${f.END} 07:59")
        ring(f.KEY_0800, 0)

        assertTrue(h.notifications().isEmpty())
        // The ring still went through: the mark moved, so these doses aren't rung again in a loop, and the next alarm is 08:05's.
        assertEquals("${f.END} 08:00", Settings.current().medRungThrough)
        assertEquals(dt(8, 5), h.nextAlarm()!!.at)
        assertEquals(f.KEY_0805, h.nextAlarm()!!.key)
        // The doses are still open, for Medications.
        assertEquals(3, h.openAt(f.KEY_0800).size)
    }

    // ---- every alarm rings at its minute (#346) ---------------------------------------------------------------------

    @Test
    fun eachDoseHasAnAlarmClockAtItsOwnMinute() {
        load()
        clock("07:59")
        arm()
        // The platform's own answer: an alarm clock for exactly 08:00.
        assertEquals(millis(dt(8, 0)), h.nextAlarmClock()!!.triggerTime)

        clock("08:00")
        ring(f.KEY_0800, 3)
        h.waitUntil("an alarm clock at 08:05") { h.nextAlarmClock()?.triggerTime == millis(dt(8, 5)) }
        assertEquals(f.KEY_0805, h.nextAlarm()!!.key)
    }

    @Test
    fun aFollowUpAndASnoozeAreAlarmClocksAtTheirExactMinute() {
        loadMorningOnly()
        ring(f.KEY_0800, 3)
        h.waitUntil("the follow-up alarm clock") { h.nextAlarmClock()?.triggerTime == millis(dt(8, 30)) }
        clock("08:02")
        h.press(posted("Metformin 500"), snoozeLabel()) { snoozes().until.size == 1 }
        h.waitUntil("the snooze alarm clock") { h.nextAlarmClock()?.triggerTime == millis(dt(8, 17)) }
    }

    @Test
    fun anAlarmThatCannotLoadItsDataRingsAgainAMinuteLaterWithTheSameExtras() {
        load()
        setMark("${f.END} 07:59")
        MedReminders.loadOverride = { null }
        h.fire(h.medAlarm(f.KEY_0800).putExtra(MedReminders.EXTRA_NUDGE, true)) { h.nextAlarmClock()?.triggerTime == millis(dt(8, 1)) }
        h.settle()
        assertTrue(h.notifications().isEmpty())
        val retry = h.nextAlarm()!!
        assertEquals("MedAlarmReceiver", retry.receiver)
        assertEquals(f.KEY_0800, retry.key)
        assertTrue(retry.intent.getBooleanExtra(MedReminders.EXTRA_NUDGE, false))
        assertFalse(retry.intent.getBooleanExtra(MedReminders.EXTRA_SNOOZED, false))

        // The data is back: the retry carries on, and the chain continues with the next dose.
        MedReminders.loadOverride = null
        clock("08:01")
        h.fire(retry.intent) { h.notifications().size == 3 }
        h.settle()
        assertEquals(List(3) { againTitle("08:00") }, h.notifications().map { it.title })
        assertEquals(millis(dt(8, 5)), h.nextAlarmClock()!!.triggerTime)
    }

    @Test
    fun aBootWithoutDataLeavesAnAlarmToTryAgain() {
        load()
        setMark("2026-10-29 21:00")
        MedReminders.loadOverride = { null }
        h.fire(Intent(h.app, MedBootReceiver::class.java).setAction(Intent.ACTION_BOOT_COMPLETED)) {
            h.alarms().any { it.receiver == "MedAlarmReceiver" && it.at == dt(8, 1) }
        }
        h.settle()
        val retry = h.alarms().first { it.receiver == "MedAlarmReceiver" }
        assertNull(retry.key)

        MedReminders.loadOverride = null
        clock("08:01")
        h.fire(retry.intent) { h.notifications().size == 3 }
        h.settle()
        h.waitUntil("the chain set again") { h.nextAlarmClock()?.triggerTime == millis(dt(8, 5)) }
    }

    @Test
    fun aRingKeepsASnoozeSavedWhileItWorkedAndNeverMovesTheMarkBackwards() {
        loadMorningOnly()
        val m500 = doseId(f.KEY_0800, "Metformin", 500.0)
        val m250 = doseId(f.KEY_0800, "Metformin", 250.0)
        h.setDevice { it.copy(medSnoozes = MedReminders.Snoozes().with(m500, f.KEY_0800, dt(8, 15)).encode(), medRungThrough = "${f.END} 08:00") }
        clock("08:16")
        // Another receiver snoozes Metformin 250 and a ring moves the mark on, between this ring's read and its write.
        MedReminders.raceHook = {
            MedReminders.raceHook = null
            Settings.updateDeviceNow {
                it.copy(
                    medSnoozes = MedReminders.Snoozes.decode(it.medSnoozes).with(m250, f.KEY_0800, dt(8, 31)).encode(),
                    medRungThrough = "${f.END} 08:30"
                )
            }
        }
        h.fire(h.medAlarm(f.KEY_0800).putExtra(MedReminders.EXTRA_SNOOZED, true)) { h.notifications().size == 1 }
        h.settle()

        // Metformin 500's snooze rang and went; the one saved meanwhile is still there; the mark did not go back to 08:16.
        assertEquals(setOf(tag(m250, f.KEY_0800)), snoozes().until.keys)
        assertEquals("${f.END} 08:30", Settings.current().medRungThrough)
    }

    @Test
    fun anArmKeepsASnoozeSavedWhileItWorkedAndNeverMovesTheMarkBackwards() {
        loadMorningOnly()
        val m250 = doseId(f.KEY_0800, "Metformin", 250.0)
        // An old snooze (days ago) is pruned by this arm.
        h.setDevice { it.copy(medSnoozes = "2026-10-26 08:00=2026-10-26 08:15", medRungThrough = "${f.END} 08:00") }
        clock("08:20")
        MedReminders.raceHook = {
            MedReminders.raceHook = null
            Settings.updateDeviceNow {
                it.copy(
                    medSnoozes = MedReminders.Snoozes.decode(it.medSnoozes).with(m250, f.KEY_0800, dt(8, 35)).encode(),
                    medRungThrough = "${f.END} 08:30"
                )
            }
        }
        arm()

        assertEquals(setOf(tag(m250, f.KEY_0800)), snoozes().until.keys)
        assertEquals("${f.END} 08:30", Settings.current().medRungThrough)
    }

    // ---- two reminders due in one minute both ring ------------------------------------------------------------------

    private val pairRuns = listOf(
        MedRun(
            MedSchedule(1, "Pair", phases = listOf(MedPhase(1, true, 1, PhaseUnit.ONGOING, listOf(
                MedDose(1, "Alpha", 1.0, "mg", "08:00"),
                MedDose(2, "Bravo", 2.0, "mg", "08:30")
            )))),
            "2026-10-01"
        )
    )
    private val sameTimeRuns = listOf(
        MedRun(
            MedSchedule(1, "Same", phases = listOf(MedPhase(1, true, 1, PhaseUnit.ONGOING, listOf(
                MedDose(1, "Alpha", 1.0, "mg", "08:00"),
                MedDose(2, "Bravo", 2.0, "mg", "08:00")
            )))),
            "2026-10-01"
        )
    )

    @Test
    fun aFollowUpAndAFirstReminderDueInTheSameMinuteBothRing() {
        h.loadPlan(pairRuns)
        ring("${f.END} 08:00", 1)
        // 08:30: Bravo is due for the first time and Alpha's follow-up (30 minutes) is due.
        clock("08:30")
        h.fire(h.medAlarm("${f.END} 08:30")) { h.notifications().size == 2 && posted("Alpha").title == againTitle("08:00") }
        h.settle()
        assertEquals(dueTitle("08:30"), posted("Bravo").title)
    }

    @Test
    fun whicheverOfTheTwoAlarmsIsSetBringsBothReminders() {
        h.loadPlan(pairRuns)
        ring("${f.END} 08:00", 1)
        h.waitUntil("the 08:30 alarm clock") { h.nextAlarmClock()?.triggerTime == millis(dt(8, 30)) }
        clock("08:30")
        h.fire(h.nextAlarm()!!.intent) { h.notifications().size == 2 && posted("Alpha").title == againTitle("08:00") }
        h.settle()
        assertEquals(dueTitle("08:30"), posted("Bravo").title)
    }

    @Test
    fun aSnoozeComingBackInTheSameMinuteAsAFollowUpBringsBoth() {
        h.loadPlan(sameTimeRuns)
        h.setDevice { it.copy(medNudgeMinutes = 15) }
        ring("${f.END} 08:00", 2)
        // Bravo is snoozed for 15 minutes, which is also when Alpha's follow-up is due.
        h.press(posted("Bravo"), snoozeLabel()) { snoozes().until.size == 1 }
        h.waitUntil("the 08:15 alarm clock") { h.nextAlarmClock()?.triggerTime == millis(dt(8, 15)) }
        clock("08:15")
        h.fire(h.nextAlarm()!!.intent) { h.notifications().size == 2 && posted("Alpha").title == againTitle("08:00") }
        h.settle()
        assertEquals(dueTitle("08:00"), posted("Bravo").title)
    }

    // ---- first reminder or follow-up --------------------------------------------------------------------------

    @Test
    fun aRealFollowUpSaysStillDueAndOnlyForDosesStillOpen() {
        loadMorningOnly()
        ring(f.KEY_0800, 3)
        h.waitUntil("the follow-up alarm") { h.nextAlarm()?.at == dt(8, 30) }
        val nudge = h.nextAlarm()!!
        assertTrue(nudge.intent.getBooleanExtra(MedReminders.EXTRA_NUDGE, false))
        assertEquals(f.KEY_0800, nudge.key)
        assertEquals(dueTitle("08:00"), posted("Metformin 500").title)

        // Metformin 250 is ticked on the Medications screen before the follow-up rings.
        val m250 = h.dueAt(f.KEY_0800).first { it.amount == 250.0 }
        runBlocking { MedSchedules.take(m250, "08:10:00") }
        clock("08:30")
        h.fire(nudge.intent) { h.notifications().size == 2 && h.notifications().all { it.title == againTitle("08:00") } }
        h.settle()
        assertEquals(listOf("Metformin 500 mg", "Semaglutide 0.5 mg"), h.notifications().map { it.text }.sorted())
    }

    // ---- injection sites -------------------------------------------------------------------------------------

    @Test
    fun anInjectedDoseNamesItsSiteOnItsOwnButtonAndRecordsExactlyThat() {
        load()
        ring(f.KEY_0800, 3)
        clock("08:03")
        val upperLeft = siteName(h.app.resources, InjectionSite.ABDOMEN_UPPER_LEFT)
        val sema = posted("Semaglutide")
        assertEquals(listOf(str(R.string.mr_taken_site, upperLeft), snoozeLabel(), str(R.string.md_skip)), sema.actions)
        // A medication that isn't injected has a plain Taken button.
        assertEquals(str(R.string.md_take), posted("Metformin 500").actions.first())

        val id = doseId(f.KEY_0800, "Semaglutide")
        h.press(sema, sema.actions.first()) { recordsOf(id).isNotEmpty() }
        assertEquals(InjectionSite.ABDOMEN_UPPER_LEFT.key, recordsOf(id).single().site)
    }

    private val creatineRuns = listOf(
        MedRun(
            MedSchedule(1, "Creatine", phases = listOf(MedPhase(1, true, 1, PhaseUnit.ONGOING, listOf(
                MedDose(1, "Creatine", 5.0, "mg", "09:00"),
                MedDose(2, "Creatine", 10.0, "mg", "09:00")
            )))),
            "2026-10-01"
        )
    )
    private val key0900 = "${f.END} 09:00"

    private fun twoInjectedDosesTakenInOrder(first: String, second: String) {
        h.loadPlan(creatineRuns, listOf(MedInfo("Creatine", injected = true)))
        clock("09:00")
        ring(key0900, 2)
        val left = siteName(h.app.resources, InjectionSite.ABDOMEN_UPPER_LEFT)
        val right = siteName(h.app.resources, InjectionSite.ABDOMEN_UPPER_RIGHT)
        // Each button names its own site: the two doses of one medication do not share one.
        assertEquals(str(R.string.mr_taken_site, left), posted("Creatine 5 mg").actions.first())
        assertEquals(str(R.string.mr_taken_site, right), posted("Creatine 10 mg").actions.first())

        for (text in listOf(first, second)) {
            val before = h.snapshot().medicationView.records.size
            val p = posted(text)
            h.press(p, p.actions.first()) { h.snapshot().medicationView.records.size == before + 1 }
        }
        assertEquals(InjectionSite.ABDOMEN_UPPER_LEFT.key, recordsOf(1L).single().site)
        assertEquals(InjectionSite.ABDOMEN_UPPER_RIGHT.key, recordsOf(2L).single().site)
    }

    @Test
    fun twoDosesOfOneInjectedMedicationRecordTheirOwnSites() = twoInjectedDosesTakenInOrder("Creatine 5 mg", "Creatine 10 mg")

    @Test
    fun twoDosesOfOneInjectedMedicationRecordTheirOwnSitesTakenInTheOtherOrder() = twoInjectedDosesTakenInOrder("Creatine 10 mg", "Creatine 5 mg")

    @Test
    fun theTakenButtonRecordsTheSiteItSaidEvenIfTheSuggestionMovedOn() {
        h.loadPlan(creatineRuns, listOf(MedInfo("Creatine", injected = true)))
        clock("09:00")
        ring(key0900, 2)
        val left = siteName(h.app.resources, InjectionSite.ABDOMEN_UPPER_LEFT)
        val five = posted("Creatine 5 mg")
        assertEquals(str(R.string.mr_taken_site, left), five.actions.first())

        // Meanwhile yesterday's dose is logged with that very site, so a fresh suggestion would now be the next one.
        val yesterday = MedSchedules.dueOn(h.snapshot().medPlan, "2026-10-29").first { it.dose.id == 1L }
        runBlocking { MedSchedules.take(yesterday, "09:00:00", site = InjectionSite.ABDOMEN_UPPER_LEFT) }
        assertEquals(
            InjectionSite.ABDOMEN_UPPER_RIGHT,
            MedReminders.sitesFor(h.snapshot().medPlan, h.snapshot().medicationView.records, listOf(h.openAt(key0900).first { it.dose.id == 1L })).single()
        )
        h.press(five, five.actions.first()) { recordsOf(1L).any { it.due == key0900 } }
        assertEquals(InjectionSite.ABDOMEN_UPPER_LEFT.key, recordsOf(1L).first { it.due == key0900 }.site)
    }

    @Test
    fun aTakenButtonWithoutAReadableSiteWorksOutTheSuggestedOne() {
        h.loadPlan(creatineRuns, listOf(MedInfo("Creatine", injected = true)))
        clock("09:00")
        ring(key0900, 2)
        for ((id, extra) in listOf(1L to null, 2L to "not-a-site")) {
            val intent = h.medAlarm(key0900, MedReminders.ACTION_TAKEN).putExtra(MedReminders.EXTRA_DOSE, id)
            if (extra != null) intent.putExtra(MedReminders.EXTRA_SITE, extra)
            h.fire(intent) { recordsOf(id).isNotEmpty() }
        }
        h.settle()
        // Worked out afresh, one dose after the other: the first site, then the next.
        assertEquals(InjectionSite.ABDOMEN_UPPER_LEFT.key, recordsOf(1L).single().site)
        assertEquals(InjectionSite.ABDOMEN_UPPER_RIGHT.key, recordsOf(2L).single().site)
    }

    // ---- Lock Medications and lock-screen privacy ---------------------------------------------------------------

    @Test
    fun lockedMedicationsNameNothingInAnyOfTheThreeNotifications() {
        load()
        h.setDevice { it.copy(medLock = true, medNamesOnLockScreen = true) }
        ring(f.KEY_0800, 3)
        clock("08:03")

        val shown = h.notifications()
        assertEquals(3, shown.size)
        val names = listOf("Metformin", "Semaglutide", "mg", "Abdomen")
        shown.forEach { p ->
            assertEquals(dueTitle("08:00"), p.title)
            assertEquals(str(R.string.ml_notification_text), p.text)
            assertEquals(str(R.string.ml_notification_text), p.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString())
            // No site on a button, and nothing else that names a dose.
            assertEquals(listOf(str(R.string.md_take), snoozeLabel(), str(R.string.md_skip)), p.actions)
            assertTrue(names.none { it in p.title + p.text + p.actions.joinToString() })
            // Not even the order key Android keeps with the notification names a medication.
            assertTrue("sort key ${p.notification.sortKey}", names.none { p.notification.sortKey.orEmpty().contains(it, ignoreCase = true) })
            // Locked beats "names on the lock screen".
            assertEquals(Notification.VISIBILITY_PRIVATE, p.notification.visibility)
        }
        // The dose still goes to its suggested site when taken from the notification.
        val sema = doseId(f.KEY_0800, "Semaglutide")
        val button = h.notifications().first { it.tag == tag(sema, f.KEY_0800) }
        h.press(button, str(R.string.md_take)) { recordsOf(sema).isNotEmpty() }
        assertEquals(InjectionSite.ABDOMEN_UPPER_LEFT.key, recordsOf(sema).single().site)
    }

    @Test
    fun namesOnTheLockScreenSetEachNotificationsVisibility() {
        load()
        h.setDevice { it.copy(medNamesOnLockScreen = true) }
        ring(f.KEY_0800, 3)
        assertEquals(List(3) { Notification.VISIBILITY_PUBLIC }, h.notifications().map { it.notification.visibility })
        h.notifications().forEach {
            // What a locked screen shows instead when the names are private.
            assertEquals(str(R.string.mr_public_title), it.notification.publicVersion?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        }
        h.setDevice { it.copy(medNamesOnLockScreen = false) }
        h.fire(h.medAlarm(f.KEY_0800)) { h.notifications().size == 3 && h.notifications().all { it.notification.visibility == Notification.VISIBILITY_PRIVATE } }
        assertEquals(List(3) { Notification.VISIBILITY_PRIVATE }, h.notifications().map { it.notification.visibility })
    }

    // ---- identity ----------------------------------------------------------------------------------------------

    @Test
    fun everyNotificationAndButtonIsDistinctAcrossDosesAndDays() {
        load()
        setMark("${f.END} 07:59")
        clock("08:07")
        ring(f.KEY_0800, 4)
        // The next morning: the same doses again on another day, while yesterday's are still unanswered.
        val nextDay = "2026-10-31"
        clock("08:00", nextDay)
        ring("$nextDay 08:00", 6)

        val shown = h.notifications()
        assertEquals(6, shown.map { it.tag }.distinct().size)
        val metformin = doseId(f.KEY_0800, "Metformin", 500.0)
        assertTrue(tags().containsAll(listOf(tag(metformin, f.KEY_0800), tag(metformin, "$nextDay 08:00"))))
        val intents = shown.flatMap { p ->
            listOf(shadowOf(p.notification.contentIntent).savedIntent) + p.notification.actions.map { shadowOf(it.actionIntent).savedIntent }
        }
        assertEquals(24, intents.size)
        for (i in intents.indices) for (j in i + 1 until intents.size) {
            assertFalse("Intent $i and $j are the same PendingIntent: ${intents[i]}", intents[i].filterEquals(intents[j]))
        }
    }

    // ---- clearing -------------------------------------------------------------------------------------------

    @Test
    fun aDoseTickedOnTheMedicationsPathRemovesOnlyItsNotification() {
        loadMorningOnly()
        ring(f.KEY_0800, 3)
        val m500 = h.dueAt(f.KEY_0800).first { it.amount == 500.0 }
        runBlocking { MedSchedules.take(m500, "08:10:00") }
        clock("08:10")
        arm()
        h.waitUntil("only its notification gone") { h.notifications().size == 2 }
        h.settle()
        assertEquals(
            setOf(tag(doseId(f.KEY_0800, "Metformin", 250.0), f.KEY_0800), tag(doseId(f.KEY_0800, "Semaglutide"), f.KEY_0800)),
            tags()
        )
    }

    @Test
    fun anAlarmForDosesAllTakenMeanwhileClearsTheirNotifications() {
        loadMorningOnly()
        ring(f.KEY_0800, 3)
        h.take(f.KEY_0800)
        h.fire(h.medAlarm(f.KEY_0800)) { h.notifications().isEmpty() && h.nextAlarm() != null }
        h.settle()
        assertTrue(h.notifications().isEmpty())
    }

    private fun postOldGroupedNotification(): Int {
        val nm = h.app.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(MedReminders.CHANNEL, "Medication reminders", NotificationManager.IMPORTANCE_HIGH))
        // How the previous version posted it: one notification for every dose at the time, with no dose and no tag.
        val id = 7000 + (f.KEY_0800.hashCode() and 0xFFFF)
        nm.notify(
            id,
            NotificationCompat.Builder(h.app, MedReminders.CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_medication)
                .setContentTitle("Medication due")
                .setContentText("Metformin 500 mg, Metformin 250 mg, Semaglutide 0.5 mg")
                .addExtras(Bundle().apply { putString(MedReminders.EXTRA_AT, f.KEY_0800) })
                .build()
        )
        return id
    }

    @Test
    fun anOldVersionsGroupedNotificationIsReplacedByOnePerDose() {
        loadMorningOnly()
        val old = postOldGroupedNotification()
        assertEquals(listOf(old), h.notifications().map { it.id })
        arm()
        h.waitUntil("one per dose") { h.notifications().size == 3 && h.notifications().all { it.tag != null } }
        h.settle()
        assertEquals(3, h.notifications().size)
        assertTrue(h.notifications().none { it.tag == null })
        assertEquals(listOf("Metformin 250 mg", "Metformin 500 mg", "Semaglutide 0.5 mg"), h.notifications().map { it.text }.sorted())
    }

    @Test
    fun aButtonOnAnOldVersionsNotificationActsOnEveryDoseAtThatTimeAsBefore() {
        loadMorningOnly()
        postOldGroupedNotification()
        clock("08:03")
        // The old buttons carried the due time and no dose.
        h.fire(h.medAlarm(f.KEY_0800, MedReminders.ACTION_TAKEN)) { h.openAt(f.KEY_0800).isEmpty() && h.notifications().isEmpty() }
        h.settle()
        val records = h.snapshot().medicationView.records.filter { it.due == f.KEY_0800 }
        assertEquals(3, records.size)
        assertEquals(setOf("08:03:00"), records.map { it.time }.toSet())
    }

    // ---- the logging reminders share the clock seam (#345) ---------------------------------------------------------

    // ---- nothing already past rings when the plan changes (audit round 1, item 1) ---------------------------------------

    private val dayRuns = listOf(
        MedRun(
            MedSchedule(1, "Day", phases = listOf(MedPhase(1, true, 1, PhaseUnit.ONGOING, listOf(
                MedDose(1, "Alpha", 1.0, "mg", "09:00"),
                MedDose(2, "Bravo", 2.0, "mg", "13:00"),
                MedDose(3, "Charlie", 3.0, "mg", "19:00")
            )))),
            "2026-10-01"
        )
    )
    private val eveningRuns = listOf(
        MedRun(
            MedSchedule(1, "Day", phases = listOf(MedPhase(1, true, 1, PhaseUnit.ONGOING, listOf(
                MedDose(1, "Alpha", 1.0, "mg", "09:00"),
                MedDose(2, "Bravo", 2.0, "mg", "13:00"),
                MedDose(3, "Charlie", 3.0, "mg", "21:00")
            )))),
            "2026-10-01"
        )
    )

    /** At 20:00 nothing is posted, the next alarm is tomorrow's first dose, and the mark is now. */
    private fun assertNothingOwedAtTwenty() {
        arm()
        h.settle()
        assertTrue(h.notifications().isEmpty())
        assertEquals(0, MedReminders.posted)
        assertEquals(millis(dt(9, 0, "2026-10-31")), h.nextAlarmClock()!!.triggerTime)
        assertEquals("${f.END} 20:00", Settings.current().medRungThrough)
        assertEquals(stampNow(), Settings.current().medPlanStamp)
    }

    @Test
    fun startingAScheduleTodayDoesNotRingTheDosesAlreadyPast() {
        clock("20:00")
        h.setDevice { it.copy(medRungThrough = "${f.END} 07:00", medPlanStamp = "before-the-schedule") }
        h.loadPlan(dayRuns)
        assertNothingOwedAtTwenty()
    }

    @Test
    fun aRingAfterThePlanChangedOwesNothingAlreadyPast() {
        clock("20:00")
        h.loadPlan(dayRuns)
        h.setDevice { it.copy(medRungThrough = "${f.END} 07:00", medPlanStamp = "old") }
        h.fire(h.medAlarm(null)) { Settings.current().medPlanStamp == stampNow() && h.nextAlarm() != null }
        h.settle()
        assertTrue(h.notifications().isEmpty())
        assertEquals(0, MedReminders.posted)
    }

    @Test
    fun movingADoseEarlierDoesNotRingItAtOnce() {
        h.loadPlan(eveningRuns)
        clock("20:00")
        // Alpha and Bravo rang by 13:00; Charlie is due at 21:00, then moved to 14:00, which is already past.
        setMark("${f.END} 13:00")
        val s = h.snapshot().medPlan.schedules.first()
        runBlocking {
            MedSchedules.save(s.copy(phases = s.phases.map { p -> p.copy(doses = p.doses.map { if (it.medication == "Charlie") it.copy(time = "14:00") else it }) }))
        }
        assertNothingOwedAtTwenty()
    }

    @Test
    fun resumingAPauseDoesNotRingTheDosesOfTodayAlreadyPast() {
        h.loadPlan(dayRuns)
        runBlocking { MedSchedules.pause(h.snapshot().medPlan.activations.first(), "2026-10-29") }
        clock("20:00")
        setMark("${f.END} 07:59")
        runBlocking { MedSchedules.resume(h.snapshot().medPlan.activations.first(), f.END) }
        assertNothingOwedAtTwenty()
    }

    @Test
    fun undoingAnEndDoesNotRingTheDosesOfTodayAlreadyPast() {
        h.loadPlan(dayRuns)
        val run = h.snapshot().medPlan.activations.first()
        runBlocking { MedSchedules.end(run, f.END) }
        clock("20:00")
        setMark("${f.END} 07:59")
        runBlocking { MedSchedules.restore(run) }
        assertNothingOwedAtTwenty()
    }

    @Test
    fun aRestoredRunDoesNotRingTheDosesOfTodayAlreadyPast() {
        h.loadPlan(dayRuns)
        val run = h.snapshot().medPlan.activations.first()
        runBlocking { MedSchedules.deleteActivation(run) }
        clock("20:00")
        setMark("${f.END} 07:59")
        runBlocking { MedSchedules.restoreRun(run, emptyList()) }
        assertNothingOwedAtTwenty()
    }

    // ---- a failed write, a failed ring ------------------------------------------------------------------------------

    @Test
    fun aFailedSettingsWriteDoesNotEndTheChainOrRepeatTheAlerts() {
        load()
        setMark("${f.END} 07:59")
        Settings.deviceWriteFault = java.io.IOException("storage full")
        clock("08:07")
        ring(f.KEY_0800, 4)
        // The notifications posted and the next alarm is set, though nothing could be saved.
        assertEquals(4, MedReminders.posted)
        h.waitUntil("an alarm clock") { h.nextAlarmClock() != null }
        // The mark moved in memory, so another ring owes nothing: the same doses are not told again.
        h.fire(h.medAlarm(null)) { MedReminders.posted >= 4 }
        h.settle()
        assertEquals(4, MedReminders.posted)
        assertNotNull(h.nextAlarmClock())
    }

    @Test
    fun anExceptionInARingSetsARetryInsteadOfClosingTheApp() {
        load()
        MedReminders.raceHook = {
            MedReminders.raceHook = null
            throw IllegalStateException("boom")
        }
        h.fire(h.medAlarm(f.KEY_0800)) { h.alarms().any { it.receiver == "MedAlarmReceiver" && it.at == dt(8, 1) } }
        val retry = h.nextAlarm()!!
        assertEquals(f.KEY_0800, retry.key)
        assertEquals(1, retry.intent.getIntExtra(MedReminders.EXTRA_ATTEMPT, 0))
    }

    // ---- clocks going back ------------------------------------------------------------------------------------------

    private val overlapRuns = listOf(
        MedRun(
            MedSchedule(1, "Night", phases = listOf(MedPhase(1, true, 1, PhaseUnit.ONGOING, listOf(MedDose(1, "Night", 1.0, "mg", "01:30"))))),
            "2026-10-01"
        )
    )

    @Test
    fun anAlarmForATimeThatHappensTwiceIsSetForTheOneStillToCome() {
        val zone = ZoneId.of("America/New_York")
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        try {
            // 2026-11-01: clocks go back from 02:00 to 01:00, so 01:30 happens twice. It is the second 01:10 (06:10 UTC).
            val clock = Clock.fixed(Instant.parse("2026-11-01T06:10:00Z"), zone)
            Dates.clockSource = clock
            h.loadPlan(overlapRuns)
            arm()
            // 01:30 EST is 06:30 UTC; the 01:30 EDT of the first pass (05:30 UTC) has gone.
            val expected = Instant.parse("2026-11-01T06:30:00Z").toEpochMilli()
            assertEquals(expected, h.nextAlarmClock()!!.triggerTime)
            assertTrue(h.nextAlarmClock()!!.triggerTime > clock.millis())

            // A logging reminder at the same time is treated the same way.
            h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.WORKOUT, true, "01:30", (1..7).toSet())))
            runBlocking { LogReminders.arm(h.app) }
            val log = h.alarms().first { it.receiver == "LogAlarmReceiver" }
            assertEquals(expected, log.triggerAtMillis)
        } finally {
            TimeZone.setDefault(original)
        }
    }

    // ---- locked: the order key names nothing (see the locked test above) -------------------------------------------

    // ---- reminders off ----------------------------------------------------------------------------------------------

    @Test
    fun aSnoozeLeftOverWhileRemindersWereOffDoesNotRingWhenTheyAreBackOn() {
        loadMorningOnly()
        val m500 = doseId(f.KEY_0800, "Metformin", 500.0)
        val stamp = stampNow()
        h.setDevice {
            it.copy(
                medSnoozes = MedReminders.Snoozes().with(m500, f.KEY_0800, dt(8, 15)).encode(),
                medRungThrough = "${f.END} 08:00", medPlanStamp = stamp, medReminders = false
            )
        }
        clock("08:20")
        arm()
        // The snooze's time passed while they were off: it is gone, not waiting to ring.
        assertTrue(snoozes().until.isEmpty())
        h.setDevice { it.copy(medReminders = true) }
        arm()
        h.settle()
        assertTrue(h.notifications().isEmpty())
        assertEquals(0, MedReminders.posted)
        assertEquals(dt(8, 30), h.nextAlarm()!!.at)
    }

    // ---- injection sites: two doses of one medication never name the same one -------------------------------------

    private fun twoInjectedDosesWithOneSnoozed(firstThenSecond: Boolean) {
        h.loadPlan(creatineRuns, listOf(MedInfo("Creatine", injected = true)))
        clock("09:00")
        ring(key0900, 2)
        val left = siteName(h.app.resources, InjectionSite.ABDOMEN_UPPER_LEFT)
        val right = siteName(h.app.resources, InjectionSite.ABDOMEN_UPPER_RIGHT)
        val five = posted("Creatine 5 mg")
        assertEquals(str(R.string.mr_taken_site, left), five.actions.first())
        assertEquals(str(R.string.mr_taken_site, right), posted("Creatine 10 mg").actions.first())

        // The second dose is snoozed, with the first one's notification still showing.
        h.press(posted("Creatine 10 mg"), snoozeLabel()) { snoozes().until.size == 1 }
        h.waitUntil("the snooze alarm clock") { h.nextAlarmClock()?.triggerTime == millis(dt(9, 15)) }
        clock("09:15")
        h.fire(h.nextAlarm()!!.intent) { h.notifications().size == 2 }
        h.settle()
        // It comes back alone and names the site it had, not the one the first dose's button still names.
        assertEquals(str(R.string.mr_taken_site, right), posted("Creatine 10 mg").actions.first())
        assertEquals(five.actions.first(), posted("Creatine 5 mg").actions.first())

        for (text in if (firstThenSecond) listOf("Creatine 5 mg", "Creatine 10 mg") else listOf("Creatine 10 mg", "Creatine 5 mg")) {
            val before = h.snapshot().medicationView.records.size
            val p = posted(text)
            h.press(p, p.actions.first()) { h.snapshot().medicationView.records.size == before + 1 }
        }
        assertEquals(InjectionSite.ABDOMEN_UPPER_LEFT.key, recordsOf(1L).single().site)
        assertEquals(InjectionSite.ABDOMEN_UPPER_RIGHT.key, recordsOf(2L).single().site)
    }

    @Test
    fun aSnoozedInjectedDoseReturnsWithADifferentSiteAndBothRecordTheirOwn() = twoInjectedDosesWithOneSnoozed(true)

    @Test
    fun aSnoozedInjectedDoseReturnsWithADifferentSiteTakenInTheOtherOrder() = twoInjectedDosesWithOneSnoozed(false)

    private val creatineOnce = listOf(
        MedRun(
            MedSchedule(1, "Creatine", phases = listOf(MedPhase(1, true, 1, PhaseUnit.ONGOING, listOf(MedDose(1, "Creatine", 5.0, "mg", "09:00"))))),
            "2026-10-01"
        )
    )
    private val thursday = "2026-10-29 09:00"

    private fun thursdayLeftUnanswered() {
        h.loadPlan(creatineOnce, listOf(MedInfo("Creatine", injected = true)))
        clock("09:00", "2026-10-29")
        ring(thursday, 1)
        clock("09:00")
    }

    private fun thursdayAndFriday(thursdayFirst: Boolean) {
        thursdayLeftUnanswered()
        ring(key0900, 2)
        val left = siteName(h.app.resources, InjectionSite.ABDOMEN_UPPER_LEFT)
        val right = siteName(h.app.resources, InjectionSite.ABDOMEN_UPPER_RIGHT)
        val thu = h.notifications().first { it.notification.extras.getString(MedReminders.EXTRA_AT) == thursday }
        val fri = h.notifications().first { it.notification.extras.getString(MedReminders.EXTRA_AT) == key0900 }
        // Yesterday's unanswered dose and today's do not both say the same site.
        assertEquals(str(R.string.mr_taken_site, left), thu.actions.first())
        assertEquals(str(R.string.mr_taken_site, right), fri.actions.first())
        for (p in if (thursdayFirst) listOf(thu, fri) else listOf(fri, thu)) {
            val before = h.snapshot().medicationView.records.size
            h.press(p, p.actions.first()) { h.snapshot().medicationView.records.size == before + 1 }
        }
        assertEquals(InjectionSite.ABDOMEN_UPPER_LEFT.key, recordsOf(1L).first { it.due == thursday }.site)
        assertEquals(InjectionSite.ABDOMEN_UPPER_RIGHT.key, recordsOf(1L).first { it.due == key0900 }.site)
    }

    @Test
    fun yesterdaysUnansweredDoseAndTodaysNameDifferentSites() = thursdayAndFriday(true)

    @Test
    fun yesterdaysUnansweredDoseAndTodaysNameDifferentSitesTakenTodaysFirst() = thursdayAndFriday(false)

    @Test
    fun theWidgetsTakenButtonDoesNotRepeatYesterdaysUnansweredDosesSite() {
        thursdayLeftUnanswered()
        val intent = Intent(h.app, TodayWidgetActions::class.java).setAction(TodayWidgets.ACTION_TAKE)
            .putExtra("com.fitlens.companion.WIDGET_AT", key0900)
            .putExtra("com.fitlens.companion.WIDGET_DOSE", 1L)
        h.fire(intent) { recordsOf(1L).any { it.due == key0900 } }
        assertEquals(InjectionSite.ABDOMEN_UPPER_RIGHT.key, recordsOf(1L).first { it.due == key0900 }.site)
    }

    // ---- unanswered reminders expire --------------------------------------------------------------------------------

    @Test
    fun anUnansweredReminderGoesOnceItsDoseIsOlderThanYesterdayAndTitlesNameTheDay() {
        load()
        clock("08:00", "2026-10-28")
        ring("2026-10-28 08:00", 2)
        // The title names the day and the time, in the one layout.
        val first = str(R.string.mr_title, "Wed 28 Oct, ${Dates.time(h.app, "08:00")}")
        assertEquals(List(2) { first }, h.notifications().map { it.title })
        clock("08:00", "2026-10-29")
        ring("2026-10-29 08:00", 4)
        // Two days later: Wednesday's are gone (the doses stay on Medications), Thursday's stay, Friday's are new.
        clock("08:00")
        ring(f.KEY_0800, 5)
        assertTrue(h.notifications().none { it.notification.extras.getString(MedReminders.EXTRA_AT) == "2026-10-28 08:00" })
        assertEquals(2, h.notifications().count { it.notification.extras.getString(MedReminders.EXTRA_AT) == "2026-10-29 08:00" })
        assertEquals(3, h.notifications().count { it.title == dueTitle("08:00") })
        assertTrue(dueTitle("08:00").contains("Fri 30 Oct"))
        assertEquals(2, h.openAt("2026-10-28 08:00").size)
    }

    // ---- retries back off, and both chains are always left an alarm --------------------------------------------------

    @Test
    fun theRetryGapGrowsAndAnAlarmIsAlwaysSet() {
        load()
        MedReminders.loadOverride = { null }
        var at = dt(8, 0)
        var intent = h.medAlarm(f.KEY_0800)
        for (gap in listOf(1L, 2L, 4L, 8L, 15L, 15L)) {
            val expected = at.plusMinutes(gap)
            h.fire(intent) { h.nextAlarm()?.at == expected }
            assertEquals(1, h.alarms().size)
            at = expected
            clock("%02d:%02d".format(at.hour, at.minute))
            intent = h.nextAlarm()!!.intent
        }
        assertEquals(6, intent.getIntExtra(MedReminders.EXTRA_ATTEMPT, 0))
    }

    @Test
    fun anAlarmThatOverrunsItsTimeSetsARetry() {
        load()
        MedReminders.workTimeoutMs = 300
        MedReminders.loadOverride = { awaitCancellation() }
        h.fire(h.medAlarm(f.KEY_0800).putExtra(MedReminders.EXTRA_NUDGE, true)) { h.nextAlarm()?.at == dt(8, 1) }
        val retry = h.nextAlarm()!!
        assertEquals(f.KEY_0800, retry.key)
        assertTrue(retry.intent.getBooleanExtra(MedReminders.EXTRA_NUDGE, false))
        assertEquals(1, retry.intent.getIntExtra(MedReminders.EXTRA_ATTEMPT, 0))
    }

    @Test
    fun aBootThatOverrunsItsTimeLeavesBothChainsAnAlarm() {
        load()
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(5))))
        MedReminders.workTimeoutMs = 300
        MedReminders.loadOverride = { awaitCancellation() }
        h.fire(Intent(h.app, MedBootReceiver::class.java).setAction(Intent.ACTION_BOOT_COMPLETED)) {
            h.alarms().any { it.receiver == "MedAlarmReceiver" && it.at == dt(8, 1) } &&
                h.alarms().any { it.receiver == "LogAlarmReceiver" && it.at == dt(8, 1) }
        }
    }

    @Test
    fun aBootWithoutDataSetsBothChainsToTryAgain() {
        load()
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(5))))
        MedReminders.loadOverride = { null }
        h.fire(Intent(h.app, MedBootReceiver::class.java).setAction(Intent.ACTION_BOOT_COMPLETED)) {
            h.alarms().any { it.receiver == "MedAlarmReceiver" && it.at == dt(8, 1) } &&
                h.alarms().any { it.receiver == "LogAlarmReceiver" && it.at == dt(8, 1) }
        }
    }

    // ---- the clock or zone changed ----------------------------------------------------------------------------------

    @Test
    fun aMarkAheadOfTheClockIsResetWhenTheClockOrZoneChangesButNotOnAReboot() {
        load()
        for (action in listOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            h.setDevice { it.copy(medRungThrough = "2026-10-31 20:00") }
            h.fire(Intent(h.app, MedBootReceiver::class.java).setAction(action)) { Settings.current().medRungThrough == "${f.END} 08:00" }
        }
        // A reboot or an update leaves a mark alone: only a change of the clock or the zone resets one that is ahead.
        h.setDevice { it.copy(medRungThrough = "2026-10-31 20:00") }
        h.settle()
        h.cancelAllAlarms()
        h.fire(Intent(h.app, MedBootReceiver::class.java).setAction(Intent.ACTION_BOOT_COMPLETED)) { h.nextAlarm() != null }
        h.settle()
        assertEquals("2026-10-31 20:00", Settings.current().medRungThrough)
    }

    // ---- one ring at a time -------------------------------------------------------------------------------------------

    @Test
    fun aColdStartsOwnArmDoesNotRingTheSameDosesAgain() {
        load()
        setMark("${f.END} 07:59")
        clock("08:07")
        // The application's start-up asks for an arm 300 ms later, while the alarm that started the process is still ringing.
        MedReminders.init(h.app)
        var alarmsWhileRinging = -1
        MedReminders.raceHook = {
            MedReminders.raceHook = null
            delay(700)
            alarmsWhileRinging = h.alarms().size
        }
        h.fire(h.medAlarm(f.KEY_0800)) { h.notifications().size == 4 && h.nextAlarm() != null }
        h.settle()
        assertEquals(4, MedReminders.posted)
        // No stale alarm was set over the ring while it worked.
        assertEquals(0, alarmsWhileRinging)
    }

    // ---- a button's write fails ---------------------------------------------------------------------------------------

    @Test
    fun aButtonWhoseWriteFailsLeavesTheReminderToPressAgain() {
        loadMorningOnly()
        ring(f.KEY_0800, 3)
        val m500 = doseId(f.KEY_0800, "Metformin", 500.0)
        MedReminders.writeFault = IllegalStateException("disk full")
        h.press(posted("Metformin 500"), str(R.string.md_take))
        assertEquals(3, h.notifications().size)
        assertTrue(recordsOf(m500).isEmpty())

        MedReminders.writeFault = null
        h.press(posted("Metformin 500"), str(R.string.md_take)) { recordsOf(m500).isNotEmpty() && h.notifications().size == 2 }
    }

    @Test
    fun onlyTheExactAlarmHelperSetsAnAlarm() {
        val root = File("src/main/java")
        val files = root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "ExactAlarms.kt" }.toList()
        assertTrue("No sources found under ${root.absolutePath}", files.size > 20)
        // Every way an AlarmManager alarm is set, including its plain set(...) and the repeating ones.
        val call = Regex("""\bset(Window|Exact\w*|AndAllowWhileIdle|AlarmClock|Repeating|InexactRepeating)\(|\.set\(""")
        // The helper's own set(...) and an unrelated map of reps are not alarms.
        val allowed = listOf("ExactAlarms.set(", "RepsForGraph.set(")
        val found = files.flatMap { file ->
            file.readLines().withIndex().filter { (_, line) ->
                var code = line.substringBefore("//").trim()
                allowed.forEach { code = code.replace(it, "") }
                !code.startsWith("*") && !code.startsWith("/*") && call.containsMatchIn(code)
            }.map { "${file.name}:${it.index + 1}" }
        }
        assertTrue("Alarms are set through ExactAlarms only (#346): $found", found.isEmpty())
    }

    @Test
    fun noReminderFileReadsTheWallClockDirectly() {
        val files = File("src/main/java/com/fitlens/companion/data").listFiles { f -> f.name.endsWith("Reminders.kt") }.orEmpty()
        assertTrue("No *Reminders.kt files found: ${File("src/main/java").absolutePath}", files.size >= 2)
        for (file in files) {
            val found = file.readLines().withIndex().filter { (_, line) ->
                Regex("""Local(Date|Time|DateTime)\.now\(""").containsMatchIn(line.substringBefore("//"))
            }
            assertTrue("${file.name} reads the wall clock at lines ${found.map { it.index + 1 }}", found.isEmpty())
        }
    }
}
