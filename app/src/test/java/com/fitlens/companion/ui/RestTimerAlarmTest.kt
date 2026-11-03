package com.fitlens.companion.ui

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.fitlens.companion.data.ReminderHarness
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

/**
 * The rest timer's alarm (#342, #346): a rest ends on an alarm clock at `endAt` as well as on the in-process timer, so the
 * alert comes on time with the screen off. Driven through the real [RestTimer] and [RestAlarmReceiver]; the platform's own
 * `AlarmManager.nextAlarmClock` says what is set. The rest timer reads the real clock (#343 owns that), so these tests
 * compare the alarm with the state's `endAt` rather than with a fixed time.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class RestTimerAlarmTest {

    private lateinit var h: ReminderHarness

    /** The rest-over notification's id (TimerService.ALERT_ID). */
    private val alertId = 2002

    @Before
    fun setUp() {
        h = ReminderHarness.start(ApplicationProvider.getApplicationContext())
        // An earlier test in this process may have just finished or stopped a rest.
        RestTimer.resetForTests()
    }

    @After
    fun tearDown() {
        RestTimer.resetForTests()
        h.stop()
    }

    private val state get() = RestTimer.state.value

    private fun restAlarms() = h.alarms().filter { it.receiver == "RestAlarmReceiver" }

    private fun alertShown() = h.app.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == alertId }

    private fun ring() = h.fire(Intent(h.app, RestAlarmReceiver::class.java).setAction(RestTimer.ALARM_ACTION)) { true }

    @Test
    fun aRestSetsAnAlarmClockAtItsEnd() {
        RestTimer.start(h.app, 180)
        val end = state.endAt
        assertTrue(state.active)
        assertEquals(end, h.nextAlarmClock()!!.triggerTime)
        assertEquals(1, restAlarms().size)
    }

    @Test
    fun anAdjustmentAndANewLengthMoveTheAlarm() {
        RestTimer.start(h.app, 180)
        val first = state.endAt
        RestTimer.adjust(30)
        assertEquals(first + 30_000L, state.endAt)
        assertEquals(state.endAt, h.nextAlarmClock()!!.triggerTime)
        RestTimer.setLength(60)
        // Rested about nothing yet, so about a minute is left: far earlier than before, and the alarm followed.
        assertTrue(state.endAt < first)
        assertEquals(state.endAt, h.nextAlarmClock()!!.triggerTime)
        assertEquals(1, restAlarms().size)
    }

    @Test
    fun pauseAndStopCancelItAndResumeSetsItAgain() {
        RestTimer.start(h.app, 180)
        RestTimer.pause()
        assertTrue(state.paused)
        assertNull(h.nextAlarmClock())
        // Changing a paused rest leaves no alarm set.
        RestTimer.adjust(30)
        assertNull(h.nextAlarmClock())
        RestTimer.setLength(120)
        assertNull(h.nextAlarmClock())
        assertTrue(restAlarms().isEmpty())

        RestTimer.resume()
        assertFalse(state.paused)
        assertEquals(state.endAt, h.nextAlarmClock()!!.triggerTime)

        RestTimer.stop()
        assertFalse(state.active)
        assertNull(h.nextAlarmClock())
        assertTrue(restAlarms().isEmpty())
    }

    @Test
    fun theAlarmEndingTheRestAlertsOnceAndALaterRingingDoesNothing() {
        RestTimer.start(h.app, 1)
        // The alarm arrives at the end of the rest (here at once: a second or less is left).
        ring()
        h.waitUntil("the rest-over alert") { alertShown() }
        assertFalse(state.active)
        // Finishing cancelled the alarm, and the in-process timer no longer fires for it.
        assertNull(h.nextAlarmClock())

        // The alert is dismissed; the alarm rings again (it was already on its way): nothing more happens.
        h.app.getSystemService(NotificationManager::class.java).cancel(alertId)
        ring()
        h.settle()
        assertFalse(alertShown())
    }

    @Test
    fun theInProcessTimerEndingTheRestFirstMeansTheAlarmAddsNothing() {
        RestTimer.start(h.app, 1)
        // Two seconds pass on the main looper: the in-process timer ends the rest and alerts.
        ShadowLooper.idleMainLooper(2, TimeUnit.SECONDS)
        h.waitUntil("the rest-over alert from the timer") { alertShown() }
        assertNull(h.nextAlarmClock())
        h.app.getSystemService(NotificationManager::class.java).cancel(alertId)
        ring()
        h.settle()
        assertFalse(alertShown())
        assertFalse(state.active)
    }

    @Test
    fun anAlarmArrivingAfterTheRestWasStoppedDoesNotAlert() {
        RestTimer.start(h.app, 1)
        RestTimer.stop()
        // The alarm was already on its way when the person stopped the rest.
        ring()
        h.settle()
        assertFalse(alertShown())
        assertFalse(state.active)
    }

    @Test
    fun anAlarmAlertsEvenWhenTheSettingsNeverLoad() {
        // The process was started by the alarm and the settings do not load in time: the alert still comes, with defaults.
        RestTimer.settingsTimeoutMs = 300
        RestTimer.awaitSettings = { awaitCancellation() }
        ring()
        // The receiver waits on the main looper; let its wait pass.
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS)
        h.waitUntil("the rest-over alert") { alertShown() }
    }

    @Test
    fun aStaleAlarmForARestThatWasMovedDoesNothing() {
        RestTimer.start(h.app, 180)
        val end = state.endAt
        ring()
        h.settle()
        // Three minutes from the end: not an alarm for this rest. Still running, still set, no alert.
        assertFalse(alertShown())
        assertEquals(end, state.endAt)
        assertEquals(end, h.nextAlarmClock()!!.triggerTime)

        // Nor does one for a paused rest.
        RestTimer.pause()
        ring()
        h.settle()
        assertFalse(alertShown())
        assertTrue(state.paused)
    }

    @Test
    fun anAlarmForARestTheProcessNoLongerKnowsStillMeansItEnded() {
        // The app was killed during the rest: the state is empty, but the alarm survived (a stop would have cancelled it).
        assertFalse(state.active)
        ring()
        h.waitUntil("the rest-over alert") { alertShown() }
    }
}
