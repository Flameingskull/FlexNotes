package com.fitlens.companion.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.fitlens.companion.R
import com.fitlens.companion.ui.LongHistoryFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Proves [ReminderHarness] (#333): each test goes through a real receiver, a real Room database and the real
 * notification and alarm code, and asserts what was posted, set and stored. 2026-10-30, [LongHistoryFixture.END], is a
 * Friday (ISO day 5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ReminderHarnessTest {

    private val f = LongHistoryFixture
    private val key = "${f.END} 08:00"
    private lateinit var h: ReminderHarness

    @Before
    fun setUp() {
        h = ReminderHarness.start(ApplicationProvider.getApplicationContext(), f.clockAt("07:59"))
    }

    @After
    fun tearDown() = h.stop()

    private fun photosOnFridays() = LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(5)))

    private fun str(id: Int): String = h.app.getString(id)

    @Test
    fun aLoggingAlarmPostsItsNotificationAndSetsTheNextAlarm() {
        h.setLogReminders(photosOnFridays())
        // The alarm is delivered at 08:00 on the Friday, so the next one is the following Friday (#345: exact, from the clock).
        Dates.clockSource = f.clockAt("08:00")
        h.fire(h.logAlarm(key)) { h.notifications().isNotEmpty() && h.nextAlarm() != null }

        val posted = h.notifications().single()
        assertEquals(str(R.string.lr_photos_title), posted.title)
        assertEquals(str(R.string.lr_photos_text), posted.text)
        // The next ring is the exact next chosen weekday and time, carrying its own due time.
        val next = h.nextAlarm()!!
        assertEquals("LogAlarmReceiver", next.receiver)
        assertEquals(LocalDateTime.of(2026, 11, 6, 8, 0), next.at)
        assertEquals(DayOfWeek.FRIDAY, next.at.dayOfWeek)
        assertEquals(LocalTime.of(8, 0), next.at.toLocalTime())
        assertEquals("2026-11-06 08:00", next.key)
    }

    @Test
    fun onceTodaysTimeHasPassedTheNextLoggingAlarmIsTheNextChosenWeekdayAndTime() {
        // Friday 2026-10-30 at 08:30: photos on Fridays at 08:00 have passed, so the next is a week away.
        Dates.clockSource = f.clockAt("08:30")
        h.setLogReminders(photosOnFridays())
        runBlocking { LogReminders.arm(h.app) }
        assertEquals(LocalDateTime.of(2026, 11, 6, 8, 0), h.nextAlarm()!!.at)
        assertEquals("2026-11-06 08:00", h.nextAlarm()!!.key)

        // With a weigh-in on Mondays at 07:30 as well, the next alarm is that Monday, 2026-11-02.
        h.setLogReminders(photosOnFridays().with(LogReminder(LogKind.WEIGH_IN, true, "07:30", setOf(1))))
        runBlocking { LogReminders.arm(h.app) }
        assertEquals(LocalDateTime.of(2026, 11, 2, 7, 30), h.nextAlarm()!!.at)
        assertEquals("2026-11-02 07:30", h.nextAlarm()!!.key)

        // Before 08:00 the same Friday it is still today.
        Dates.clockSource = f.clockAt("07:59")
        h.setLogReminders(photosOnFridays())
        runBlocking { LogReminders.arm(h.app) }
        assertEquals(LocalDateTime.of(2026, 10, 30, 8, 0), h.nextAlarm()!!.at)
    }

    @Test
    fun aLoggingReminderIsAnAlarmClockAtItsExactMinute() {
        // Friday 2026-10-30, 08:30: photos on Fridays at 08:00 next ring on 2026-11-06 at exactly 08:00 (#346).
        Dates.clockSource = f.clockAt("08:30")
        h.setLogReminders(photosOnFridays())
        runBlocking { LogReminders.arm(h.app) }
        val expected = LocalDateTime.of(2026, 11, 6, 8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        assertEquals(expected, h.nextAlarmClock()!!.triggerTime)
    }

    @Test
    fun aLoggingAlarmThatCannotFinishRingsAgainAMinuteLaterWithTheSameKey() {
        // The receiver sets this when it runs out of time (#346), so the chain is never lost.
        LogReminders.retryLater(h.app, key)
        val retry = h.nextAlarm()!!
        assertEquals("LogAlarmReceiver", retry.receiver)
        assertEquals(key, retry.key)
        assertEquals(LocalDateTime.of(2026, 10, 30, 8, 1), retry.at)
        assertEquals(retry.triggerAtMillis, h.nextAlarmClock()!!.triggerTime)
    }

    @Test
    fun aLoggingReminderForSomethingAlreadyLoggedStaysQuiet() {
        h.setLogReminders(photosOnFridays().with(LogReminder(LogKind.WEIGH_IN, true, "08:00", setOf(5))))
        h.loadRecords(listOf(MRecord(0L, "Bodyweight", "kg", f.END, "07:30:00", 84.2, null, Sources.FLEXNOTES)))
        h.fire(h.logAlarm(key)) { h.nextAlarm() != null }

        // Weighed in already, so only the photos reminder rings.
        assertEquals(listOf(str(R.string.lr_photos_title)), h.notifications().map { it.title })
        assertEquals(84.2, h.snapshot().recordsByName.getValue("Bodyweight").single().value, 0.0)
    }

    @Test
    fun withoutPermissionAReminderPostsNothingButTheNextAlarmIsStillSet() {
        h.allowNotifications(false)
        h.setLogReminders(photosOnFridays())
        h.fire(h.logAlarm(key)) { h.nextAlarm() != null }

        assertTrue(h.notifications().isEmpty())
    }

    @Test
    fun dosesDueTogetherEachGetTheirOwnNotificationAndSkippingOneFromTheShadeClearsOnlyIt() {
        h.loadPlan(f.MED_RUNS, f.MED_INFOS)
        // Three doses at 08:00: two of Metformin and an injected Semaglutide (#327: one notification each).
        assertEquals(3, h.openAt(f.KEY_0800).size)

        h.fire(h.medAlarm(f.KEY_0800)) { h.notifications().size == 3 && h.nextAlarm() != null }
        val shown = h.notifications()
        assertEquals(3, shown.size)
        assertTrue(shown.all { it.actions.size == 3 })
        assertEquals(listOf(f.METFORMIN, f.METFORMIN, f.SEMAGLUTIDE), shown.map { it.text.substringBefore(' ') }.sorted())
        assertEquals("MedAlarmReceiver", h.nextAlarm()!!.receiver)

        // Pressing Skip in the shade, with no screen open, skips that one dose and takes down only its notification.
        val sema = shown.first { f.SEMAGLUTIDE in it.text }
        h.press(sema, str(R.string.md_skip)) { h.openAt(f.KEY_0800).size == 2 && h.notifications().size == 2 }
        h.settle()
        assertTrue(h.notifications().none { f.SEMAGLUTIDE in it.text })
        assertEquals(2, h.openAt(f.KEY_0800).size)
        assertEquals(1, h.openAt(f.KEY_0805).size)
    }

    @Test
    fun dosesTakenBeforeTheAlarmLeaveNothingToRemindAbout() {
        h.loadPlan(f.MED_RUNS, f.MED_INFOS)
        h.take(f.KEY_0800)
        assertTrue(h.openAt(f.KEY_0800).isEmpty())
        // Only what was open is taken: Vitamin D, five minutes later, still is.
        assertEquals(1, h.openAt(f.KEY_0805).size)

        h.fire(h.medAlarm(f.KEY_0800)) { h.nextAlarm() != null }
        assertTrue(h.notifications().isEmpty())
    }
}
