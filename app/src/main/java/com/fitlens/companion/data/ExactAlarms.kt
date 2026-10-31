package com.fitlens.companion.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The one place an alarm is set (#346): medication reminders, snoozes, follow-ups, logging reminders and the rest
 * timer all go through [set]. An alarm must ring at its scheduled minute, so it is an alarm clock
 * (`AlarmManager.setAlarmClock`): exact, wakes the phone from doze and isn't rate-limited. The price is Android's alarm
 * icon in the status bar while one is set, and the next alarm's time (no names) on some lock screens.
 *
 * Android 13 and later grant `USE_EXACT_ALARM` at install, with no prompt. On Android 12 and 12L the user can revoke
 * `SCHEDULE_EXACT_ALARM`; then [set] falls back to an inexact alarm that may be late, and Settings says so.
 * Nothing else under `app/src/main` may call `setWindow`, `setExact*`, `setAndAllowWhileIdle` or `setAlarmClock`
 * (a test reads the sources to be sure).
 */
object ExactAlarms {
    /** Android 12 and 12L send this when the user allows or revokes exact alarms: the alarms already set are set again. */
    const val ACTION_PERMISSION_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

    /**
     * The epoch time of [local] on the phone. When clocks go back, a local time such as 02:30 happens twice, and the
     * default is the earlier of the two. If that instant has already passed ([nowMillis], by `Dates.clockSource`) while the
     * clock is in the second pass, the alarm would fire at once and repeat until 02:30 comes round again; so the later
     * offset is used, the 02:30 that is still to come.
     */
    fun epochMillis(local: LocalDateTime, zone: ZoneId = ZoneId.systemDefault(), nowMillis: Long = Dates.clockSource.millis()): Long {
        val earlier = local.atZone(zone)
        val earlierMs = earlier.toInstant().toEpochMilli()
        if (earlierMs > nowMillis) return earlierMs
        return maxOf(earlierMs, earlier.withLaterOffsetAtOverlap().toInstant().toEpochMilli())
    }

    /** How long after attempt number [attempt] (1 for the first) could not finish the alarm rings again: 1, 2, 4, 8, then every 15 minutes. */
    fun retryDelayMs(attempt: Int): Long = when {
        attempt <= 1 -> 60_000L
        attempt == 2 -> 120_000L
        attempt == 3 -> 240_000L
        attempt == 4 -> 480_000L
        else -> 900_000L
    }

    /** When the retry after attempt [attempt] rings, on the phone's clock. */
    fun retryAt(attempt: Int): Long = epochMillis(Dates.nowDateTime().plusSeconds(retryDelayMs(attempt) / 1000))

    /** Whether Android lets FlexNotes ring at the exact minute: always before Android 12, and from Android 13 (granted at install). */
    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    /** Sets (or replaces: same [operation] identity) an alarm for [atMillis]; [show] is what tapping the alarm icon opens. */
    fun set(context: Context, atMillis: Long, operation: PendingIntent, show: PendingIntent) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), operation)
        } catch (e: SecurityException) {
            // Android 12 or 12L with the exact-alarm permission revoked: the best that is left, and Settings says so.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    /** Cancels the alarm set with [operation]. */
    fun cancel(context: Context, operation: PendingIntent) {
        context.getSystemService(AlarmManager::class.java)?.cancel(operation)
    }

    /** What the alarm icon opens for a dose: Medications. Its own address, so it never replaces a notification's tap. */
    fun showMedications(context: Context): PendingIntent = show(context, "medications", true)

    /** What the alarm icon opens for a logging reminder or a rest: the app. [part] keeps each its own intent. */
    fun showApp(context: Context, part: String): PendingIntent = show(context, part, false)

    private fun show(context: Context, part: String, medications: Boolean): PendingIntent = PendingIntent.getActivity(
        context, 0,
        AppHooks.openApp(context).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            data = Uri.parse("flexnotes-alarm://show/$part")
            if (medications) putExtra(MedReminders.EXTRA_OPEN_MEDICATIONS, true)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
