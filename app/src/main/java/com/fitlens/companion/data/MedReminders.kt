package com.fitlens.companion.data

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fitlens.companion.R
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.MainActivity
import com.fitlens.companion.ui.siteName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Medication reminders (#203). Only the **next** reminder is ever set, as one alarm: when it rings, its doses are
 * shown in one notification (doses due at the same time share it) and the following alarm is set, a chain. The alarm
 * is set again after every change to medications ([Store.refresh] of the body area), on start-up, and after a
 * reboot, a time or time-zone change and an app update ([MedBootReceiver]).
 *
 * Alarms are exact (`setExactAndAllowWhileIdle`) when Android allows it, otherwise within a 10-minute window. The
 * notification has **Taken**, **Snooze 15 min** and **Skip**, handled by [MedAlarmReceiver] with no screen open. A
 * dose still open [DeviceSettings.medNudgeMinutes] after it was due is reminded once more. Names stay off the lock
 * screen unless [DeviceSettings.medNamesOnLockScreen] is on. Everything happens on the phone: no internet.
 *
 * The choice of the next reminder, [next], is plain Kotlin, tested in `MedRemindersTest`.
 */
object MedReminders {
    const val CHANNEL = "med_reminders"
    const val ACTION_ALARM = "com.fitlens.companion.MED_ALARM"
    const val ACTION_TAKEN = "com.fitlens.companion.MED_TAKEN"
    const val ACTION_SNOOZE = "com.fitlens.companion.MED_SNOOZE"
    const val ACTION_SKIP = "com.fitlens.companion.MED_SKIP"
    /** The due time a reminder or action is for, `yyyy-MM-dd HH:mm`. */
    const val EXTRA_AT = "com.fitlens.companion.MED_AT"
    /** On the notification's tap: open Medications. */
    const val EXTRA_OPEN_MEDICATIONS = "com.fitlens.companion.OPEN_MEDICATIONS"
    const val SNOOZE_MINUTES = 15L
    /** How far ahead a reminder is looked for. A schedule with nothing due in that long is checked again on the next change. */
    private const val LOOKAHEAD_DAYS = 62L
    private const val WINDOW_MS = 10 * 60_000L
    private val KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    /** From `App.onCreate`: keeps the application context and sets the next reminder once the data has loaded. */
    fun init(context: Context) {
        app = context.applicationContext
        rearm()
    }

    // ---------- Choosing the next reminder (pure) ----------

    /** A reminder to ring [at] for the doses due at [key]; [nudge] when it's the follow-up, [snoozed] after a snooze. */
    data class Ring(val at: LocalDateTime, val key: String, val nudge: Boolean = false, val snoozed: Boolean = false)

    /** Snoozed reminders: a due key to the time it rings again. Stored as "key=yyyy-MM-dd HH:mm;…". */
    data class Snoozes(val until: Map<String, String> = emptyMap()) {
        fun encode(): String? = until.entries.joinToString(";") { "${it.key}=${it.value}" }.ifEmpty { null }

        fun with(key: String, at: LocalDateTime): Snoozes = Snoozes(until + (key to at.format(KEY)))

        /** Without those that have rung before [now]. */
        fun pruned(now: LocalDateTime): Snoozes = Snoozes(until.filterValues { v -> parse(v)?.isAfter(now) == true })

        companion object {
            fun decode(text: String?): Snoozes = Snoozes(
                text.orEmpty().split(';').mapNotNull { part ->
                    val bits = part.split('=')
                    val k = bits.getOrNull(0)?.trim().orEmpty()
                    val v = bits.getOrNull(1)?.trim().orEmpty()
                    if (parse(k) != null && parse(v) != null) k to v else null
                }.toMap()
            )
        }
    }

    fun parse(key: String): LocalDateTime? = runCatching { LocalDateTime.parse(key, KEY) }.getOrNull()

    /** The doses due at [key] that are neither taken nor skipped. */
    fun openAt(plan: MedPlan, records: List<MRecord>, key: String): List<DueDose> {
        val date = key.take(10)
        return MedSchedules.dueOn(plan, date).filter { d ->
            d.due == key && MedSchedules.takenRecord(records, d) == null && !plan.isSkipped(d.dose.id, d.due)
        }
    }

    /**
     * The next reminder after [now]: the earliest of each open dose time still to come, its follow-up [nudgeMinutes]
     * later (0 for none), and any snooze, or null when nothing is due within [LOOKAHEAD_DAYS].
     */
    fun next(plan: MedPlan, records: List<MRecord>, now: LocalDateTime, nudgeMinutes: Int, snoozes: Snoozes = Snoozes()): Ring? {
        if (plan.activations.isEmpty()) return null
        val candidates = ArrayList<Ring>()
        snoozes.until.forEach { (key, v) ->
            val at = parse(v) ?: return@forEach
            if (at.isAfter(now) && openAt(plan, records, key).isNotEmpty()) candidates += Ring(at, key, snoozed = true)
        }
        // Yesterday too, for a follow-up of a dose due just before midnight.
        var day = now.toLocalDate().minusDays(1)
        val last = now.toLocalDate().plusDays(LOOKAHEAD_DAYS)
        while (!day.isAfter(last)) {
            val date = day.format(Dates.ISO)
            MedSchedules.dueOn(plan, date)
                .filter { MedSchedules.takenRecord(records, it) == null && !plan.isSkipped(it.dose.id, it.due) }
                .map { it.due }
                .distinct()
                .forEach { key ->
                    val at = parse(key) ?: return@forEach
                    if (snoozes.until.containsKey(key)) return@forEach
                    if (at.isAfter(now)) candidates += Ring(at, key)
                    else if (nudgeMinutes > 0) {
                        val nudge = at.plusMinutes(nudgeMinutes.toLong())
                        if (nudge.isAfter(now)) candidates += Ring(nudge, key, nudge = true)
                    }
                }
            // The earliest ring today or tomorrow can't be beaten by a later day.
            if (candidates.isNotEmpty() && day.isAfter(now.toLocalDate())) break
            day = day.plusDays(1)
        }
        return candidates.minByOrNull { it.at }
    }

    // ---------- Setting the alarm ----------

    /** Sets the next reminder, a moment after the latest change so a burst of writes sets it once. */
    fun rearm() {
        val context = app ?: return
        pending?.cancel()
        pending = scope.launch {
            delay(300)
            runCatching { arm(context) }
        }
    }

    /** Whether Android lets FlexNotes ring at the exact minute (Android 12 and later ask the user). */
    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, MedAlarmReceiver::class.java).setAction(ACTION_ALARM),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Sets the next alarm now, and clears reminders whose doses were all taken or skipped meanwhile. */
    internal suspend fun arm(context: Context) {
        app = app ?: context.applicationContext
        Settings.awaitLoaded()
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val device = Settings.current()
        val snap = Store.snapshot.value ?: return
        clearStale(context, snap)
        val now = LocalDateTime.now()
        val snoozes = Snoozes.decode(device.medSnoozes).pruned(now)
        if (snoozes.encode() != device.medSnoozes) Settings.updateDeviceNow { it.copy(medSnoozes = snoozes.encode()) }
        val ring = if (device.medReminders) next(snap.medPlan, snap.medicationView.records, now, device.medNudgeMinutes, snoozes) else null
        val intent = alarmIntent(context)
        if (ring == null) {
            am.cancel(intent)
            return
        }
        val at = ring.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val withKey = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, MedAlarmReceiver::class.java).setAction(ACTION_ALARM).putExtra(EXTRA_AT, ring.key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        try {
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, withKey)
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, withKey)
        } catch (e: SecurityException) {
            // Exact alarms were withdrawn between the check and the call.
            am.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, withKey)
        }
    }

    // ---------- The notification ----------

    private fun notificationId(key: String): Int = 7000 + (key.hashCode() and 0xFFFF)

    /** Removes shown reminders with nothing left to take, such as a dose ticked on the Medications screen. */
    private fun clearStale(context: Context, snap: Snapshot) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.activeNotifications }.getOrNull().orEmpty()
            .filter { it.notification.channelId == CHANNEL }
            .forEach { sbn ->
                val key = sbn.notification.extras.getString(EXTRA_AT) ?: return@forEach
                if (openAt(snap.medPlan, snap.medicationView.records, key).isEmpty()) nm.cancel(sbn.id)
            }
    }

    /** Waits for the settings and the data, loading them when the app wasn't running. */
    internal suspend fun ensureLoaded(): Snapshot? {
        Settings.awaitLoaded()
        Store.snapshot.value?.let { return it }
        withTimeoutOrNull(20_000) { Store.open() }
        return Store.snapshot.value
    }

    /** Shows the reminder for the open doses due at [key], or clears it when none are left. */
    internal fun show(context: Context, snap: Snapshot, key: String, nudge: Boolean) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val open = openAt(snap.medPlan, snap.medicationView.records, key)
        if (open.isEmpty()) {
            nm.cancel(notificationId(key))
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.mr_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.mr_channel_desc)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
        val time = key.drop(11)
        val device = Settings.current()
        // Lock Medications (#214): the notification itself names nothing, not only its lock-screen version.
        val locked = device.medLock
        val title = context.getString(if (nudge) R.string.mr_title_again else R.string.mr_title, time)
        val text = if (locked) context.getString(R.string.ml_notification_text)
            else open.joinToString(", ") { "${it.dose.medication} ${fmtNum(it.amount)} ${it.dose.unit}".trim() }
        // An injected dose taken from here goes to the suggested next site (#224), named on the button when there's one.
        val sites = sitesFor(snap.medPlan, snap.medicationView.records, open).filterNotNull()
        val takeLabel = sites.singleOrNull()?.takeIf { !locked }?.let { context.getString(R.string.mr_taken_site, siteName(context.resources, it)) }
            ?: context.getString(if (sites.isEmpty()) R.string.md_take else R.string.mr_taken_sites)
        val tap = PendingIntent.getActivity(
            context, notificationId(key),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_MEDICATIONS, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun action(action: String, n: Int) = PendingIntent.getBroadcast(
            context, notificationId(key) * 4 + n,
            Intent(context, MedAlarmReceiver::class.java).setAction(action).putExtra(EXTRA_AT, key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        // What the lock screen shows when names are kept private: no medication, no amount.
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_medication)
            .setColor(Brand.Gold.toArgb())
            .setContentTitle(context.getString(R.string.mr_public_title))
            .setContentText(context.getString(R.string.mr_public_text))
            .build()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_medication)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.notification_character))
            .setColor(Brand.Gold.toArgb())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(if (device.medNamesOnLockScreen && !locked) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .addExtras(android.os.Bundle().apply { putString(EXTRA_AT, key) })
            .addAction(0, takeLabel, action(ACTION_TAKEN, 1))
            .addAction(0, context.getString(R.string.mr_snooze, SNOOZE_MINUTES), action(ACTION_SNOOZE, 2))
            .addAction(0, context.getString(R.string.md_skip), action(ACTION_SKIP, 3))
            .build()
        try {
            nm.notify(notificationId(key), n)
        } catch (e: SecurityException) {
            // Notifications were switched off meanwhile; the doses still show on Medications.
        }
    }

    /**
     * The site each of [open] goes to when taken from the notification (#224), index for index: null for a medication
     * that isn't injected, else the suggested next site, so two doses of one medication at once get different sites.
     * The user can change a site afterwards from the medication's History.
     */
    fun sitesFor(plan: MedPlan, records: List<MRecord>, open: List<DueDose>): List<InjectionSite?> {
        val used = records.toMutableList()
        return open.map { d ->
            if (!plan.infoOf(d.dose.medication).injected) return@map null
            val site = InjectionSites.suggest(used, d.dose.medication)
            // Counted as used just now, after every real value, so the next dose of it moves on.
            used += MRecord(0L, d.dose.medication, d.dose.unit, "9999-12-31", "23:59:59", 0.0, null, "", site = site.key)
            site
        }
    }

    /** Taken, Snooze or Skip from the notification, with no screen open. */
    internal suspend fun handle(context: Context, action: String, key: String) {
        val snap = ensureLoaded() ?: return
        context.getSystemService(NotificationManager::class.java)?.cancel(notificationId(key))
        val open = openAt(snap.medPlan, snap.medicationView.records, key)
        when (action) {
            ACTION_TAKEN -> {
                val today = Dates.today()
                val now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
                val sites = sitesFor(snap.medPlan, snap.medicationView.records, open)
                open.forEachIndexed { i, d -> MedSchedules.take(d, if (d.date == today) now else d.time + ":00", site = sites[i]) }
            }
            ACTION_SKIP -> open.forEach { MedSchedules.skip(it) }
            ACTION_SNOOZE -> {
                val at = LocalDateTime.now().plusMinutes(SNOOZE_MINUTES)
                Settings.updateDeviceNow { it.copy(medSnoozes = Snoozes.decode(it.medSnoozes).with(key, at).encode()) }
            }
        }
        arm(context)
    }

    /** When an alarm rings: the snooze for [key] is used up, the reminder shows, and the next alarm is set. */
    internal suspend fun ring(context: Context, key: String?) {
        val snap = ensureLoaded() ?: return
        if (key != null) {
            val device = Settings.current()
            val snoozes = Snoozes.decode(device.medSnoozes)
            val snoozed = snoozes.until.containsKey(key)
            if (snoozed) Settings.updateDeviceNow { it.copy(medSnoozes = Snoozes(snoozes.until - key).encode()) }
            val due = parse(key)
            val nudge = !snoozed && due != null && LocalDateTime.now().isAfter(due.plusMinutes(1))
            if (device.medReminders) show(context, snap, key, nudge)
        }
        arm(context)
    }
}

/** The alarm and the notification's buttons (#203). Explicit intents only, so it isn't exported. */
class MedAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        val key = intent.getStringExtra(MedReminders.EXTRA_AT)
        val action = intent.action
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(25_000) {
                    when (action) {
                        MedReminders.ACTION_ALARM -> MedReminders.ring(context, key)
                        MedReminders.ACTION_TAKEN, MedReminders.ACTION_SNOOZE, MedReminders.ACTION_SKIP ->
                            if (key != null) MedReminders.handle(context, action, key)
                    }
                }
            } finally {
                result.finish()
            }
        }
    }
}

/** Sets the next reminder again after a reboot, a time or time-zone change, or an app update (#203). */
class MedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(25_000) {
                    MedReminders.ensureLoaded()
                    MedReminders.arm(context)
                }
            } finally {
                result.finish()
            }
        }
    }
}
