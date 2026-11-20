package com.fitlens.companion.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.fitlens.companion.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** How a logging reminder repeats (#332). [key] is stored, so it never changes. */
enum class LogPattern(val key: String) {
    /** On the chosen weekdays, every week (the only pattern before #332). */
    WEEKDAYS("w"),
    /** Every [LogReminder.every] days, counted from the start date. */
    EVERY_DAYS("d"),
    /** On the chosen weekdays every [LogReminder.every] weeks; the first week is the 7 days from the start date. */
    EVERY_WEEKS("k"),
    /** On [LogReminder.monthDay] of each month, or the month's last day when the month is shorter. */
    MONTH_DAY("m");

    companion object {
        fun of(key: String?): LogPattern = entries.firstOrNull { it.key == key } ?: WEEKDAYS
    }
}

/** What a logging reminder (#219) is for. [key] is stored, so it never changes. */
enum class LogKind(val key: String) {
    WEIGH_IN("weigh"),
    MEASUREMENTS("measure"),
    PHOTOS("photos"),
    WORKOUT("workout");

    companion object {
        fun of(key: String?): LogKind? = entries.firstOrNull { it.key == key }
    }
}

/**
 * One logging reminder: on or off, its time (`HH:mm`), its ISO weekdays (Monday = 1) and, since #332, how it repeats,
 * from which date ([start], `yyyy-MM-dd`, null for no limit), [quiet], and for measurements the ones it is for.
 */
data class LogReminder(
    val kind: LogKind,
    val on: Boolean,
    val time: String,
    val days: Set<Int>,
    val pattern: LogPattern = LogPattern.WEEKDAYS,
    val every: Int = 2,
    val start: String? = null,
    val monthDay: Int = 1,
    val quiet: Boolean = true,
    val measures: List<String> = emptyList()
) {
    val localTime: LocalTime get() = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(8, 0))

    /** [start] as a date, or null when there is none or it can't be read. */
    val startDate: LocalDate? get() = start?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /**
     * Whether it can ring at all: the two weekday patterns need at least one weekday, and the two counting ones a start
     * date to count from (never 1970, #332).
     */
    val schedulable: Boolean get() = when (pattern) {
        LogPattern.WEEKDAYS -> days.isNotEmpty()
        LogPattern.EVERY_WEEKS -> days.isNotEmpty() && startDate != null
        LogPattern.EVERY_DAYS -> startDate != null
        LogPattern.MONTH_DAY -> true
    }

    companion object {
        /** Off, at a time and on days that suit most people, ready to switch on. */
        fun default(kind: LogKind): LogReminder = when (kind) {
            LogKind.WEIGH_IN -> LogReminder(kind, false, "07:30", (1..7).toSet())
            LogKind.MEASUREMENTS -> LogReminder(kind, false, "08:00", setOf(7))
            LogKind.PHOTOS -> LogReminder(kind, false, "08:00", setOf(7))
            LogKind.WORKOUT -> LogReminder(kind, false, "17:30", setOf(1, 3, 5))
        }
    }
}

/**
 * Every logging reminder, kept in [DeviceSettings.logReminders] as "weigh=1|07:30|1,2,3;photos=0|08:00|7". Since #332
 * each part goes on with its pattern, every, start, day of month and quiet ("photos=1|08:00|7|k|2|2026-11-08|1|1"), and
 * [armedFor] is one more part, "armed=2026-11-08 08:00": the time the alarm now set is for. Parts stored by earlier
 * versions read back with the defaults, so they ring as before. A kind that isn't stored, or can't be read, has its
 * [LogReminder.default].
 */
data class LogReminderSet(val items: Map<LogKind, LogReminder> = emptyMap(), val armedFor: String? = null) {
    fun of(kind: LogKind): LogReminder = items[kind] ?: LogReminder.default(kind)

    val all: List<LogReminder> get() = LogKind.entries.map { of(it) }

    fun with(r: LogReminder): LogReminderSet = copy(items = items + (r.kind to r))

    fun encode(): String? = (
        items.values.sortedBy { it.kind.ordinal }.map { r ->
            "${r.kind.key}=${if (r.on) 1 else 0}|${r.time}|${r.days.sorted().joinToString(",")}" +
                "|${r.pattern.key}|${r.every}|${r.start.orEmpty()}|${r.monthDay}|${if (r.quiet) 1 else 0}" +
                // The picked measurements, each URL-encoded so a name may hold any character; left out when none.
                (if (r.measures.isEmpty()) "" else "|" + r.measures.joinToString(",") { m -> URLEncoder.encode(m, "UTF-8") })
        } + listOfNotNull(armedFor?.let { "$ARMED=$it" })
        ).joinToString(";").ifEmpty { null }

    companion object {
        fun decode(text: String?): LogReminderSet = LogReminderSet(
            text.orEmpty().split(';').mapNotNull { part ->
                val kind = LogKind.of(part.substringBefore('=', "").trim()) ?: return@mapNotNull null
                val bits = part.substringAfter('=').split('|')
                val time = bits.getOrNull(1)?.trim()?.takeIf { t -> runCatching { LocalTime.parse(t) }.isSuccess }
                    ?: return@mapNotNull null
                val days = bits.getOrNull(2).orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
                val pattern = LogPattern.of(bits.getOrNull(3)?.trim())
                kind to LogReminder(
                    kind, bits.getOrNull(0)?.trim() == "1", time, days,
                    pattern = pattern,
                    // Up to 52 weeks or 365 days: inside what nextFor looks ahead, so it always rings.
                    every = bits.getOrNull(4)?.trim()?.toIntOrNull()?.coerceIn(1, if (pattern == LogPattern.EVERY_WEEKS) 52 else 365) ?: 2,
                    start = bits.getOrNull(5)?.trim()?.takeIf { s -> runCatching { LocalDate.parse(s) }.isSuccess },
                    monthDay = bits.getOrNull(6)?.trim()?.toIntOrNull()?.coerceIn(1, 31) ?: 1,
                    quiet = bits.getOrNull(7)?.trim() != "0",
                    // A name that can't be decoded is dropped; the rest are kept.
                    measures = bits.getOrNull(8).orEmpty().split(',').mapNotNull { m ->
                        runCatching { URLDecoder.decode(m.trim(), "UTF-8") }.getOrNull()?.trim()?.ifEmpty { null }
                    }.distinct()
                )
            }.toMap(),
            armedIn(text)
        )

        private const val ARMED = "armed"

        /** The `armed=` part of [text], the time the alarm set last is for, or null. */
        private fun armedIn(text: String?): String? = text.orEmpty().split(';')
            .firstOrNull { it.substringBefore('=', "").trim() == ARMED }
            ?.substringAfter('=')?.trim()?.ifEmpty { null }
    }
}

/**
 * Reminders for other logging (#219): weigh-ins, measurements, progress photos and workout days, each with its own
 * time and, since #332, how it repeats (weekdays, every few days or weeks, or a day of the month). Built like
 * [MedReminders]: only the **next** reminder is one alarm, set again after it rings, after a change on Settings ›
 * Reminders, on start-up, and after a reboot, a time change or an app update ([MedBootReceiver]). One missed while
 * the phone was off is shown when it comes back, the same day ([owed]). A reminder can stay quiet on a day that
 * already has what it's for ([LogReminder.quiet]). Tapping it opens the screen where that is logged. Everything
 * happens on the phone: no internet.
 *
 * The choice of the next reminder, [next], is plain Kotlin, tested in `LogRemindersTest`.
 */
object LogReminders {
    const val CHANNEL = "log_reminders"
    const val ACTION_ALARM = "com.fitlens.companion.LOG_ALARM"
    /** The due time a reminder is for, `yyyy-MM-dd HH:mm`. */
    const val EXTRA_AT = "com.fitlens.companion.LOG_AT"
    /** On the notification's tap: the [LogKind.key] whose screen opens. */
    const val EXTRA_OPEN_LOG = "com.fitlens.companion.OPEN_LOG"
    /** On a retry alarm: how many times in a row the alarm could not finish, for the back-off ([ExactAlarms.retryDelayMs]). */
    const val EXTRA_ATTEMPT = "com.fitlens.companion.LOG_ATTEMPT"

    /** How long a receiver works before it gives up and sets a retry. A seam so a test can make the work overrun. */
    @Volatile internal var workTimeoutMs = 25_000L

    /** Tests only: work run inside the receiver's time limit, to make it overrun. */
    @Volatile internal var overrun: (suspend () -> Unit)? = null

    /** Tests only: work run when the alarm is set, to make the boot's logging step overrun. */
    @Volatile internal var armOverrun: (suspend () -> Unit)? = null

    /** Tests only: whether no debounced re-arm is waiting or running. */
    internal fun rearmIdle(): Boolean = pending?.isActive != true

    /** Tests only: puts every seam back and forgets the application, so one test does not leak into the next. */
    internal fun resetForTests() {
        pending?.cancel()
        pending = null
        app = null
        workTimeoutMs = 25_000L
        overrun = null
        armOverrun = null
    }
    private val KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null
    /** One arm or ring at a time, so a catch-up and an alarm ringing together show a reminder once (#332). */
    private val lock = Mutex()

    /** From `App.onCreate`: keeps the application context and sets the next reminder. */
    fun init(context: Context) {
        app = context.applicationContext
        rearm()
    }

    // ---------- Choosing the next reminder (pure) ----------

    /** How many days ahead [nextFor] looks: past the longest gap a pattern can have (every 52 weeks, every 365 days). */
    private const val SCAN_DAYS = 400

    /** Whether [r] rings on [day], its time aside: on or after its start, and a day its pattern picks (#332). */
    fun ringsOn(r: LogReminder, day: LocalDate): Boolean {
        val start = r.startDate
        if (start != null && day.isBefore(start)) return false
        // Counted from the start date. The counting patterns can't ring without one ([LogReminder.schedulable]); the
        // weekday and month patterns don't count, so 1 January 1970 only stands in for theirs.
        val from = ChronoUnit.DAYS.between(start ?: LocalDate.of(1970, 1, 1), day)
        // Up to 52 weeks or 365 days, as when it is read back, so it always rings inside what nextFor looks ahead.
        val every = r.every.coerceIn(1, if (r.pattern == LogPattern.EVERY_WEEKS) 52 else 365).toLong()
        return when (r.pattern) {
            LogPattern.WEEKDAYS -> day.dayOfWeek.value in r.days
            LogPattern.EVERY_DAYS -> from % every == 0L
            LogPattern.EVERY_WEEKS -> day.dayOfWeek.value in r.days && (from / 7) % every == 0L
            LogPattern.MONTH_DAY -> day.dayOfMonth == minOf(r.monthDay, day.lengthOfMonth())
        }
    }

    /** A reminder to ring [at] for [kinds] (several when their times match). */
    data class Ring(val at: LocalDateTime, val kinds: List<LogKind>) {
        val key: String get() = at.format(KEY)
    }

    /** The next time after [now] that a switched-on reminder rings, with every kind due then, or null when none is on. */
    fun next(set: LogReminderSet, now: LocalDateTime): Ring? {
        val on = set.all.filter { it.on && it.schedulable }
        if (on.isEmpty()) return null
        var best: LocalDateTime? = null
        val kinds = ArrayList<LogKind>()
        for (r in on) {
            val at = nextFor(r, now) ?: continue
            val b = best
            if (b == null || at.isBefore(b)) {
                best = at
                kinds.clear()
                kinds += r.kind
            } else if (at == b) kinds += r.kind
        }
        return best?.let { Ring(it, kinds) }
    }

    /** When [r] next rings after [now]: the first day from today (or from its start, if later) that [ringsOn] picks, at its time. */
    fun nextFor(r: LogReminder, now: LocalDateTime): LocalDateTime? {
        if (!r.schedulable) return null
        val time = r.localTime
        val today = now.toLocalDate()
        var day: LocalDate = r.startDate?.takeIf { it.isAfter(today) } ?: today
        repeat(SCAN_DAYS) {
            val at = day.atTime(time)
            if (ringsOn(r, day) && at.isAfter(now)) return at
            day = day.plusDays(1)
        }
        return null
    }

    /** The next [count] times [r] rings after [now], each from [nextFor], so what the schedule sheet shows is what rings. */
    fun upcoming(r: LogReminder, now: LocalDateTime, count: Int = 3): List<LocalDateTime> {
        val out = ArrayList<LocalDateTime>()
        var from = now
        while (out.size < count) {
            val at = nextFor(r, from) ?: break
            out += at
            from = at
        }
        return out
    }

    /** The switched-on kinds due at [key] (`yyyy-MM-dd HH:mm`): their time matches and [ringsOn] picks that day. */
    fun dueAt(set: LogReminderSet, key: String): List<LogKind> {
        val at = runCatching { LocalDateTime.parse(key, KEY) }.getOrNull() ?: return emptyList()
        return set.all.filter { it.on && it.schedulable && it.localTime == at.toLocalTime() && ringsOn(it, at.toLocalDate()) }.map { it.kind }
    }

    /** How old a reminder whose alarm never rang may be and still be shown late (#332). */
    private const val OWED_HOURS = 12L

    /**
     * The reminders owed because their alarm never rang (the phone was off, #332): every ring from
     * [LogReminderSet.armedFor] up to [now], today only and less than 12 hours old, after [rang] (the alarm ringing now),
     * oldest first. A kind rings once a day, so each kind is in one of them at most, at its latest time. Empty when none.
     */
    fun owed(set: LogReminderSet, now: LocalDateTime, rang: String? = null): List<Ring> {
        val armed = set.armedFor?.let { runCatching { LocalDateTime.parse(it, KEY) }.getOrNull() } ?: return emptyList()
        var from = armed.minusMinutes(1)
        var oldest = now.minusHours(OWED_HOURS)
        // Never one from yesterday: shown now, it would say "today" about another day (#332).
        val today = now.toLocalDate().atStartOfDay().minusMinutes(1)
        if (oldest.isBefore(today)) oldest = today
        if (from.isBefore(oldest)) from = oldest
        val rangAt = rang?.let { runCatching { LocalDateTime.parse(it, KEY) }.getOrNull() }
        if (rangAt != null && !rangAt.isBefore(from)) from = rangAt
        val rings = ArrayList<Ring>()
        while (true) {
            val ring = next(set, from) ?: break
            if (ring.at.isAfter(now)) break
            rings += ring
            from = ring.at
        }
        return rings
    }

    /**
     * Whether [r]'s day [date] is already logged, so it can stay quiet ([LogReminder.quiet]). A measurements reminder
     * is logged only when it is for at least one measurement and every one of them has a value that day (#332).
     */
    fun isLogged(snap: Snapshot, r: LogReminder, date: String): Boolean =
        if (r.kind != LogKind.MEASUREMENTS) alreadyLogged(snap, r.kind, date)
        else measuresFor(snap, r).isNotEmpty() && measuresLeft(snap, r, date).isEmpty()

    /** Whether [kind] was already logged on [date]. Measurements answer false: [isLogged] counts the ones picked. */
    fun alreadyLogged(snap: Snapshot, kind: LogKind, date: String): Boolean = when (kind) {
        LogKind.WEIGH_IN -> weightName(snap)?.let { n -> snap.recordsByName[n].orEmpty().any { it.date.take(10) == date } } == true
        LogKind.PHOTOS -> snap.photos.any { it.date == date }
        LogKind.WORKOUT -> snap.setsByDate[date].orEmpty().isNotEmpty()
        LogKind.MEASUREMENTS -> false
    }

    /**
     * The measurements a measurements reminder can be for (#332): the Body tracker's own, in its order (lab markers
     * last), other than the weight, which has its own reminder. Medications are never among them.
     */
    fun measureChoices(snap: Snapshot): List<String> {
        val weight = weightName(snap)
        return snap.allMeasurements.filter { it.enabled && it.name != weight }.sortedBy { it.lab }.map { it.name }
    }

    /**
     * The measurements [r] is for, as the Body tracker names them now, in its order. A pick it no longer shows
     * (deleted, renamed, switched off or made a medication) is left out while it isn't shown (#332).
     */
    fun measuresFor(snap: Snapshot, r: LogReminder): List<String> =
        measureChoices(snap).filter { n -> r.measures.any { it.equals(n, ignoreCase = true) } }

    /** [r]'s measurements with no value on [date], typed in or read from Health Connect: both count (owner, 2026-11-19). */
    fun measuresLeft(snap: Snapshot, r: LogReminder, date: String): List<String> = measuresFor(snap, r).filter { n ->
        snap.recordsByName[n].orEmpty().none { it.date.take(10) == date }
    }

    /** The body value that holds the user's weight ("Bodyweight", or FitNotes's "Body Weight"), if there's one. */
    fun weightName(snap: Snapshot): String? = snap.allMeasurements.map { it.name }.firstOrNull {
        it.equals(HealthConnect.Names.BODYWEIGHT, true) || it.equals("Body Weight", true)
    }

    // ---------- Setting the alarm ----------

    /** Sets the next reminder, a moment after the latest change so a burst of changes sets it once. */
    fun rearm() {
        val context = app ?: return
        pending?.cancel()
        pending = scope.launch {
            delay(300)
            runCatching { arm(context) }
        }
    }

    internal suspend fun arm(context: Context) = lock.withLock { armLocked(context, null) }

    /**
     * Sets the next alarm first, so a slow step after it never leaves none set; then shows each reminder owed after the
     * phone was off ([owed]); then notes what the alarm is for. Noted last, so one still owed when showing it fails or
     * is cut short is owed again next time (#332).
     */
    private suspend fun armLocked(context: Context, rang: String?) {
        app = app ?: context.applicationContext
        armOverrun?.invoke()
        Settings.awaitLoaded()
        val set = LogReminderSet.decode(Settings.current().logReminders)
        val now = Dates.nowDateTime()
        val ring = next(set, now)
        if (ring == null) {
            ExactAlarms.cancel(context, alarmIntent(context, null))
        } else {
            // At its exact minute (#346), through the one helper that sets alarms (which also settles a time that happens twice).
            ExactAlarms.set(context, ExactAlarms.epochMillis(ring.at), alarmIntent(context, ring.key), ExactAlarms.showApp(context, "log"))
        }
        owed(set, now, rang).forEach { showDue(context, set, it.key) }
        if (set.armedFor != ring?.key) saveArmed(ring?.key)
    }

    /**
     * Notes [key] as the time the alarm is for. Kept in memory even when it can't be saved, so a failed write never
     * stops the reminders; tried twice, so a passing failure doesn't leave the old time on disk to be owed again after
     * a restart (#332).
     */
    private suspend fun saveArmed(key: String?) {
        repeat(2) {
            try {
                Settings.updateDeviceNow { d -> d.copy(logReminders = LogReminderSet.decode(d.logReminders).copy(armedFor = key).encode()) }
                return
            } catch (e: java.io.IOException) {
            }
        }
    }

    /** The one logging alarm. Same action and request code whatever the key, so setting it replaces and cancelling finds it. */
    private fun alarmIntent(context: Context, key: String?, attempt: Int = 0): PendingIntent = PendingIntent.getBroadcast(
        context, 0,
        Intent(context, LogAlarmReceiver::class.java).setAction(ACTION_ALARM).apply {
            key?.let { putExtra(EXTRA_AT, it) }
            if (attempt > 0) putExtra(EXTRA_ATTEMPT, attempt)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /**
     * For an alarm that rang but could not finish: rings again later with the same key (1, 2, 4, 8 minutes, then every 15,
     * by [attempt]), so the chain is never lost and a failing phone is not woken every minute for ever.
     */
    internal fun retryLater(context: Context, key: String?, attempt: Int = 1) {
        ExactAlarms.set(context, ExactAlarms.retryAt(attempt), alarmIntent(context, key, attempt), ExactAlarms.showApp(context, "log"))
    }

    // ---------- The notification ----------

    private fun notificationId(kind: LogKind): Int = 7400 + kind.ordinal

    /** When the alarm rings: each kind due at [key] is shown (unless a catch-up already showed it), and the next alarm is set. */
    internal suspend fun ring(context: Context, key: String?) = lock.withLock {
        val set = LogReminderSet.decode(Settings.current().logReminders)
        // A key before the time now armed was already shown, by a catch-up after the phone was off.
        val armed = set.armedFor
        if (key != null && (armed == null || key >= armed)) showDue(context, set, key)
        armLocked(context, key)
    }

    /** Shows each kind due at [key], leaving out one that stays quiet ([LogReminder.quiet]) on a day already logged. */
    private suspend fun showDue(context: Context, set: LogReminderSet, key: String) {
        val due = dueAt(set, key)
        if (due.isEmpty()) return
        val snap = MedReminders.ensureLoaded()
        val date = key.take(10)
        due.map { set.of(it) }.filter { r -> snap == null || !r.quiet || !isLogged(snap, r, date) }.forEach { r ->
            // A measurements reminder names the ones it is for that are still to log that day.
            show(context, snap, r.kind, date, snap?.let { measuresLeft(it, r, date) }.orEmpty())
        }
    }

    private fun show(context: Context, snap: Snapshot?, kind: LogKind, date: String, measures: List<String> = emptyList()) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.lr_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.lr_channel_desc)
            }
        )
        val (title, text) = when (kind) {
            LogKind.WEIGH_IN -> R.string.lr_weigh_title to context.getString(R.string.lr_weigh_text)
            LogKind.MEASUREMENTS -> R.string.lr_measure_title to (
                if (measures.isEmpty()) context.getString(R.string.lr_measure_text)
                else context.getString(R.string.lr_measure_named, measures.joinToString(", "))
                )
            LogKind.PHOTOS -> R.string.lr_photos_title to context.getString(R.string.lr_photos_text)
            LogKind.WORKOUT -> R.string.lr_workout_title to (
                snap?.let { TodayPlan.workout(it, Settings.currentPortable(), date) }?.takeIf { !it.logged }
                    ?.let { w -> context.getString(R.string.lr_workout_named, w.day.name, w.routine.name) }
                    ?: context.getString(R.string.lr_workout_text)
                )
        }
        val tap = PendingIntent.getActivity(
            context, notificationId(kind),
            AppHooks.openApp(context).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_LOG, kind.key)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.notification_character))
            .setColor(AppHooks.accent)
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tap)
            .setAutoCancel(true)
            // Measurements named on it (lab markers among them) stay off the lock screen, which shows the general words (#332).
            .apply {
                if (measures.isNotEmpty()) {
                    setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    setPublicVersion(
                        NotificationCompat.Builder(context, CHANNEL)
                            .setSmallIcon(R.drawable.ic_stat_reminder)
                            .setColor(AppHooks.accent)
                            .setContentTitle(context.getString(R.string.lr_measure_title))
                            .setContentText(context.getString(R.string.lr_measure_text))
                            .build()
                    )
                }
            }
            .build()
        try {
            nm.notify(notificationId(kind), n)
        } catch (e: SecurityException) {
            // Notifications were switched off meanwhile.
        }
    }
}

/** The logging reminders' alarm (#219). Explicit intents only, so it isn't exported. */
class LogAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != LogReminders.ACTION_ALARM) return
        val result = goAsync()
        val key = intent.getStringExtra(LogReminders.EXTRA_AT)
        val attempt = intent.getIntExtra(LogReminders.EXTRA_ATTEMPT, 0)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val finished = try {
                    withTimeoutOrNull(LogReminders.workTimeoutMs) {
                        LogReminders.overrun?.invoke()
                        Settings.awaitLoaded()
                        LogReminders.ring(context, key)
                        true
                    }
                } catch (e: Exception) {
                    // A failure, even a cancellation from inside the work (the receiver's own scope is never cancelled),
                    // must not end the chain or close the app.
                    null
                }
                // It needs no data, but a settings load that never finishes, or a failure, must not end the chain either.
                if (finished == null) LogReminders.retryLater(context, key, attempt + 1)
            } finally {
                result.finish()
            }
        }
    }
}
