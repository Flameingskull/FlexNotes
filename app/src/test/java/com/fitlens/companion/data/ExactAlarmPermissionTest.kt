package com.fitlens.companion.data

import android.app.AlarmManager
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.fitlens.companion.ui.LongHistoryFixture
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Android 12 and 12L (#346): the user can revoke exact alarms. An alarm is still set (the fallback), and when they are
 * allowed again the platform's broadcast sets every alarm again as an exact alarm clock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class ExactAlarmPermissionTest {

    private val f = LongHistoryFixture
    private lateinit var h: ReminderHarness

    @Before
    fun setUp() {
        h = ReminderHarness.start(ApplicationProvider.getApplicationContext(), f.clockAt("07:00"))
    }

    @After
    fun tearDown() = h.stop()

    private fun allowExact(allowed: Boolean) =
        shadowOf(h.app.getSystemService(AlarmManager::class.java)).setCanScheduleExactAlarms(allowed)

    private fun millis(at: LocalDateTime) = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun withExactAlarmsRevokedAnAlarmIsStillSetAtTheRightMinute() {
        h.loadPlan(f.MED_RUNS, f.MED_INFOS)
        allowExact(false)
        assertFalse(ExactAlarms.canExact(h.app))
        runBlocking { MedReminders.arm(h.app) }
        // Through the fallback, still for 08:00: the chain is not lost, only no longer exact.
        assertEquals(LocalDateTime.of(2026, 10, 30, 8, 0), h.nextAlarm()!!.at)
        assertEquals(f.KEY_0800, h.nextAlarm()!!.key)
    }

    @Test
    fun allowingExactAlarmsAgainSetsBothChainsAgainAsAlarmClocks() {
        h.loadPlan(f.MED_RUNS, f.MED_INFOS)
        h.setLogReminders(LogReminderSet().with(LogReminder(LogKind.PHOTOS, true, "09:00", setOf(5))))
        allowExact(false)
        runBlocking {
            MedReminders.arm(h.app)
            LogReminders.arm(h.app)
        }
        // Whatever was set while it was revoked is gone from the picture; Android then says exact alarms are allowed again.
        h.cancelAllAlarms()
        allowExact(true)
        h.fire(Intent(h.app, MedBootReceiver::class.java).setAction(ExactAlarms.ACTION_PERMISSION_CHANGED)) {
            h.alarms().any { it.receiver == "MedAlarmReceiver" } && h.alarms().any { it.receiver == "LogAlarmReceiver" }
        }
        // The next alarm clock is the 08:00 dose, exactly.
        assertEquals(millis(LocalDateTime.of(2026, 10, 30, 8, 0)), h.nextAlarmClock()!!.triggerTime)
        assertEquals(millis(LocalDateTime.of(2026, 10, 30, 9, 0)), h.alarms().first { it.receiver == "LogAlarmReceiver" }.triggerAtMillis)
    }
}
