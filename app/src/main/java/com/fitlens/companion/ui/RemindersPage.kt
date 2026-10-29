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
import com.fitlens.companion.data.MedReminders
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
    var exact by remember { mutableStateOf(MedReminders.canExact(ctx)) }
    LifecycleResumeEffect(Unit) {
        exact = MedReminders.canExact(ctx)
        onPauseOrDispose { }
    }
    val set = LogReminderSet.decode(device.logReminders)
    val why = stringResource(R.string.lr_notify_why)
    var timeFor by remember { mutableStateOf<LogKind?>(null) }
    var daysFor by remember { mutableStateOf<LogKind?>(null) }

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
            stringResource(R.string.lr_time),
            value = Dates.time(ctx, r.time),
            enabled = r.on,
            disabledReason = stringResource(R.string.lr_needs_on),
            sheetOpen = timeFor == r.kind
        ) { timeFor = r.kind }
        SettingsActionRow(
            stringResource(R.string.lr_days),
            value = daysText(ctx.resources, r.days, prefs.weekStart),
            enabled = r.on,
            disabledReason = stringResource(R.string.lr_needs_on),
            warning = if (r.on && r.days.isEmpty()) stringResource(R.string.lr_no_days) else null,
            sheetOpen = daysFor == r.kind
        ) { daysFor = r.kind }
    }
    val anyOn = set.all.any { it.on }
    NotificationsOffNote(notify, anyOn)
    if (Build.VERSION.SDK_INT >= 31 && anyOn && !exact) {
        SettingsActionRow(
            stringResource(R.string.mr_exact),
            stringResource(R.string.mr_exact_summary),
            value = stringResource(R.string.mr_exact_not_allowed),
            warning = stringResource(R.string.mr_exact_warning)
        ) {
            val intent = Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                ctx.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                openAppSettings(ctx)
            }
        }
    }
    SettingsNote(stringResource(R.string.lr_quiet_note))
    SettingsNote(stringResource(R.string.lr_local_note))

    timeFor?.let { kind ->
        val r = set.of(kind)
        ClockDialog(
            title = stringResource(switchTitle(kind)),
            hour = r.localTime.hour, minute = r.localTime.minute,
            onDismiss = { timeFor = null }
        ) { picked ->
            // ASCII digits whatever the language, so the stored `HH:mm` always reads back.
            save(r.copy(time = String.format(Locale.ROOT, "%02d:%02d", picked.hour, picked.minute)))
            timeFor = null
        }
    }
    daysFor?.let { kind ->
        val r = set.of(kind)
        var days by remember(kind) { mutableStateOf(r.days) }
        FitSheet(
            title = stringResource(R.string.lr_days_title, stringResource(switchTitle(kind))),
            onDismiss = { daysFor = null },
            confirmLabel = stringResource(R.string.wk_set),
            confirmEnabled = days.isNotEmpty(),
            onConfirm = {
                save(r.copy(days = days))
                daysFor = null
            }
        ) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                MedSchedules.weekOrder(prefs.weekStart).forEach { d ->
                    FitFilterChip(
                        selected = d in days,
                        onClick = { days = if (d in days) days - d else days + d },
                        label = { Text(MedSchedules.weekdayShort(d)) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                FitTextButton(onClick = { days = (1..7).toSet() }) { Text(stringResource(R.string.lr_every_day)) }
                FitTextButton(onClick = { days = (1..5).toSet() }) { Text(stringResource(R.string.lr_weekdays)) }
            }
        }
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

private fun switchSummary(kind: LogKind): Int = when (kind) {
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
    return if (!r.on) res.getString(R.string.settings_value_off) else "${Dates.time(ctx, r.time)} · ${daysText(res, r.days, weekStart)}"
}
