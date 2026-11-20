@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.fitlens.companion.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.ExactAlarms
import com.fitlens.companion.data.LogKind
import com.fitlens.companion.data.LogPattern
import com.fitlens.companion.data.LogReminder
import com.fitlens.companion.data.LogReminderSet
import com.fitlens.companion.data.LogReminders
import com.fitlens.companion.data.MedSchedules
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.ui.design.FitFilterChip
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.FitTextButton
import com.fitlens.companion.ui.design.SectionLabel
import java.util.Locale

/** The one logging reminder schedule sheet (#332), from Settings › Reminders, Photos and the Body tracker. */
@Composable
internal fun ReminderScheduleSheet(kind: LogKind, snap: Snapshot? = Store.snapshot.value, onDismiss: () -> Unit) {
    val device by Settings.device.collectAsState()
    val prefs by Settings.portable.collectAsState()
    // The draft is one reminder's stored text, so turning the phone keeps every choice (#381).
    var draftText by rememberSaveable(kind) {
        mutableStateOf(LogReminderSet().with(LogReminderSet.decode(device.logReminders).of(kind)).encode().orEmpty())
    }
    val draft = LogReminderSet.decode(draftText).of(kind)
    val dirty = rememberDirty(draftText)
    val change: (LogReminder) -> Unit = { r -> draftText = LogReminderSet().with(r).encode().orEmpty() }
    // The dialog that is open, "time" or "start", kept when the phone is turned.
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    FitSheet(
        title = stringResource(switchTitle(kind)),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.lr_save),
        onConfirm = {
            Settings.updateDevice { it.copy(logReminders = LogReminderSet.decode(it.logReminders).with(draft).encode()) }
            LogReminders.rearm()
            onDismiss()
        },
        confirmEnabled = !draft.on || draft.schedulable,
        dirty = dirty
    ) {
        ScheduleFields(kind, draft, prefs.weekStart, snap, change) { picking = it }
    }
    picking?.let { which -> ScheduleDialog(which, draft, change) { picking = null } }
}

/** The dialog [which] the sheet opened: "time" (the clock) or "start" (the date picker). Both change the draft only. */
@Composable
private fun ScheduleDialog(which: String, draft: LogReminder, change: (LogReminder) -> Unit, onClose: () -> Unit) {
    when (which) {
        "time" -> ClockDialog(
            title = stringResource(R.string.lr_time),
            hour = draft.localTime.hour,
            minute = draft.localTime.minute,
            onDismiss = onClose
        ) { t ->
            // ASCII digits whatever the language, so the stored `HH:mm` always reads back.
            change(draft.copy(time = String.format(Locale.ROOT, "%02d:%02d", t.hour, t.minute)))
            onClose()
        }
        "start" -> PickDateDialog(draft.start ?: Dates.today(), onDismiss = onClose) { d -> change(draft.copy(start = d)) }
    }
}

/** The sheet's rows: on or off, the time, how it repeats, the start date, staying quiet, and the next dates it rings. */
@Composable
private fun ScheduleFields(kind: LogKind, draft: LogReminder, weekStart: Int, snap: Snapshot?, change: (LogReminder) -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    val notify = rememberNotificationAccess()
    val why = stringResource(R.string.lr_notify_why)
    SettingsSwitchRow(stringResource(R.string.lr_sched_on), draft.on, stringResource(switchSummary(kind))) { on ->
        change(draft.copy(on = on))
        if (on) notify.ask(why)
    }
    NotificationsOffNote(notify, draft.on)
    SettingsActionRow(stringResource(R.string.lr_time), value = Dates.time(ctx, draft.time)) { onPick("time") }
    RepeatChoice(draft, weekStart, change)
    SettingsActionRow(stringResource(R.string.lr_start), value = Dates.day(draft.start ?: Dates.today())) { onPick("start") }
    QuietRows(kind, draft, snap, change)
    val next = LogReminders.upcoming(draft, Dates.nowDateTime())
    when {
        !draft.schedulable -> SettingsNote(stringResource(R.string.lr_no_days), error = true)
        !draft.on -> Unit
        next.isEmpty() -> SettingsNote(stringResource(R.string.lr_next_none))
        else -> SettingsNote(stringResource(R.string.lr_next, next.joinToString(", ") { Dates.day(it.toLocalDate().format(Dates.ISO)) }))
    }
    ExactAlarmNote(draft.on)
}

/** How the reminder repeats: the four patterns as chips, then what each needs (weekdays, how often, the day of the month). */
@Composable
private fun RepeatChoice(draft: LogReminder, weekStart: Int, change: (LogReminder) -> Unit) {
    SectionLabel(stringResource(R.string.lr_repeat))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        LogPattern.entries.forEach { p ->
            FitFilterChip(
                selected = draft.pattern == p,
                onClick = { change(switchedTo(draft, p)) },
                label = { Text(stringResource(patternLabel(p))) }
            )
        }
    }
    when (draft.pattern) {
        LogPattern.WEEKDAYS -> WeekdayChips(draft, weekStart, change, presets = true)
        LogPattern.EVERY_WEEKS -> {
            EveryStepper(stringResource(R.string.lr_every_weeks_label), draft.every, 52) { change(draft.copy(every = it)) }
            WeekdayChips(draft, weekStart, change, presets = false)
        }
        LogPattern.EVERY_DAYS -> EveryStepper(stringResource(R.string.lr_every_days_label), draft.every, 365) { change(draft.copy(every = it)) }
        LogPattern.MONTH_DAY -> {
            EveryStepper(stringResource(R.string.lr_month_day_label), draft.monthDay, 31) { change(draft.copy(monthDay = it)) }
            if (draft.monthDay > 28) SettingsNote(stringResource(R.string.lr_month_short_note))
        }
    }
}

/** [draft] moved to pattern [p]: it gets a start date (today) if it has none, and a new monthly one takes that date's day. */
private fun switchedTo(draft: LogReminder, p: LogPattern): LogReminder {
    val start = draft.start ?: Dates.today()
    val day = Dates.parse(start)?.dayOfMonth ?: 1
    return draft.copy(
        pattern = p,
        start = if (p == LogPattern.WEEKDAYS) draft.start else start,
        monthDay = if (p == LogPattern.MONTH_DAY && draft.pattern != LogPattern.MONTH_DAY) day else draft.monthDay,
        every = if (p == LogPattern.EVERY_WEEKS) draft.every.coerceAtMost(52) else draft.every
    )
}

/** The weekdays as chips in the week's order, with Every day and Weekdays when [presets] (the weekly pattern). */
@Composable
private fun WeekdayChips(draft: LogReminder, weekStart: Int, change: (LogReminder) -> Unit, presets: Boolean) {
    val days = draft.days
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        MedSchedules.weekOrder(weekStart).forEach { d ->
            FitFilterChip(
                selected = d in days,
                onClick = { change(draft.copy(days = if (d in days) days - d else days + d)) },
                label = { Text(MedSchedules.weekdayShort(d)) }
            )
        }
    }
    if (presets) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            FitTextButton(onClick = { change(draft.copy(days = (1..7).toSet())) }) { Text(stringResource(R.string.lr_every_day)) }
            FitTextButton(onClick = { change(draft.copy(days = (1..5).toSet())) }) { Text(stringResource(R.string.lr_weekdays)) }
        }
    }
}

/** A whole number from 1 to [max] with − and + (how many days or weeks, or the day of the month). */
@Composable
private fun EveryStepper(label: String, value: Int, max: Int, onValue: (Int) -> Unit) {
    StepperField(
        label = label,
        value = value.toString(),
        onValue = { t -> t.filter { it.isDigit() }.take(3).toIntOrNull()?.let { onValue(it.coerceIn(1, max)) } },
        onStep = { step -> onValue((value + step).coerceIn(1, max)) },
        keyboard = KeyboardType.Number
    )
}

/**
 * "Reminders on the minute" (#346): on Android 12 a phone can refuse exact alarms, and then a reminder can't ring on
 * time. Shown while [on] and they aren't allowed; tapping it asks Android. Checked again whenever the screen comes back.
 */
@Composable
internal fun ExactAlarmNote(on: Boolean) {
    val ctx = LocalContext.current
    var exact by remember { mutableStateOf(ExactAlarms.canExact(ctx)) }
    LifecycleResumeEffect(Unit) {
        exact = ExactAlarms.canExact(ctx)
        onPauseOrDispose { }
    }
    if (Build.VERSION.SDK_INT < 31 || !on || exact) return
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
    ExactAlarmNote(draft.on)
}

/**
 * Staying quiet on a day already logged (#332). A measurements reminder first lists the measurements it can be for:
 * only those picked count, so with none picked it has nothing to count and can't stay quiet.
 */
@Composable
private fun QuietRows(kind: LogKind, draft: LogReminder, snap: Snapshot?, change: (LogReminder) -> Unit) {
    val picks = snap?.let { LogReminders.measuresFor(it, draft) }.orEmpty()
    if (kind == LogKind.MEASUREMENTS) {
        val choices = snap?.let { LogReminders.measureChoices(it) }.orEmpty()
        MeasurePicks(choices, picks) { change(draft.copy(measures = it)) }
    }
    val canQuiet = kind != LogKind.MEASUREMENTS || picks.isNotEmpty()
    SettingsSwitchRow(
        stringResource(R.string.lr_quiet),
        draft.quiet && canQuiet,
        stringResource(quietSummary(kind)),
        enabled = canQuiet,
        disabledReason = stringResource(R.string.lr_quiet_measure_none)
    ) { change(draft.copy(quiet = it)) }
}

/** The measurements a measurements reminder is for: chips in the Body tracker's order, and the picks in plain words. */
@Composable
private fun MeasurePicks(choices: List<String>, picks: List<String>, onPicks: (List<String>) -> Unit) {
    SectionLabel(stringResource(R.string.lr_measures_label))
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        choices.forEach { name ->
            FitFilterChip(
                selected = name in picks,
                // Built from the picks still shown, so one deleted or renamed meanwhile is dropped on the next change.
                onClick = { onPicks(if (name in picks) picks - name else picks + name) },
                label = { Text(name) }
            )
        }
    }
    SettingsNote(
        when {
            choices.isEmpty() -> stringResource(R.string.lr_measures_no_choices)
            picks.isEmpty() -> stringResource(R.string.lr_measures_none)
            else -> stringResource(R.string.lr_measures_picked, picks.joinToString(", "))
        }
    )
}

private fun patternLabel(p: LogPattern): Int = when (p) {
    LogPattern.WEEKDAYS -> R.string.lr_pat_weekdays
    LogPattern.EVERY_DAYS -> R.string.lr_pat_days
    LogPattern.EVERY_WEEKS -> R.string.lr_pat_weeks
    LogPattern.MONTH_DAY -> R.string.lr_pat_month
}

private fun quietSummary(kind: LogKind): Int = when (kind) {
    LogKind.WEIGH_IN -> R.string.lr_quiet_weigh
    LogKind.MEASUREMENTS -> R.string.lr_quiet_measure
    LogKind.PHOTOS -> R.string.lr_quiet_photos
    LogKind.WORKOUT -> R.string.lr_quiet_workout
}

/**
 * A reminder's schedule in a few words, as its row shows it: "08:00 · Every 2 weeks on Sun", and for a measurements
 * reminder the ones it is for, "08:00 · Monthly, day 31 · Waist, Chest" ([snap] gives their current names).
 */
internal fun scheduleText(ctx: Context, r: LogReminder, weekStart: Int, snap: Snapshot? = Store.snapshot.value): String {
    val res = ctx.resources
    val repeats = when (r.pattern) {
        LogPattern.WEEKDAYS -> daysText(res, r.days, weekStart)
        LogPattern.EVERY_DAYS -> res.getQuantityString(R.plurals.lr_sum_days, r.every, r.every)
        LogPattern.EVERY_WEEKS -> res.getQuantityString(R.plurals.lr_sum_weeks, r.every, r.every, daysText(res, r.days, weekStart))
        LogPattern.MONTH_DAY -> res.getString(R.string.lr_sum_month, r.monthDay)
    }
    val picks = snap?.let { LogReminders.measuresFor(it, r) }.orEmpty()
    return "${Dates.time(ctx, r.time)} · $repeats" + (if (picks.isEmpty()) "" else " · " + picks.joinToString(", "))
}
