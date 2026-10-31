package com.fitlens.companion.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fitlens.companion.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Medication reminders (#203, one notification per dose #327). Only the **next** reminder is ever set, as one alarm:
 * when it rings, every dose it is for gets **its own notification** (a tag of dose and due time, so three doses at one
 * minute are three notifications, never one) and the following alarm is set, a chain. The alarm is set again after
 * every change to medications ([Store.refresh] of the body area), on start-up, and after a reboot, a time or time-zone
 * change and an app update ([MedBootReceiver]).
 *
 * Every alarm rings at its scheduled minute (#346): it is an alarm clock set through [ExactAlarms], never an inexact
 * window. Each notification has **Taken**, **Snooze 15 min** and **Skip**, handled by [MedAlarmReceiver] with no screen
 * open, and every button acts on **that dose only**. A dose still open [DeviceSettings.medNudgeMinutes] after it was
 * due is reminded once more. An alarm that rings but can't load the data sets itself again a minute later, so the
 * chain never ends.
 *
 * Catching up is only the backstop for a phone that was switched off, which can't ring (and for an alarm replaced the
 * moment it was due): [DeviceSettings.medRungThrough] is the time up to which first notifications have gone out, so a
 * dose due after it, at most [CATCH_UP_HOURS] hours ago, is notified as soon as the next alarm is set. It is not how
 * lateness is handled: alarms are not late. Names stay off the lock screen unless [DeviceSettings.medNamesOnLockScreen]
 * is on. Everything happens on the phone: no internet.
 *
 * The choice of the next reminder, [next], is plain Kotlin, tested in `MedRemindersTest`; what is shown and pressed is
 * driven through the harness in `MedReminderNotificationsTest`.
 */
object MedReminders {
    const val CHANNEL = "med_reminders"
    const val ACTION_ALARM = "com.fitlens.companion.MED_ALARM"
    const val ACTION_TAKEN = "com.fitlens.companion.MED_TAKEN"
    const val ACTION_SNOOZE = "com.fitlens.companion.MED_SNOOZE"
    const val ACTION_SKIP = "com.fitlens.companion.MED_SKIP"
    /** The due time a reminder or action is for, `yyyy-MM-dd HH:mm`. */
    const val EXTRA_AT = "com.fitlens.companion.MED_AT"
    /** The id of the one dose a notification or button is for (a Long). Missing on a notification the old version posted. */
    const val EXTRA_DOSE = "com.fitlens.companion.MED_DOSE"
    /** On a Taken button: the injection site its label named (`InjectionSite.key`), recorded as it said. */
    const val EXTRA_SITE = "com.fitlens.companion.MED_SITE"
    /** On an alarm: it is the follow-up reminder, not the first one. */
    const val EXTRA_NUDGE = "com.fitlens.companion.MED_NUDGE"
    /** On an alarm: it is a snooze coming back, which brings back only the snoozed doses. */
    const val EXTRA_SNOOZED = "com.fitlens.companion.MED_SNOOZED"
    /** On the notification's tap: open Medications. */
    const val EXTRA_OPEN_MEDICATIONS = "com.fitlens.companion.OPEN_MEDICATIONS"
    const val SNOOZE_MINUTES = 15L
    /** A dose first notified later than this after it was due isn't caught up: it is only on Medications. */
    const val CATCH_UP_HOURS = 12L
    /** The one notification id; each notification is told apart by its tag, [tagOf]. */
    const val NOTIFICATION_ID = 7000
    /** How far ahead a reminder is looked for. A schedule with nothing due in that long is checked again on the next change. */
    private const val LOOKAHEAD_DAYS = 62L
    /** A snooze that is still stored this long after it was due is dropped, whatever became of its dose. */
    private const val SNOOZE_KEEP_DAYS = 2L
    /** How long after an alarm could not finish (the data did not load) it tries again. */
    private const val RETRY_MS = 60_000L
    private val KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val HHMMSS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    /** Tests only: runs between deciding what to store and storing it, to put another receiver's write in between. */
    @Volatile internal var raceHook: (suspend () -> Unit)? = null

    /** Tests only: stands in for loading the data, to make that fail. */
    @Volatile internal var loadOverride: (suspend () -> Snapshot?)? = null

    /** From `App.onCreate`: keeps the application context and sets the next reminder once the data has loaded. */
    fun init(context: Context) {
        app = context.applicationContext
        rearm()
    }

    // ---------- Choosing the next reminder (pure) ----------

    /** A reminder to ring [at] for the doses due at [key]; [nudge] when it's the follow-up, [snoozed] after a snooze. */
    data class Ring(val at: LocalDateTime, val key: String, val nudge: Boolean = false, val snoozed: Boolean = false)

    /** One stored snooze: [doseId] null for one stored by the old version, which means every open dose due at [key]. */
    data class SnoozeEntry(val token: String, val doseId: Long?, val key: String, val at: LocalDateTime)

    /**
     * Snoozed reminders: a token to the time it rings again. The token is "<doseId>@<key>" for one dose, or just
     * "<key>" as the old version stored it (every open dose due then). Stored as "token=yyyy-MM-dd HH:mm;…".
     */
    data class Snoozes(val until: Map<String, String> = emptyMap()) {
        fun encode(): String? = until.entries.joinToString(";") { "${it.key}=${it.value}" }.ifEmpty { null }

        /** [at] for a token as stored: a due key, or "<doseId>@<key>". */
        fun with(token: String, at: LocalDateTime): Snoozes = Snoozes(until + (token to at.format(KEY)))

        /** [at] for one dose due at [key]. */
        fun with(doseId: Long, key: String, at: LocalDateTime): Snoozes = with(tokenOf(doseId, key), at)

        /** Every stored snooze that can be read. */
        fun entries(): List<SnoozeEntry> = until.mapNotNull { (token, v) -> entryOf(token, v) }

        /** Whether the dose [doseId] due at [key] is snoozed. */
        fun covers(doseId: Long, key: String): Boolean = until.containsKey(key) || until.containsKey(tokenOf(doseId, key))

        /** Without [entries] as they were read: one stored again for the same dose with another time since stays. */
        fun without(entries: List<SnoozeEntry>): Snoozes =
            Snoozes(until.filter { (token, v) -> entries.none { it.token == token && parse(v) == it.at } })

        /**
         * Without a snooze whose dose is no longer [isOpen] (given its id, null for an old one, and its due key) and without
         * one still stored [SNOOZE_KEEP_DAYS] after it was due. One whose time has passed stays: it rings, then goes.
         */
        fun pruned(now: LocalDateTime, isOpen: (Long?, String) -> Boolean = { _, _ -> true }): Snoozes =
            Snoozes(until.filter { (token, v) ->
                val e = entryOf(token, v)
                e != null && !e.at.isBefore(now.minusDays(SNOOZE_KEEP_DAYS)) && isOpen(e.doseId, e.key)
            })

        companion object {
            fun tokenOf(doseId: Long, key: String): String = "$doseId@$key"

            private fun entryOf(token: String, value: String): SnoozeEntry? {
                val at = parse(value) ?: return null
                val i = token.indexOf('@')
                if (i < 0) return parse(token)?.let { SnoozeEntry(token, null, token, at) }
                val id = token.substring(0, i).toLongOrNull() ?: return null
                val key = token.substring(i + 1)
                return parse(key)?.let { SnoozeEntry(token, id, key, at) }
            }

            fun decode(text: String?): Snoozes = Snoozes(
                text.orEmpty().split(';').mapNotNull { part ->
                    val bits = part.split('=')
                    val k = bits.getOrNull(0)?.trim().orEmpty()
                    val v = bits.getOrNull(1)?.trim().orEmpty()
                    if (entryOf(k, v) != null) k to v else null
                }.toMap()
            )
        }
    }

    fun parse(key: String): LocalDateTime? = runCatching { LocalDateTime.parse(key, KEY) }.getOrNull()

    /** The tag of the notification for one dose due at [key]: what tells three doses at one minute apart. */
    fun tagOf(doseId: Long, key: String): String = "$doseId@$key"

    /** The doses due at [key] that are neither taken nor skipped. */
    fun openAt(plan: MedPlan, records: List<MRecord>, key: String): List<DueDose> {
        val date = key.take(10)
        return MedSchedules.dueOn(plan, date).filter { d ->
            d.due == key && MedSchedules.takenRecord(records, d) == null && !plan.isSkipped(d.dose.id, d.due)
        }
    }

    /**
     * The open, unsnoozed doses whose first notification is owed: due after [rungThrough] (the time first notifications
     * have gone out up to), not after [now] and no more than [CATCH_UP_HOURS] hours before it. Earliest first.
     */
    fun catchUp(plan: MedPlan, records: List<MRecord>, now: LocalDateTime, rungThrough: LocalDateTime, snoozes: Snoozes = Snoozes()): List<DueDose> {
        val oldest = now.minusHours(CATCH_UP_HOURS)
        val out = ArrayList<DueDose>()
        var day = oldest.toLocalDate()
        while (!day.isAfter(now.toLocalDate())) {
            MedSchedules.dueOn(plan, day.format(Dates.ISO)).forEach { d ->
                val at = parse(d.due) ?: return@forEach
                if (at.isAfter(rungThrough) && !at.isAfter(now) && !at.isBefore(oldest) &&
                    MedSchedules.takenRecord(records, d) == null && !plan.isSkipped(d.dose.id, d.due) &&
                    !snoozes.covers(d.dose.id, d.due)
                ) out += d
            }
            day = day.plusDays(1)
        }
        return out
    }

    /**
     * The open, unsnoozed doses whose follow-up ("Still due") falls after [rungThrough] and not after [now], no more than
     * [CATCH_UP_HOURS] hours after the dose was due: follow-ups that came due since the last ring. Empty with no follow-up
     * ([nudgeMinutes] 0). A ring shows these with the others, so a follow-up and another reminder due in one minute both
     * ring.
     */
    fun followUps(
        plan: MedPlan, records: List<MRecord>, now: LocalDateTime, rungThrough: LocalDateTime, nudgeMinutes: Int,
        snoozes: Snoozes = Snoozes()
    ): List<DueDose> {
        if (nudgeMinutes <= 0) return emptyList()
        val oldest = now.minusHours(CATCH_UP_HOURS)
        val out = ArrayList<DueDose>()
        var day = oldest.toLocalDate()
        while (!day.isAfter(now.toLocalDate())) {
            MedSchedules.dueOn(plan, day.format(Dates.ISO)).forEach { d ->
                val at = parse(d.due) ?: return@forEach
                val again = at.plusMinutes(nudgeMinutes.toLong())
                if (again.isAfter(rungThrough) && !again.isAfter(now) && !at.isBefore(oldest) &&
                    MedSchedules.takenRecord(records, d) == null && !plan.isSkipped(d.dose.id, d.due) &&
                    !snoozes.covers(d.dose.id, d.due)
                ) out += d
            }
            day = day.plusDays(1)
        }
        return out
    }

    /**
     * The next reminder after [now]: the earliest of each open dose time still to come, its follow-up [nudgeMinutes]
     * later (0 for none), and any snooze, or null when nothing is due within [LOOKAHEAD_DAYS]. A dose owed its first
     * notification ([catchUp], when [rungThrough] is known) or a snooze whose time has passed rings at [now]: nothing
     * is passed over. A snoozed dose is left out of its time's ring and follow-up until its snooze rings.
     */
    fun next(
        plan: MedPlan, records: List<MRecord>, now: LocalDateTime, nudgeMinutes: Int,
        snoozes: Snoozes = Snoozes(), rungThrough: LocalDateTime? = null
    ): Ring? {
        if (plan.activations.isEmpty()) return null
        val candidates = ArrayList<Ring>()
        snoozes.entries().forEach { e ->
            val open = openAt(plan, records, e.key).filter { e.doseId == null || it.dose.id == e.doseId }
            if (open.isNotEmpty()) candidates += Ring(if (e.at.isAfter(now)) e.at else now, e.key, snoozed = true)
        }
        val owed = if (rungThrough != null) catchUp(plan, records, now, rungThrough, snoozes) else emptyList()
        owed.firstOrNull()?.let { candidates += Ring(now, it.due) }
        if (rungThrough != null) {
            followUps(plan, records, now, rungThrough, nudgeMinutes, snoozes).firstOrNull()?.let { candidates += Ring(now, it.due, nudge = true) }
        }
        val owedKeys = owed.map { it.due }.toSet()
        // Yesterday too, for a follow-up of a dose due just before midnight.
        var day = now.toLocalDate().minusDays(1)
        val last = now.toLocalDate().plusDays(LOOKAHEAD_DAYS)
        while (!day.isAfter(last)) {
            val date = day.format(Dates.ISO)
            MedSchedules.dueOn(plan, date)
                .filter { MedSchedules.takenRecord(records, it) == null && !plan.isSkipped(it.dose.id, it.due) && !snoozes.covers(it.dose.id, it.due) }
                .map { it.due }
                .distinct()
                .forEach { key ->
                    val at = parse(key) ?: return@forEach
                    if (at.isAfter(now)) candidates += Ring(at, key)
                    else if (nudgeMinutes > 0 && key !in owedKeys) {
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

    /**
     * The one alarm of the chain. Every alarm has this action and request code (extras do not count), so setting it
     * replaces the last one and cancelling finds it, whatever extras it carries.
     */
    private fun chainIntent(context: Context, key: String?, nudge: Boolean = false, snoozed: Boolean = false): PendingIntent =
        PendingIntent.getBroadcast(
            context, 0,
            Intent(context, MedAlarmReceiver::class.java).setAction(ACTION_ALARM).apply {
                if (key != null) putExtra(EXTRA_AT, key).putExtra(EXTRA_NUDGE, nudge).putExtra(EXTRA_SNOOZED, snoozed)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    /**
     * Sets the alarm to ring again a minute from now with the same extras, for an alarm that rang but could not finish
     * (the data did not load, or it timed out), so the chain is never lost. With no [key] it only runs the catch-up and
     * sets the following alarm.
     */
    internal fun retryLater(context: Context, key: String? = null, nudge: Boolean = false, snoozed: Boolean = false) {
        val at = Dates.nowDateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() + RETRY_MS
        ExactAlarms.set(context, at, chainIntent(context, key, nudge, snoozed), ExactAlarms.showMedications(context))
    }

    /** What is stored for the snoozes and the time rung through, worked out from [d] as it is when written. */
    private fun reconciled(d: DeviceSettings, now: LocalDateTime, isOpen: (Long?, String) -> Boolean): DeviceSettings {
        val snoozes = Snoozes.decode(d.medSnoozes).pruned(now, isOpen)
        val stored = parse(d.medRungThrough.orEmpty())
        val minute = now.truncatedTo(ChronoUnit.MINUTES)
        // First run after the update, or reminders off for a while: nothing old rings. The mark never moves backwards.
        val mark = if (stored == null || (!d.medReminders && stored.isBefore(minute))) minute else stored
        return d.copy(medSnoozes = snoozes.encode(), medRungThrough = mark.format(KEY))
    }

    /** Sets the next alarm now, and clears reminders whose doses were all taken or skipped meanwhile. */
    internal suspend fun arm(context: Context) {
        app = app ?: context.applicationContext
        Settings.awaitLoaded()
        val snap = Store.snapshot.value ?: return
        clearStale(context, snap)
        val now = Dates.nowDateTime()
        val records = snap.medicationView.records
        val isOpen: (Long?, String) -> Boolean = { id, key -> openAt(snap.medPlan, records, key).any { id == null || it.dose.id == id } }
        // Decided from what is stored now, but written from what is stored when it is written: a snooze another
        // receiver saved in between is kept (the lambda reads its own argument, not the earlier copy).
        val before = Settings.current()
        if (reconciled(before, now, isOpen) != before) {
            raceHook?.invoke()
            Settings.updateDeviceNow { reconciled(it, now, isOpen) }
        }
        val device = Settings.current()
        val snoozes = Snoozes.decode(device.medSnoozes)
        val mark = parse(device.medRungThrough.orEmpty())
        val ring = if (device.medReminders) next(snap.medPlan, records, now, device.medNudgeMinutes, snoozes, mark) else null
        if (ring == null) {
            ExactAlarms.cancel(context, chainIntent(context, null))
            return
        }
        val at = ring.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        ExactAlarms.set(context, at, chainIntent(context, ring.key, ring.nudge, ring.snoozed), ExactAlarms.showMedications(context))
    }

    // ---------- The notifications ----------

    /** The id the old version gave its one notification per due time, so a press on one it left behind can clear it. */
    private fun legacyNotificationId(key: String): Int = 7000 + (key.hashCode() and 0xFFFF)

    /** A distinct address for one dose's notification, tap or button: dose, due time and what it is. */
    private fun doseUri(doseId: Long, key: String, part: String): Uri = Uri.parse("flexnotes-med://dose/$doseId/${Uri.encode(key)}/$part")

    /**
     * Removes shown reminders with nothing left to take, such as a dose ticked on the Medications screen or the widget.
     * One the old version posted for several doses (it has no [EXTRA_DOSE]) is replaced by one per dose still open.
     */
    private fun clearStale(context: Context, snap: Snapshot) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val records = snap.medicationView.records
        runCatching { nm.activeNotifications }.getOrNull().orEmpty()
            .filter { it.notification.channelId == CHANNEL }
            .forEach { sbn ->
                val extras = sbn.notification.extras
                val key = extras.getString(EXTRA_AT) ?: return@forEach
                val open = openAt(snap.medPlan, records, key)
                if (!extras.containsKey(EXTRA_DOSE)) {
                    nm.cancel(sbn.tag, sbn.id)
                    if (open.isNotEmpty() && Settings.current().medReminders) show(context, snap, open.map { Shown(it, false) })
                } else if (open.none { it.dose.id == extras.getLong(EXTRA_DOSE) }) {
                    nm.cancel(sbn.tag, sbn.id)
                }
            }
    }

    /** Waits for the settings and the data, loading them when the app wasn't running. */
    internal suspend fun ensureLoaded(): Snapshot? {
        loadOverride?.let { return it() }
        Settings.awaitLoaded()
        Store.snapshot.value?.let { return it }
        withTimeoutOrNull(20_000) { Store.open() }
        return Store.snapshot.value
    }

    /** One dose to notify about, and whether it is the follow-up ("Still due") rather than the first reminder. */
    private data class Shown(val dose: DueDose, val again: Boolean)

    /**
     * Posts one notification for each of [doses]: its own medication and amount, its own Taken, Snooze and Skip. One
     * already shown for the same dose and due time is replaced (and alerts again). Nothing is posted without the
     * permission, and the doses still show on Medications.
     */
    private fun show(context: Context, snap: Snapshot, doses: List<Shown>) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (doses.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.mr_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.mr_channel_desc)
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
        )
        val device = Settings.current()
        // Lock Medications (#214): the notification itself names nothing, not only its lock-screen version.
        val locked = device.medLock
        val ordered = doses.sortedWith(compareBy({ it.dose.due }, { it.dose.dose.medication.lowercase() }, { it.dose.dose.id }))
        // An injected dose taken from here goes to the suggested next site (#224). Worked out over every dose shown
        // together, so two doses of one medication name different sites, and each button says its own.
        val sites = sitesFor(snap.medPlan, snap.medicationView.records, ordered.map { it.dose })
        // What the lock screen shows when names are kept private: no medication, no amount.
        val public = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_medication)
            .setColor(AppHooks.accent)
            .setContentTitle(context.getString(R.string.mr_public_title))
            .setContentText(context.getString(R.string.mr_public_text))
            .build()
        val icon = BitmapFactory.decodeResource(context.resources, R.drawable.notification_character)
        ordered.forEachIndexed { i, shown ->
            val d = shown.dose
            val key = d.due
            val site = sites[i]
            val time = Dates.time(context, d.time)
            val title = context.getString(if (shown.again) R.string.mr_title_again else R.string.mr_title, time)
            val text = if (locked) context.getString(R.string.ml_notification_text)
                else "${d.dose.medication} ${fmtNum(d.amount)} ${d.dose.unit}".trim()
            val takeLabel = site?.takeIf { !locked }?.let { context.getString(R.string.mr_taken_site, siteName(context.resources, it)) }
                ?: context.getString(R.string.md_take)
            val tap = PendingIntent.getActivity(
                context, 0,
                AppHooks.openApp(context).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    data = doseUri(d.dose.id, key, "open")
                    putExtra(EXTRA_OPEN_MEDICATIONS, true)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            fun button(kind: String, part: String) = PendingIntent.getBroadcast(
                context, 0,
                Intent(context, MedAlarmReceiver::class.java).setAction(kind)
                    .setData(doseUri(d.dose.id, key, part))
                    .putExtra(EXTRA_AT, key)
                    .putExtra(EXTRA_DOSE, d.dose.id)
                    .apply { if (kind == ACTION_TAKEN && site != null) putExtra(EXTRA_SITE, site.key) },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_medication)
                .setLargeIcon(icon)
                .setColor(AppHooks.accent)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(if (device.medNamesOnLockScreen && !locked) NotificationCompat.VISIBILITY_PUBLIC else NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(public)
                .setContentIntent(tap)
                .setAutoCancel(true)
                // A stable order in the shade: by time, then medication.
                .setSortKey("$key ${d.dose.medication.lowercase()} ${d.dose.id}")
                .addExtras(Bundle().apply {
                    putString(EXTRA_AT, key)
                    putLong(EXTRA_DOSE, d.dose.id)
                })
                .addAction(0, takeLabel, button(ACTION_TAKEN, "taken"))
                .addAction(0, context.getString(R.string.mr_snooze, SNOOZE_MINUTES), button(ACTION_SNOOZE, "snooze"))
                .addAction(0, context.getString(R.string.md_skip), button(ACTION_SKIP, "skip"))
                .build()
            try {
                nm.notify(tagOf(d.dose.id, key), NOTIFICATION_ID, n)
            } catch (e: SecurityException) {
                // Notifications were switched off meanwhile; the doses still show on Medications.
            }
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

    /**
     * Taken, Snooze or Skip from the notification, with no screen open, for the one dose [doseId] due at [key] if it is
     * still open (a press on a dose already taken or skipped records nothing more). [site] is what the Taken button said.
     * With no [doseId], a notification the old version left behind, it acts on every open dose due at [key] as it did.
     */
    internal suspend fun handle(context: Context, action: String, key: String, doseId: Long? = null, site: String? = null) {
        val snap = ensureLoaded() ?: return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (doseId != null) nm?.cancel(tagOf(doseId, key), NOTIFICATION_ID) else nm?.cancel(legacyNotificationId(key))
        val records = snap.medicationView.records
        val open = openAt(snap.medPlan, records, key)
        val targets = if (doseId == null) open else open.filter { it.dose.id == doseId }
        when (action) {
            ACTION_TAKEN -> {
                val today = Dates.today()
                val now = Dates.nowDateTime().toLocalTime().format(HHMMSS)
                val named = if (doseId != null) InjectionSite.of(site) else null
                val computed = sitesFor(snap.medPlan, records, targets)
                targets.forEachIndexed { i, d ->
                    // What the button said; worked out afresh when it didn't carry one, and never for a medication that isn't injected.
                    val chosen = if (named != null && snap.medPlan.infoOf(d.dose.medication).injected) named else computed[i]
                    MedSchedules.take(d, if (d.date == today) now else d.time + ":00", site = chosen)
                }
            }
            ACTION_SKIP -> targets.forEach { MedSchedules.skip(it) }
            ACTION_SNOOZE -> if (targets.isNotEmpty()) {
                val at = Dates.nowDateTime().plusMinutes(SNOOZE_MINUTES)
                Settings.updateDeviceNow { d ->
                    val current = Snoozes.decode(d.medSnoozes)
                    d.copy(medSnoozes = (if (doseId == null) current.with(key, at) else current.with(doseId, key, at)).encode())
                }
            }
        }
        arm(context)
    }

    /**
     * When an alarm rings: every dose it is for gets its notification, and the next alarm is set. That is the open doses
     * due at [key] (unless it is a snooze, [snoozed], which brings back only what was snoozed), every dose owed its first
     * notification ([catchUp]) and every snooze whose time has come, each once. A [nudge] says "Still due" for the doses at
     * [key]; every follow-up that came due ([followUps]) says so too, and everything else is a first reminder. The time
     * rung through moves to now even when nothing could be posted, so a refused permission never rings the same doses
     * again and again. When the data cannot load the alarm is set again a minute later with the same extras.
     */
    internal suspend fun ring(context: Context, key: String?, nudge: Boolean = false, snoozed: Boolean = false) {
        val snap = ensureLoaded()
        if (snap == null) {
            retryLater(context, key, nudge, snoozed)
            return
        }
        val device = Settings.current()
        val now = Dates.nowDateTime()
        val records = snap.medicationView.records
        val plan = snap.medPlan
        val snoozes = Snoozes.decode(device.medSnoozes)
        clearStale(context, snap)
        val shown = LinkedHashMap<Pair<Long, String>, Shown>()
        fun add(d: DueDose, again: Boolean) { shown.putIfAbsent(d.dose.id to d.due, Shown(d, again)) }
        if (key != null && !snoozed) {
            openAt(plan, records, key).filter { !snoozes.covers(it.dose.id, key) }.forEach { add(it, nudge) }
        }
        // With no mark yet (an alarm before the first arm), the doses from this alarm's own time on are still owed.
        val mark = parse(device.medRungThrough.orEmpty()) ?: key?.let { parse(it) }?.minusMinutes(1)
        if (mark != null) {
            catchUp(plan, records, now, mark, snoozes).forEach { add(it, false) }
            followUps(plan, records, now, mark, device.medNudgeMinutes, snoozes).forEach { add(it, true) }
        }
        val rung = snoozes.entries().filter { !it.at.isAfter(now) }
        rung.forEach { e -> openAt(plan, records, e.key).filter { e.doseId == null || it.dose.id == e.doseId }.forEach { add(it, false) } }
        if (device.medReminders) show(context, snap, shown.values.toList())
        val minute = now.truncatedTo(ChronoUnit.MINUTES)
        raceHook?.invoke()
        // From what is stored when it is written: a snooze saved meanwhile stays, and the mark never goes backwards.
        Settings.updateDeviceNow { d ->
            val old = parse(d.medRungThrough.orEmpty())
            d.copy(
                medSnoozes = Snoozes.decode(d.medSnoozes).without(rung).encode(),
                medRungThrough = (if (old != null && old.isAfter(minute)) old else minute).format(KEY)
            )
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
        val doseId = if (intent.hasExtra(MedReminders.EXTRA_DOSE)) intent.getLongExtra(MedReminders.EXTRA_DOSE, -1L).takeIf { it > 0L } else null
        val site = intent.getStringExtra(MedReminders.EXTRA_SITE)
        val nudge = intent.getBooleanExtra(MedReminders.EXTRA_NUDGE, false)
        val snoozed = intent.getBooleanExtra(MedReminders.EXTRA_SNOOZED, false)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val finished = withTimeoutOrNull(25_000) {
                    when (action) {
                        MedReminders.ACTION_ALARM -> MedReminders.ring(context, key, nudge, snoozed)
                        MedReminders.ACTION_TAKEN, MedReminders.ACTION_SNOOZE, MedReminders.ACTION_SKIP ->
                            if (key != null) MedReminders.handle(context, action, key, doseId, site)
                    }
                    true
                }
                // An alarm that timed out before it finished must not end the chain.
                if (finished == null && action == MedReminders.ACTION_ALARM) MedReminders.retryLater(context, key, nudge, snoozed)
            } finally {
                result.finish()
            }
        }
    }
}

/** Sets the next reminder again after a reboot, a time or time-zone change, or an app update (#203), the logging ones too (#219). */
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
                val finished = withTimeoutOrNull(25_000) {
                    // The logging reminders (#219) are set again with them, and need no data.
                    LogReminders.arm(context)
                    // With no data the alarm is not left unset: it rings in a minute and tries again.
                    if (MedReminders.ensureLoaded() == null) MedReminders.retryLater(context) else MedReminders.arm(context)
                    true
                }
                if (finished == null) MedReminders.retryLater(context)
            } finally {
                result.finish()
            }
        }
    }
}
