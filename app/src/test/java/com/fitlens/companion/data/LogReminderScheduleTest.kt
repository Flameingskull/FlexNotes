package com.fitlens.companion.data

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * #332 through the real receivers, database and alarms ([ReminderHarness]): the new patterns after a reboot, a reminder
 * missed while the phone was off, and the "stay quiet if already logged" choice. 2026-10-30 is a Friday (ISO day 5).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LogReminderScheduleTest {

    private val f = LongHistoryFixture
    private val key = "${f.END} 08:00"
    private lateinit var h: ReminderHarness

    @Before
    fun setUp() {
        h = ReminderHarness.start(ApplicationProvider.getApplicationContext(), f.clockAt("07:59"))
    }

    @After
    fun tearDown() = h.stop()

    private fun str(id: Int): String = h.app.getString(id)

    private fun boot() = h.fire(Intent(h.app, MedBootReceiver::class.java).setAction(Intent.ACTION_BOOT_COMPLETED)) {
        h.alarms().any { it.receiver == "LogAlarmReceiver" }
    }

    private fun logAlarm(): SetAlarm = h.alarms().single { it.receiver == "LogAlarmReceiver" }

    /** The logging reminders' notifications only (ids 7400 to 7403). */
    private fun logTitles(): List<String> = h.notifications().filter { it.id in 7400..7403 }.map { it.title }

    @Test
    fun aRebootSetsTheNextEveryTwoWeeksDateAndEachRingSetsTheOneAfter() {
        // Every 2 weeks on Fridays from Friday 30 October: 30 October, 13 November, 27 November.
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(5), LogPattern.EVERY_WEEKS, 2, f.END)))
        Dates.clockSource = f.clockAt("09:00")
        boot()
        h.settle()
        assertEquals(LocalDateTime.of(2026, 11, 13, 8, 0), logAlarm().at)
        assertEquals("2026-11-13 08:00", logAlarm().key)
        // It rings on 13 November; the next is two weeks later, not the Friday after.
        Dates.clockSource = f.clockAt("08:00", "2026-11-13")
        h.fire(logAlarm().intent) { h.alarms().any { it.receiver == "LogAlarmReceiver" && it.key == "2026-11-27 08:00" } }
        assertEquals(listOf(str(R.string.lr_photos_title)), logTitles())
        // Tapping it opens where photos are taken.
        val tap = shadowOf(h.notifications().single { it.id in 7400..7403 }.notification.contentIntent).savedIntent
        assertEquals(LogKind.PHOTOS.key, tap.getStringExtra(LogReminders.EXTRA_OPEN_LOG))
    }
    @Test
    fun aReminderMissedWhileThePhoneWasOffIsShownWhenItComesBack() {
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(5))))
        // At 07:59 the alarm is set for 08:00, and noted.
        runBlocking { LogReminders.arm(h.app) }
        assertEquals(key, LogReminderSet.decode(Settings.current().logReminders).armedFor)
        // The phone is off at 08:00 (its alarms are lost) and starts again at 09:30.
        h.cancelAllAlarms()
        Dates.clockSource = f.clockAt("09:30")
        h.fire(Intent(h.app, MedBootReceiver::class.java).setAction(Intent.ACTION_BOOT_COMPLETED)) {
            logTitles().isNotEmpty() && h.alarms().any { it.receiver == "LogAlarmReceiver" }
        }
        h.settle()
        assertEquals(listOf(str(R.string.lr_photos_title)), logTitles())
        assertEquals("2026-11-06 08:00", logAlarm().key)
        // What is noted now is next Friday's, so arming again owes nothing more.
        assertEquals("2026-11-06 08:00", LogReminderSet.decode(Settings.current().logReminders).armedFor)
        assertEquals(emptyList<LogReminders.Ring>(), LogReminders.owed(LogReminderSet.decode(Settings.current().logReminders), Dates.nowDateTime()))
    }

    @Test
    fun aReminderMoreThanTwelveHoursOldIsNotShownLate() {
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(5))).copy(armedFor = key))
        Dates.clockSource = f.clockAt("20:30")
        boot()
        h.settle()
        assertTrue(logTitles().isEmpty())
        assertEquals("2026-11-06 08:00", logAlarm().key)
    }
    @Test
    fun aMeasurementsReminderNamesThePickedOnesStillToLog() {
        h.loadRecords(listOf(
            MRecord(0L, "Chest", "cm", "2026-10-23", "07:30:00", 101.0, null, Sources.FLEXNOTES),
            MRecord(0L, "Waist", "cm", f.END, "07:30:00", 82.0, null, Sources.FLEXNOTES)
        ))
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.MEASUREMENTS, true, "08:00", setOf(5), measures = listOf("Waist", "Chest"))))
        Dates.clockSource = f.clockAt("08:00")
        h.fire(h.logAlarm(key)) { logTitles().isNotEmpty() }
        // Waist is logged today and Chest isn't: it comes, naming Chest.
        val shown = h.notifications().single { it.id in 7400..7403 }
        assertEquals(str(R.string.lr_measure_title), shown.title)
        assertEquals(h.app.getString(R.string.lr_measure_named, "Chest"), shown.text)
        // Its tap opens the Body tracker.
        val tap = shadowOf(shown.notification.contentIntent).savedIntent
        assertEquals(LogKind.MEASUREMENTS.key, tap.getStringExtra(LogReminders.EXTRA_OPEN_LOG))
    }
    @Test
    fun aWeighInOrAnotherMeasurementDoesNotSilenceTheMeasurementsReminder() {
        fun texts() = h.notifications().filter { it.id in 7400..7403 }.map { it.text }
        h.loadRecords(listOf(
            MRecord(0L, "Bodyweight", "kg", f.END, "07:30:00", 84.2, null, Sources.FLEXNOTES),
            MRecord(0L, "Chest", "cm", f.END, "07:35:00", 101.0, null, Sources.FLEXNOTES),
            MRecord(0L, "Waist", "cm", "2026-10-23", "07:30:00", 82.0, null, Sources.FLEXNOTES)
        ))
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.MEASUREMENTS, true, "08:00", setOf(5), measures = listOf("Waist"))))
        Dates.clockSource = f.clockAt("08:00")
        h.fire(h.logAlarm(key)) { logTitles().isNotEmpty() }
        assertEquals(listOf(str(R.string.lr_measure_title)), logTitles())
        assertEquals(listOf(h.app.getString(R.string.lr_measure_named, "Waist")), texts())
        // None picked: nothing to count, so even a day with values logged gets its reminder, in general words.
        h.app.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        h.waitUntil("the first alarm's next one noted") { LogReminderSet.decode(Settings.current().logReminders).armedFor == "2026-11-06 08:00" }
        Dates.clockSource = f.clockAt("07:59")
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.MEASUREMENTS, true, "08:00", setOf(5))))
        Dates.clockSource = f.clockAt("08:00")
        h.fire(h.logAlarm(key)) { logTitles().isNotEmpty() }
        assertEquals(listOf(str(R.string.lr_measure_text)), texts())
    }
    @Test
    fun aMeasurementsReminderStaysQuietOnceEveryPickIsLoggedUnlessTheUserChoseOtherwise() {
        val measure = LogReminder(LogKind.MEASUREMENTS, true, "08:00", setOf(5), measures = listOf("Waist", "Chest"))
        h.loadRecords(listOf(
            MRecord(0L, "Chest", "cm", f.END, "07:40:00", 100.5, null, Sources.FLEXNOTES),
            MRecord(0L, "Waist", "cm", f.END, "07:30:00", 82.0, null, Sources.FLEXNOTES)
        ))
        h.setLogReminders(LogReminderSet().with(measure))
        Dates.clockSource = f.clockAt("08:00")
        h.fire(h.logAlarm(key)) { h.nextAlarm() != null }
        h.settle()
        assertTrue(logTitles().isEmpty())
        // With "Stay quiet if already logged that day" off, it reminds anyway, in its general words.
        h.waitUntil("the first alarm's next one noted") { LogReminderSet.decode(Settings.current().logReminders).armedFor == "2026-11-06 08:00" }
        Dates.clockSource = f.clockAt("07:59")
        h.setLogReminders(LogReminderSet().with(measure.copy(quiet = false)))
        Dates.clockSource = f.clockAt("08:00")
        h.fire(h.logAlarm(key)) { logTitles().isNotEmpty() }
        val shown = h.notifications().single { it.id in 7400..7403 }
        assertEquals(str(R.string.lr_measure_text), shown.text)
    }
    @Test
    fun twoRemindersMissedWhileThePhoneWasOffAreBothShown() {
        // A weigh-in at 07:30 and photos at 08:00 on Friday 30 October; the phone is off from 07:00 until 09:00.
        h.setLogReminders(LogReminderSet()
            .with(LogReminder(LogKind.WEIGH_IN, true, "07:30", setOf(5)))
            .with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(5)))
            .copy(armedFor = "${f.END} 07:30"))
        Dates.clockSource = f.clockAt("09:00")
        h.fire(Intent(h.app, MedBootReceiver::class.java).setAction(Intent.ACTION_BOOT_COMPLETED)) {
            logTitles().size == 2 && h.alarms().any { it.receiver == "LogAlarmReceiver" }
        }
        h.settle()
        assertEquals(2, logTitles().size)
        assertEquals(setOf(str(R.string.lr_weigh_title), str(R.string.lr_photos_title)), logTitles().toSet())
        // The next alarm is next Friday's weigh-in, and that is what is noted, so nothing more is owed.
        assertEquals("2026-11-06 07:30", logAlarm().key)
        assertEquals("2026-11-06 07:30", LogReminderSet.decode(Settings.current().logReminders).armedFor)
    }
    @Test
    fun aMeasurementsReminderNamingItsPicksHidesThemOnTheLockScreen() {
        h.loadRecords(listOf(MRecord(0L, "Waist", "cm", "2026-10-23", "07:30:00", 82.0, null, Sources.FLEXNOTES)))
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.MEASUREMENTS, true, "08:00", setOf(5), measures = listOf("Waist"))))
        Dates.clockSource = f.clockAt("08:00")
        h.fire(h.logAlarm(key)) { logTitles().isNotEmpty() }
        val shown = h.notifications().single { it.id in 7400..7403 }
        // The shade names Waist; the lock screen shows only the general words.
        assertEquals(h.app.getString(R.string.lr_measure_named, "Waist"), shown.text)
        assertEquals(Notification.VISIBILITY_PRIVATE, shown.notification.visibility)
        val lockScreen = shown.notification.publicVersion?.extras
        assertEquals(str(R.string.lr_measure_title), lockScreen?.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertEquals(str(R.string.lr_measure_text), lockScreen?.getCharSequence(Notification.EXTRA_TEXT)?.toString())
        // A photos reminder names nothing private, so it has no separate lock-screen version.
        h.app.getSystemService(NotificationManager::class.java).cancelAll()
        h.waitUntil("the first alarm's next one noted") { LogReminderSet.decode(Settings.current().logReminders).armedFor == "2026-11-06 08:00" }
        Dates.clockSource = f.clockAt("07:59")
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(5))))
        Dates.clockSource = f.clockAt("08:00")
        h.fire(h.logAlarm(key)) { logTitles().isNotEmpty() }
        assertEquals(null, h.notifications().single { it.id in 7400..7403 }.notification.publicVersion)
    }
    @Test
    fun theNextAlarmIsSetBeforeAMissedReminderIsShown() {
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(5))).copy(armedFor = key))
        // The phone is back at 09:30 and its data is slow to open.
        Dates.clockSource = f.clockAt("09:30")
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        MedReminders.loadOverride = { gate.await(); null }
        val arming = Thread { runBlocking { LogReminders.arm(h.app) } }.apply { start() }
        // Next Friday's alarm is set while the missed 08:00 waits to be shown, and 08:00 is still noted, so still owed.
        h.waitUntil("next Friday's alarm") { h.alarms().any { it.receiver == "LogAlarmReceiver" && it.key == "2026-11-06 08:00" } }
        assertEquals(key, LogReminderSet.decode(Settings.current().logReminders).armedFor)
        // Once the data answers, the missed reminder is shown and next Friday's is noted.
        gate.complete(Unit)
        arming.join(20_000)
        assertEquals(listOf(str(R.string.lr_photos_title)), logTitles())
        assertEquals("2026-11-06 08:00", LogReminderSet.decode(Settings.current().logReminders).armedFor)
    }
    @Test
    fun anAlarmForAReminderAlreadyCaughtUpShowsNothingMore() {
        // A catch-up already showed Friday's 08:00 photos and noted next Friday's; the 08:00 alarm comes late, at 08:10.
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(5))).copy(armedFor = "2026-11-06 08:00"))
        Dates.clockSource = f.clockAt("08:10")
        h.fire(h.logAlarm(key)) { h.alarms().any { it.receiver == "LogAlarmReceiver" && it.key == "2026-11-06 08:00" } }
        h.settle()
        assertTrue(logTitles().isEmpty())
        assertEquals("2026-11-06 08:00", LogReminderSet.decode(Settings.current().logReminders).armedFor)
    }
    @Test
    fun aFailedSettingsWriteStillSetsTheNextLoggingAlarm() {
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(5))))
        // From now on nothing can be saved: the phone's storage is full.
        Settings.deviceWriteFault = java.io.IOException("storage full")
        Dates.clockSource = f.clockAt("08:00")
        h.fire(h.logAlarm(key)) { h.alarms().any { it.receiver == "LogAlarmReceiver" && it.key == "2026-11-06 08:00" } }
        h.settle()
        assertEquals(listOf(str(R.string.lr_photos_title)), logTitles())
        // What the alarm is for is kept in memory, so the same alarm delivered again shows nothing more.
        assertEquals("2026-11-06 08:00", LogReminderSet.decode(Settings.current().logReminders).armedFor)
        h.app.getSystemService(android.app.NotificationManager::class.java).cancelAll()
        h.fire(h.logAlarm(key))
        h.settle()
        assertTrue(logTitles().isEmpty())
    }
    // END OF TESTS
}
