@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.LogKind
import com.fitlens.companion.data.LogReminder
import com.fitlens.companion.data.LogReminderSet
import com.fitlens.companion.data.LogReminders
import com.fitlens.companion.data.ExactAlarms
import com.fitlens.companion.data.MedSchedules
import com.fitlens.companion.data.Settings
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNote
import com.fitlens.companion.ui.design.SettingsSwitchRow
import java.util.Locale
import com.fitlens.companion.ui.design.FitFilterChip
import com.fitlens.companion.ui.design.FitTextButton

/**
 * Settings › Reminders (#219): a reminder to weigh in, take measurements, take progress photos and train, each
 * switched on with its own time and weekdays (asking for notifications when one is switched on). Each change sets
 * the next reminder again. Medication reminders keep their own page.
 */
@Composable
internal fun RemindersPage() {
    val ctx = LocalContext.current
    val device by Settings.device.collectAsState()
    val prefs by Settings.portable.collectAsState()
    val notify = rememberNotificationAccess()
    val set = LogReminderSet.decode(device.logReminders)
    val why = stringResource(R.string.lr_notify_why)
    // The reminder whose schedule sheet is open (#332), by its key, kept when the phone is turned.
    var schedFor by rememberSaveable { mutableStateOf<String?>(null) }

    fun save(r: LogReminder) {
        Settings.updateDevice { it.copy(logReminders = LogReminderSet.decode(it.logReminders).with(r).encode()) }
        LogReminders.rearm()
    }

    set.all.forEach { r ->
        SettingsGroup(stringResource(groupTitle(r.kind)))
        SettingsSwitchRow(stringResource(switchTitle(r.kind)), r.on, stringResource(switchSummary(r.kind))) { on ->
            save(r.copy(on = on))
            if (on) notify.ask(why)
        }
        SettingsActionRow(
            stringResource(R.string.lr_schedule),
            value = scheduleText(ctx, r, prefs.weekStart),
            warning = if (r.on && !r.schedulable) stringResource(R.string.lr_no_days) else null,
            sheetOpen = schedFor == r.kind.key
        ) { schedFor = r.kind.key }
    }
    val anyOn = set.all.any { it.on }
    NotificationsOffNote(notify, anyOn)
    ExactAlarmNote(anyOn)
    SettingsNote(stringResource(R.string.lr_quiet_note))
    SettingsNote(stringResource(R.string.lr_local_note))

    schedFor?.let { kind -> ReminderScheduleSheet(kind) { schedFor = null } }
}
}

private fun groupTitle(kind: LogKind): Int = when (kind) {
    LogKind.WEIGH_IN -> R.string.lr_group_weigh
    LogKind.MEASUREMENTS -> R.string.lr_group_measure
    LogKind.PHOTOS -> R.string.lr_group_photos
    LogKind.WORKOUT -> R.string.lr_group_workout
}

internal fun switchTitle(kind: LogKind): Int = when (kind) {
    LogKind.WEIGH_IN -> R.string.lr_weigh
    LogKind.MEASUREMENTS -> R.string.lr_measure
    LogKind.PHOTOS -> R.string.lr_photos
    LogKind.WORKOUT -> R.string.lr_workout
}

internal fun switchSummary(kind: LogKind): Int = when (kind) {
    LogKind.WEIGH_IN -> R.string.lr_weigh_summary
    LogKind.MEASUREMENTS -> R.string.lr_measure_summary
    LogKind.PHOTOS -> R.string.lr_photos_summary
    LogKind.WORKOUT -> R.string.lr_workout_summary
}

/** "Every day", "Weekdays", "Weekends", or the days in the week's order: "Mon, Wed, Fri". */
internal fun daysText(res: Resources, days: Set<Int>, weekStart: Int): String = when (days) {
    emptySet<Int>() -> res.getString(R.string.lr_no_days_value)
    (1..7).toSet() -> res.getString(R.string.lr_every_day)
    (1..5).toSet() -> res.getString(R.string.lr_weekdays)
    setOf(6, 7) -> res.getString(R.string.lr_weekends)
    else -> MedSchedules.weekOrder(weekStart).filter { it in days }.joinToString(", ") { MedSchedules.weekdayShort(it) }
}

/** The search entries' value for [kind]: "07:30 · Every day", or Off. */
internal fun reminderValue(ctx: Context, res: Resources, set: LogReminderSet, kind: LogKind, weekStart: Int): String {
    val r = set.of(kind)
    return if (!r.on) res.getString(R.string.settings_value_off) else scheduleText(ctx, r, weekStart)
}
