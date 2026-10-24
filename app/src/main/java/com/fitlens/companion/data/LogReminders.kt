package com.fitlens.companion.data

import android.Manifest
import android.app.AlarmManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

/** One logging reminder: on or off, its time (`HH:mm`) and the ISO weekdays it rings on (Monday = 1). */
data class LogReminder(val kind: LogKind, val on: Boolean, val time: String, val days: Set<Int>) {
    val localTime: LocalTime get() = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.of(8, 0))

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
 * Every logging reminder, kept in [DeviceSettings.logReminders] as "weigh=1|07:30|1,2,3;photos=0|08:00|7". A kind
 * that isn't stored, or can't be read, has its [LogReminder.default].
 */
data class LogReminderSet(val items: Map<LogKind, LogReminder> = emptyMap()) {
    fun of(kind: LogKind): LogReminder = items[kind] ?: LogReminder.default(kind)

    val all: List<LogReminder> get() = LogKind.entries.map { of(it) }

    fun with(r: LogReminder): LogReminderSet = LogReminderSet(items + (r.kind to r))

    fun encode(): String? = items.values.sortedBy { it.kind.ordinal }
        .joinToString(";") { "${it.kind.key}=${if (it.on) 1 else 0}|${it.time}|${it.days.sorted().joinToString(",")}" }
        .ifEmpty { null }

    companion object {
        fun decode(text: String?): LogReminderSet = LogReminderSet(
            text.orEmpty().split(';').mapNotNull { part ->
                val kind = LogKind.of(part.substringBefore('=', "").trim()) ?: return@mapNotNull null
                val bits = part.substringAfter('=').split('|')
                val time = bits.getOrNull(1)?.trim()?.takeIf { t -> runCatching { LocalTime.parse(t) }.isSuccess }
                    ?: return@mapNotNull null
                val days = bits.getOrNull(2).orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..7 }.toSet()
                kind to LogReminder(kind, bits.getOrNull(0)?.trim() == "1", time, days)
            }.toMap()
        )
    }
}

/**
 * Reminders for other logging (#219): weigh-ins, measurements, progress photos and workout days, each with its own
 * time and weekdays. Built like [MedReminders]: only the **next** reminder is one alarm, set again after it rings,
 * after a change on Settings › Reminders, on start-up, and after a reboot, a time change or an app update
 * ([MedBootReceiver]). A reminder for something already logged that day (a weight, a photo, a workout) stays quiet.
 * Tapping it opens the screen where that is logged. Everything happens on the phone: no internet.
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
    private const val WINDOW_MS = 10 * 60_000L
    private val KEY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null

    /** From `App.onCreate`: keeps the application context and sets the next reminder. */
    fun init(context: Context) {
        app = context.applicationContext
        rearm()
    }

    // ---------- Choosing the next reminder (pure) ----------

    /** A reminder to ring [at] for [kinds] (several when their times match). */
    data class Ring(val at: LocalDateTime, val kinds: List<LogKind>) {
        val key: String get() = at.format(KEY)
    }

    /** The next time after [now] that a switched-on reminder rings, with every kind due then, or null when none is on. */
    fun next(set: LogReminderSet, now: LocalDateTime): Ring? {
        val on = set.all.filter { it.on && it.days.isNotEmpty() }
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

    /** When [r] next rings after [now]: today if its time is still to come, else the next of its weekdays. */
    fun nextFor(r: LogReminder, now: LocalDateTime): LocalDateTime? {
        val time = r.localTime
        var day: LocalDate = now.toLocalDate()
        repeat(8) {
            val at = day.atTime(time)
            if (day.dayOfWeek.value in r.days && at.isAfter(now)) return at
            day = day.plusDays(1)
        }
        return null
    }

    /** The switched-on kinds due at [key] (`yyyy-MM-dd HH:mm`): their weekday and time match. */
    fun dueAt(set: LogReminderSet, key: String): List<LogKind> {
        val at = runCatching { LocalDateTime.parse(key, KEY) }.getOrNull() ?: return emptyList()
        return set.all.filter { it.on && at.dayOfWeek.value in it.days && it.localTime == at.toLocalTime() }.map { it.kind }
    }

    /** Whether [kind] was already logged on [date], so its reminder stays quiet. Measurements always remind. */
    fun alreadyLogged(snap: Snapshot, kind: LogKind, date: String): Boolean = when (kind) {
        LogKind.WEIGH_IN -> weightName(snap)?.let { n -> snap.recordsByName[n].orEmpty().any { it.date.take(10) == date } } == true
        LogKind.PHOTOS -> snap.photos.any { it.date == date }
        LogKind.WORKOUT -> snap.setsByDate[date].orEmpty().isNotEmpty()
        LogKind.MEASUREMENTS -> false
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

    internal suspend fun arm(context: Context) {
        app = app ?: context.applicationContext
        Settings.awaitLoaded()
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val ring = next(LogReminderSet.decode(Settings.current().logReminders), LocalDateTime.now())
        val intent = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, LogAlarmReceiver::class.java).setAction(ACTION_ALARM).apply { ring?.let { putExtra(EXTRA_AT, it.key) } },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        if (ring == null) {
            am.cancel(intent)
            return
        }
        val at = ring.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        try {
            if (MedReminders.canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            else am.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, intent)
        } catch (e: SecurityException) {
            am.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, intent)
        }
    }

    // ---------- The notification ----------

    private fun notificationId(kind: LogKind): Int = 7400 + kind.ordinal

    /** When the alarm rings: each kind due at [key] and not yet logged today is shown, and the next alarm is set. */
    internal suspend fun ring(context: Context, key: String?) {
        if (key != null) {
            val set = LogReminderSet.decode(Settings.current().logReminders)
            val due = dueAt(set, key)
            if (due.isNotEmpty()) {
                val snap = MedReminders.ensureLoaded()
                val date = key.take(10)
                due.filter { snap == null || !alreadyLogged(snap, it, date) }.forEach { show(context, snap, it, date) }
            }
        }
        arm(context)
    }

    private fun show(context: Context, snap: Snapshot?, kind: LogKind, date: String) {
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
            LogKind.MEASUREMENTS -> R.string.lr_measure_title to context.getString(R.string.lr_measure_text)
            LogKind.PHOTOS -> R.string.lr_photos_title to context.getString(R.string.lr_photos_text)
            LogKind.WORKOUT -> R.string.lr_workout_title to (
                snap?.let { TodayPlan.workout(it, Settings.currentPortable(), date) }?.takeIf { !it.logged }
                    ?.let { w -> context.getString(R.string.lr_workout_named, w.day.name, w.routine.name) }
                    ?: context.getString(R.string.lr_workout_text)
                )
        }
        val tap = PendingIntent.getActivity(
            context, notificationId(kind),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_OPEN_LOG, kind.key)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setLargeIcon(BitmapFactory.decodeResource(context.resources, R.drawable.notification_character))
            .setColor(Brand.Gold.toArgb())
            .setContentTitle(context.getString(title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tap)
            .setAutoCancel(true)
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
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeoutOrNull(25_000) {
                    Settings.awaitLoaded()
                    LogReminders.ring(context, key)
                }
            } finally {
                result.finish()
            }
        }
    }
}
