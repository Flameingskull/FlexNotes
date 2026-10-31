package com.fitlens.companion.data

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowLooper
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** A medication schedule to run from [start] (`yyyy-MM-dd`), for [ReminderHarness.loadPlan] and the fixtures. */
data class MedRun(val schedule: MedSchedule, val start: String)

/** A notification as the shade shows it: what a person reads and the buttons they can press. */
class Posted(
    val id: Int,
    /** The tag a medication reminder is told apart by (`<doseId>@<due time>`); null for one with no tag. */
    val tag: String?,
    val title: String,
    val text: String,
    /** The labels of its buttons, in order. */
    val actions: List<String>,
    val notification: Notification
)

/** An alarm that is set: when it rings, which receiver it wakes and the due time it carries. */
class SetAlarm(
    val triggerAtMillis: Long,
    /** [triggerAtMillis] in the phone's time zone, the way the reminders worked it out. */
    val at: LocalDateTime,
    /** The simple class name of the receiver, such as `MedAlarmReceiver`. */
    val receiver: String?,
    /** The due time it carries (`yyyy-MM-dd HH:mm`), for the medication and the logging alarms alike. */
    val key: String?,
    val intent: Intent
)

/**
 * Drives the reminders' notifications and alarms under Robolectric, over a real Room database (#333), so a test asserts
 * what a reminder actually shows and does, and what was recorded.
 *
 * ```
 * @RunWith(RobolectricTestRunner::class)
 * @Config(sdk = [34], application = Application::class)
 * class SomeTest {
 *     private lateinit var h: ReminderHarness
 *     @Before fun setUp() { h = ReminderHarness.start(ApplicationProvider.getApplicationContext(), clock) }
 *     @After fun tearDown() = h.stop()
 * }
 * ```
 *
 * - **Data:** [loadPlan] writes medication schedules through `MedSchedules.save` and `activate`, [loadRecords] body
 *   values through `Store.addManualRecord`, [take] a dose through `MedSchedules.take`. Everything lands in the real
 *   database and `Store.snapshot`, so `MedReminders.ensureLoaded` returns it. [setLogReminders] and [setDevice] change
 *   the phone settings the receivers read.
 * - **Alarms:** every alarm is an alarm clock (#346), so [nextAlarmClock] is the platform's answer for the next one. [fire] delivers an intent to its receiver the way Android does (`sendBroadcast`, which gives the
 *   receiver the `goAsync` result it needs) and waits. [medAlarm] and [logAlarm] build the intents. [press] presses a
 *   notification's button by its label, from the intent saved in its `PendingIntent`.
 * - **Reading back:** [notifications] lists what is posted, [alarms] and [nextAlarm] what is set, [openAt] and
 *   [snapshot] what is stored.
 * - **Permission:** [allowNotifications] grants or denies POST_NOTIFICATIONS (granted after [start]).
 * - **Time:** `Dates.clockSource` is set to the clock given to [start], and `LogReminders.arm` and
 *   `MedReminders.arm`/`ring`/`handle` read it (#345, #327), so a test asserts exact dates. Set `Dates.clockSource` again
 *   mid-test to let time pass (an alarm delivered late).
 *
 * **Waiting.** The receivers do their work on `Dispatchers.IO` after returning, so [fire] cannot know when it has
 * finished. Pass `until` naming the outcome the test expects (a notification posted, an alarm set, a dose taken). Every
 * ring ends by setting or cancelling the next alarm, after showing its notifications, so "the alarm is set" also
 * means "the notifications are posted". With no `until`, [fire] returns once what is posted and set has stopped
 * changing for [QUIET_MS], which is enough to assert something happened but not to prove that nothing will.
 */
class ReminderHarness private constructor(val app: Application) {

    private val notificationManager: NotificationManager get() = app.getSystemService(NotificationManager::class.java)
    private val alarmManager: AlarmManager get() = app.getSystemService(AlarmManager::class.java)

    // ---- data -------------------------------------------------------------------------------------------------

    /**
     * Saves each run's schedule and starts it, then [infos] (supply, injected), and loads everything into the Store.
     * Ids are assigned by the database in order, so schedule, phase and dose ids count up from 1 on a fresh database.
     * Returns the activations' ids.
     */
    fun loadPlan(runs: List<MedRun>, infos: List<MedInfo> = emptyList()): List<Long> = runBlocking {
        val activations = runs.map { run ->
            val fresh = run.schedule.copy(
                id = 0L,
                phases = run.schedule.phases.map { p -> p.copy(id = 0L, doses = p.doses.map { it.copy(id = 0L) }) }
            )
            MedSchedules.activate(MedSchedules.save(fresh), run.start)
        }
        infos.forEach { MedExtras.setInfo(it) }
        Store.reload()
        activations
    }

    /** Saves body values as the user would type them (source `manual`), then loads them into the Store. */
    fun loadRecords(records: List<MRecord>) = runBlocking {
        records.forEach { Store.addManualRecord(it.name, it.unit, it.date, it.time, it.value, it.comment) }
        Store.reload()
    }

    /** Changes the phone settings the receivers read. */
    fun setDevice(change: (DeviceSettings) -> DeviceSettings) = runBlocking { Settings.updateDeviceNow(change) }

    /** Sets the logging reminders (weigh-in, measurements, photos, workout) on Settings › Reminders. */
    fun setLogReminders(reminders: LogReminderSet) = setDevice { it.copy(logReminders = reminders.encode()) }

    /** What the Store holds now, as the receivers see it. */
    fun snapshot(): Snapshot = runBlocking { MedReminders.ensureLoaded() } ?: error("Nothing is loaded into the Store")

    /** The doses due at [key] (`yyyy-MM-dd HH:mm`) whether or not they were taken or skipped. */
    fun dueAt(key: String): List<DueDose> = MedSchedules.dueOn(snapshot().medPlan, key.take(10)).filter { it.due == key }

    /** The doses due at [key] that are neither taken nor skipped: what the reminder is about. */
    fun openAt(key: String): List<DueDose> = snapshot().let { MedReminders.openAt(it.medPlan, it.medicationView.records, key) }

    /** Takes every open dose due at [key], as ticking them on Medications does. */
    fun take(key: String) = runBlocking { openAt(key).forEach { MedSchedules.take(it, it.time + ":00") } }

    // ---- alarms -----------------------------------------------------------------------------------------------

    /** The intent a medication alarm delivers: its ring for [key], or its [action] (taken, snooze, skip) with no dose, as the old version's. */
    fun medAlarm(key: String?, action: String = MedReminders.ACTION_ALARM): Intent =
        Intent(app, MedAlarmReceiver::class.java).setAction(action).apply { key?.let { putExtra(MedReminders.EXTRA_AT, it) } }

    /** The intent a logging alarm delivers for [key] (`yyyy-MM-dd HH:mm`). */
    fun logAlarm(key: String?): Intent =
        Intent(app, LogAlarmReceiver::class.java).setAction(LogReminders.ACTION_ALARM).apply { key?.let { putExtra(LogReminders.EXTRA_AT, it) } }

    /** Delivers [intent] to its receiver and waits for [until], or until nothing posted or set changes (see the class note). */
    fun fire(intent: Intent, until: (() -> Boolean)? = null) {
        app.sendBroadcast(intent)
        ShadowLooper.idleMainLooper()
        if (until != null) waitUntil("the outcome of ${intent.action}", condition = until) else settle()
    }

    /** Presses the button labelled [label] on [posted], as tapping it in the shade does with the app closed. */
    fun press(posted: Posted, label: String, until: (() -> Boolean)? = null) {
        val action = posted.notification.actions.orEmpty().firstOrNull { it.title.toString() == label }
            ?: throw AssertionError("\"${posted.title}\" has no button \"$label\"; its buttons are ${posted.actions}")
        fire(shadowOf(action.actionIntent).savedIntent, until)
    }

    /** What is posted now, lowest id first, then by tag. */
    fun notifications(): List<Posted> = notificationManager.activeNotifications.map { sbn ->
        val extras = sbn.notification.extras
        Posted(
            sbn.id,
            sbn.tag,
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty(),
            sbn.notification.actions.orEmpty().map { it.title.toString() },
            sbn.notification
        )
    }.sortedWith(compareBy<Posted>({ it.id }, { it.tag.orEmpty() }))

    /** Every alarm that is set, earliest first. */
    fun alarms(): List<SetAlarm> = shadowOf(alarmManager).scheduledAlarms.map { alarm ->
        val intent = shadowOf(alarm.operation).savedIntent
        SetAlarm(
            alarm.triggerAtTime,
            Instant.ofEpochMilli(alarm.triggerAtTime).atZone(ZoneId.systemDefault()).toLocalDateTime(),
            intent.component?.className?.substringAfterLast('.'),
            intent.getStringExtra(MedReminders.EXTRA_AT) ?: intent.getStringExtra(LogReminders.EXTRA_AT),
            intent
        )
    }.sortedBy { it.triggerAtMillis }

    /** The alarm that rings next, or null when none is set. */
    fun nextAlarm(): SetAlarm? = alarms().firstOrNull()

    /**
     * The platform's own answer for "an alarm clock is set": what Android shows as the alarm icon's time (#346), or null
     * when no alarm was set through `setAlarmClock`. An alarm that is not exact and on time is not one.
     */
    fun nextAlarmClock(): AlarmManager.AlarmClockInfo? = alarmManager.nextAlarmClock

    /** Grants or denies the permission to post notifications. A reminder that rings without it posts nothing. */
    fun allowNotifications(allowed: Boolean) {
        val permissions = shadowOf(app)
        if (allowed) permissions.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        else permissions.denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    // ---- waiting ----------------------------------------------------------------------------------------------

    /** Runs the main looper until [condition] holds, failing with what is posted and set if it doesn't within [timeoutMs]. */
    fun waitUntil(what: String, timeoutMs: Long = TIMEOUT_MS, condition: () -> Boolean) {
        val end = System.nanoTime() + timeoutMs * 1_000_000
        while (true) {
            ShadowLooper.idleMainLooper()
            if (runCatching(condition).getOrDefault(false)) return
            if (System.nanoTime() > end) throw AssertionError("Gave up waiting for $what. ${describe()}")
            Thread.sleep(POLL_MS)
        }
    }

    /** Returns once what is posted and set has not changed for [QUIET_MS]. */
    fun settle(timeoutMs: Long = TIMEOUT_MS) {
        val end = System.nanoTime() + timeoutMs * 1_000_000
        var last = fingerprint()
        var since = System.nanoTime()
        while (System.nanoTime() < end) {
            ShadowLooper.idleMainLooper()
            val now = fingerprint()
            if (now != last) {
                last = now
                since = System.nanoTime()
            } else if ((System.nanoTime() - since) / 1_000_000 >= QUIET_MS) return
            Thread.sleep(POLL_MS)
        }
        throw AssertionError("Posted notifications and set alarms kept changing. ${describe()}")
    }

    private fun fingerprint(): List<Any?> =
        listOf(notifications().map { Triple(it.id, it.tag, it.text) }, alarms().map { it.triggerAtMillis to it.key })

    private fun describe(): String =
        "Posted: ${notifications().map { "${it.title} / ${it.text} ${it.actions}" }}. Alarms: ${alarms().map { "${it.receiver} at ${it.at} for ${it.key}" }}."

    // ---- set-up and tear-down ---------------------------------------------------------------------------------

    private fun begin(clock: Clock) {
        app.deleteDatabase(Db.NAME)
        Store.init(app)
        Settings.init(app)
        runBlocking { Settings.awaitLoaded() }
        // The DataStore file outlives a test's application; every test starts from the defaults.
        setDevice { DeviceSettings() }
        Dates.clockSource = clock
        allowNotifications(true)
        // Android 13 and later grant USE_EXACT_ALARM at install.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    /** Puts every seam back and removes the database. Call it from `@After`. */
    fun stop() {
        for (owner in listOf(MedReminders::class.java, LogReminders::class.java)) {
            // A later test must not rearm against this test's application.
            runCatching { (owner.getDeclaredField("pending").apply { isAccessible = true }.get(null) as? Job)?.cancel() }
            runCatching { owner.getDeclaredField("app").apply { isAccessible = true }.set(null, null) }
        }
        Dates.clockSource = Clock.systemDefaultZone()
        MedReminders.raceHook = null
        MedReminders.loadOverride = null
        runCatching { Store.db.close() }
        app.deleteDatabase(Db.NAME)
    }

    companion object {
        /** The longest a wait lasts: the first database open under Robolectric takes seconds on a busy build machine. */
        const val TIMEOUT_MS = 20_000L
        const val QUIET_MS = 600L
        private const val POLL_MS = 25L

        /** Starts from an empty database, default settings, notifications allowed and `Dates.clockSource` set to [clock]. */
        fun start(app: Application, clock: Clock = Clock.systemDefaultZone()): ReminderHarness =
            ReminderHarness(app).also { it.begin(clock) }
    }
}
