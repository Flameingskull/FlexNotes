package com.fitlens.companion.ui

import android.Manifest
import android.app.Application
import androidx.compose.foundation.layout.Column
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.LogKind
import com.fitlens.companion.data.LogPattern
import com.fitlens.companion.data.LogReminder
import com.fitlens.companion.data.LogReminderSet
import com.fitlens.companion.data.LogReminders
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Settings
import com.fitlens.companion.ui.RemindersPage
import com.fitlens.companion.ui.scheduleText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #332 the way a person reaches it: the ⋮ menu on Photos and on the Body tracker, and the Schedule row on Settings ›
 * Reminders, open the one schedule sheet; what it saves is what Settings shows and what rings. The fixture's clock is
 * Friday 30 October 2026, 10:00.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class ReminderScheduleSheetTest : FunctionTest() {

    private val f = LongHistoryFixture

    private fun str(id: Int, vararg args: Any): String = app.getString(id, *args)

    private fun stored(kind: LogKind): LogReminder = LogReminderSet.decode(Settings.current().logReminders).of(kind)

    /** "Next: " and the next three dates of [kind] as saved, from the function the alarm uses. */
    private fun nextText(kind: LogKind): String = str(
        R.string.lr_next,
        LogReminders.upcoming(stored(kind), Dates.nowDateTime()).joinToString(", ") { Dates.day(it.toLocalDate().format(Dates.ISO)) }
    )

    /** The phone's settings outlive a test: each one starts with no reminders stored. */
    @Before
    fun noReminders() = runBlocking { Settings.updateDeviceNow { it.copy(logReminders = null) } }

    @Test
    fun photosMenuSetsAnEveryTwoWeeksReminderWithRealDates() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        openScreen(f.snapshot()) { s -> PhotosScreen(s, nav) }
        tap(str(R.string.cmn_more_options))
        tap(str(R.string.lr_photos))
        tap(str(R.string.lr_sched_on))
        // Switching it on explains why it needs notifications, as Settings › Reminders does.
        assertShows(str(R.string.lr_notify_why))
        tap(str(R.string.notify_ask_not_now))
        tap(str(R.string.lr_pat_weeks), scroll = true)
        // Sundays every 2 weeks from today, Friday 30 October: this Sunday, then every other one.
        val expected = str(R.string.lr_next, "Sun 1 Nov, Sun 15 Nov, Sun 29 Nov")
        assertShows(expected)
        tap(str(R.string.lr_save))
        val r = stored(LogKind.PHOTOS)
        assertEquals(true, r.on)
        assertEquals(LogPattern.EVERY_WEEKS, r.pattern)
        assertEquals(2, r.every)
        assertEquals(f.END, r.start)
        assertEquals(setOf(7), r.days)
        // What the sheet showed is what the alarm will use.
        assertEquals(expected, nextText(LogKind.PHOTOS))
    }
    @Test
    fun bodyTrackerMenuSetsTheThirtyFirstWhichShorterMonthsMoveToTheirLastDay() {
        openScreen(f.snapshot()) { s -> BodyScreen(s, nav) }
        tap(str(R.string.cmn_more_options))
        tap(str(R.string.lr_measure))
        tap(str(R.string.lr_sched_on))
        if (shows(str(R.string.notify_ask_not_now))) tap(str(R.string.notify_ask_not_now))
        // The measurements it is for are picked in this same sheet; none are picked yet.
        assertShows(str(R.string.lr_measures_none))
        tap(str(R.string.lr_pat_month), scroll = true)
        // The day of the month starts at today's, the 30th; one step up is the 31st.
        tap(str(R.string.sv_increase, str(R.string.lr_month_day_label)))
        assertShows(str(R.string.lr_month_short_note))
        val expected = str(R.string.lr_next, "Sat 31 Oct, Mon 30 Nov, Thu 31 Dec")
        assertShows(expected)
        tap(str(R.string.lr_save))
        val r = stored(LogKind.MEASUREMENTS)
        assertEquals(true, r.on)
        assertEquals(LogPattern.MONTH_DAY, r.pattern)
        assertEquals(31, r.monthDay)
        assertEquals(expected, nextText(LogKind.MEASUREMENTS))
        // The weigh-in reminder, on the same menu, was left alone.
        assertEquals(false, stored(LogKind.WEIGH_IN).on)
    }
    @Test
    fun settingsRemindersShowsTheSameScheduleOpensTheSameSheetAndSwitchesItOff() {
        val r = LogReminder(LogKind.MEASUREMENTS, true, "08:00", setOf(7), LogPattern.MONTH_DAY, start = f.END, monthDay = 31)
        runBlocking { Settings.updateDeviceNow { it.copy(logReminders = LogReminderSet().with(r).encode()) } }
        openScreen(f.snapshot()) { Column { RemindersPage() } }
        val summary = scheduleText(app, r, PortableSettings().weekStart)
        assertShows(summary)
        tap(summary)
        // The same dates the Body tracker's sheet showed for this schedule.
        assertShows(str(R.string.lr_next, "Sat 31 Oct, Mon 30 Nov, Thu 31 Dec"))
        tap(str(R.string.lr_sched_on))
        tap(str(R.string.lr_save))
        val saved = stored(LogKind.MEASUREMENTS)
        assertEquals(false, saved.on)
        assertEquals(LogPattern.MONTH_DAY, saved.pattern)
        assertEquals(31, saved.monthDay)
    }
    @Test
    fun theMeasurementsReminderIsForTheMeasurementsPickedInItsSheet() {
        val s = f.snapshot()
        // The sheet drawn alone, so a chip isn't confused with the Body tracker's row of the same name.
        openScreen(s) { snap -> ReminderScheduleSheet(LogKind.MEASUREMENTS, snap) {} }
        // None picked yet: it says so, and staying quiet waits for a pick. The weight has its own reminder.
        assertShows(str(R.string.lr_measures_none))
        assertShows(str(R.string.lr_quiet_measure_none))
        assertNotShows(f.BODYWEIGHT)
        tap(f.WAIST, scroll = true)
        assertShows(str(R.string.lr_measures_picked, f.WAIST))
        assertShows(str(R.string.lr_quiet_measure))
        tap(str(R.string.lr_save))
        val saved = stored(LogKind.MEASUREMENTS)
        assertEquals(listOf(f.WAIST), saved.measures)
        assertEquals(true, saved.quiet)
        // Settings › Reminders' Schedule row names it, through the same summary.
        assertEquals(true, scheduleText(app, saved, PortableSettings().weekStart, s).endsWith(" · " + f.WAIST))
    }
    @Test
    fun settingsRemindersAsksForDaysOnlyWhenAWeekdayPatternHasNone() {
        // Both on, neither can ring: the weigh-in repeats on set days but has none; the photos one repeats every few
        // days and has no start date. Only the weigh-in has days to pick, so only it says "Pick at least one day".
        val weigh = LogReminder(LogKind.WEIGH_IN, true, "07:30", emptySet())
        val photos = LogReminder(LogKind.PHOTOS, true, "08:00", emptySet(), LogPattern.EVERY_DAYS)
        runBlocking { Settings.updateDeviceNow { it.copy(logReminders = LogReminderSet().with(weigh).with(photos).encode()) } }
        openScreen(f.snapshot()) { Column { RemindersPage() } }
        assertEquals(1, drawn().count { it == str(R.string.lr_no_days) })
    }
    // END OF TESTS
}