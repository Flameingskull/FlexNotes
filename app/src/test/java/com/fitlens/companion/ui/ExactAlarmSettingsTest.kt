package com.fitlens.companion.ui

import android.app.AlarmManager
import android.app.Application
import androidx.compose.foundation.layout.Column
import com.fitlens.companion.R
import com.fitlens.companion.data.LogKind
import com.fitlens.companion.data.LogReminder
import com.fitlens.companion.data.LogReminderSet
import com.fitlens.companion.data.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #346's Settings criterion, drawn the way a person sees it: on Android 13 and later exact alarms are granted at install,
 * so there is no "Reminders on the minute" row on Settings › Medications or Reminders and search finds none; "ten minutes"
 * is not said anywhere.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class ExactAlarmSettingsAndroid13Test : FunctionTest() {

    private val f = LongHistoryFixture

    private fun granted() = shadowOf(app.getSystemService(AlarmManager::class.java)).setCanScheduleExactAlarms(true)

    private fun exactRow() = app.getString(R.string.mr_exact)

    @Test
    fun settingsMedicationsHasNoExactAlarmRowAndSaysNothingAboutTenMinutes() {
        granted()
        openScreen(f.snapshot()) { Column { MedicationsPage() } }
        assertShows(app.getString(R.string.mr_reminders))
        assertNotShows(exactRow())
        assertNotShows("ten minutes", substring = true)
    }

    @Test
    fun settingsRemindersHasNoExactAlarmRowEvenWithAReminderOn() {
        granted()
        runBlocking { Settings.updateDeviceNow { it.copy(logReminders = LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(5))).encode()) } }
        openScreen(f.snapshot()) { Column { RemindersPage() } }
        assertShows(app.getString(R.string.lr_photos))
        assertNotShows(exactRow())
        assertNotShows("ten minutes", substring = true)
    }

    @Test
    fun searchingForExactAlarmsFindsNoRowThatIsNotThere() {
        granted()
        openScreen(f.snapshot()) { snap -> SettingsScreen(snap, nav) }
        type("exact")
        assertNotShows(exactRow())
    }
}

/**
 * The same on Android 12, where the user can revoke the permission: then the row shows, with the warning (no "ten minutes"),
 * and search finds it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [31], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class ExactAlarmSettingsAndroid12Test : FunctionTest() {

    private val f = LongHistoryFixture

    private fun revoked() = shadowOf(app.getSystemService(AlarmManager::class.java)).setCanScheduleExactAlarms(false)

    private fun exactRow() = app.getString(R.string.mr_exact)

    @Test
    fun settingsMedicationsShowsTheRowAndWarningWithoutTenMinutes() {
        revoked()
        openScreen(f.snapshot()) { Column { MedicationsPage() } }
        assertShows(exactRow())
        assertShows(app.getString(R.string.mr_exact_warning))
        assertNotShows("ten minutes", substring = true)
    }

    @Test
    fun settingsRemindersShowsTheRowWhenAReminderIsOn() {
        revoked()
        runBlocking { Settings.updateDeviceNow { it.copy(logReminders = LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "08:00", setOf(5))).encode()) } }
        openScreen(f.snapshot()) { Column { RemindersPage() } }
        assertShows(exactRow())
        assertNotShows("ten minutes", substring = true)
    }

    @Test
    fun searchFindsTheRow() {
        revoked()
        openScreen(f.snapshot()) { snap -> SettingsScreen(snap, nav) }
        type("exact")
        assertShows(exactRow())
    }
}
