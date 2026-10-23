package com.fitlens.companion.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.fitlens.companion.data.MedDoseChange
import com.fitlens.companion.data.Store
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.MenuAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.DueDose
import com.fitlens.companion.data.MedActivation
import com.fitlens.companion.data.MedPlan
import com.fitlens.companion.data.MedSchedule
import com.fitlens.companion.data.MedSchedules
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.Haptics
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SetCell
import com.fitlens.companion.ui.design.SetRow as SetRowView
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HHMMSS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * The top of the Medications Track tab (#202): **TODAY**, every dose due today across the running schedules with a
 * done box like a set's (#19), each schedule's phase ("On · day 12 of 56", "Off · on again 3 Dec"), missed doses
 * flagged; then **SCHEDULES** (#201), each with its state, and + New schedule. [onDose] opens a dose's sheet.
 */
internal fun LazyListScope.medicationTrackHeader(snap: Snapshot, nav: Nav, onDose: (DueDose) -> Unit) {
    val plan = snap.medPlan
    val today = Dates.today()
    val due = MedSchedules.dueOn(plan, today)
    val running = plan.activations.filter { a -> plan.schedulesById.containsKey(a.scheduleId) && a.start <= today && a.runsOn(today) }
    if (running.isNotEmpty()) {
        item(key = "today-label") { SectionTitle(stringResource(R.string.md_today)) }
        items(running.size, key = { "phase-${running[it].id}" }) { i ->
            val a = running[i]
            val s = plan.schedulesById.getValue(a.scheduleId)
            val res = LocalContext.current.resources
            Text(
                "${s.name} · ${phaseText(res, s, a, today)}",
                Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (due.isEmpty()) {
            item(key = "today-none") {
                Text(
                    stringResource(R.string.md_today_none),
                    Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(due.size, key = { "due-${due[it].dose.id}-${due[it].due}" }) { i ->
            DueDoseRow(snap, due[i], i + 1, onOpen = { onDose(due[i]) })
        }
    }
    item(key = "schedules") {
        SectionTitle(stringResource(R.string.md_schedules))
    }
    items(plan.schedules.size, key = { "schedule-${plan.schedules[it].id}" }) { i ->
        val s = plan.schedules[i]
        val res = LocalContext.current.resources
        ListRowWithMenu(
            title = s.name,
            subtitle = scheduleState(res, plan, s, today),
            onClick = { nav.push(Screen.MedSchedule(s.id)) },
            handle = false
        )
    }
    item(key = "new-schedule") {
        GlassOutlinedButton(
            onClick = { nav.push(Screen.MedSchedule(0L)) },
            modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm).heightIn(min = Spacing.touch)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Brand.Gold)
            Text(stringResource(R.string.md_new_schedule))
        }
    }
    item(key = "meds-label") { SectionTitle(stringResource(R.string.md_title)) }
}

/** One dose due today, as a set row: time, medication and amount, a done box, and Missed or Skipped beside it. */
@Composable
private fun DueDoseRow(snap: Snapshot, due: DueDose, index: Int, onOpen: () -> Unit) {
    val res = LocalContext.current.resources
    val view = LocalView.current
    val taken = MedSchedules.takenRecord(snap.medicationView.records, due)
    val skipped = taken == null && snap.medPlan.isSkipped(due.dose.id, due.due)
    val missed = taken == null && !skipped && due.date == Dates.today() && due.time < LocalTime.now().toString().take(5)
    SetRowView(
        index = index,
        summary = doseLine(due),
        cells = listOf(
            SetCell(due.time, spoken = res.getString(R.string.body_at_time, due.time), unitSlot = false),
            SetCell(due.dose.medication, spoken = due.dose.medication, unitSlot = false),
            SetCell(fmtNum(due.amount), due.dose.unit, "${fmtNum(due.amount)} ${due.dose.unit}".trim())
        ),
        comment = due.dose.comment,
        done = taken != null,
        onDoneChange = { on ->
            if (on) {
                Haptics.confirm(view)
                takeDose(res, due)
            } else {
                AppScope.scope.launch { MedSchedules.untake(due) }
            }
        },
        trailingHint = when {
            skipped -> res.getString(R.string.md_skipped)
            missed -> res.getString(R.string.md_missed)
            else -> null
        },
        noun = res.getString(R.string.md_dose),
        onClick = onOpen
    )
}

/** Takes [due] now (or at its due time on another day), with Undo. */
internal fun takeDose(res: Resources, due: DueDose) {
    val time = if (due.date == Dates.today()) LocalTime.now().format(HHMMSS) else due.time + ":00"
    AppScope.scope.launch {
        MedSchedules.take(due, time)
        UiEvents.show(res.getString(R.string.md_taken_named, due.dose.medication), res.getString(R.string.bm_undo)) {
            AppScope.scope.launch { MedSchedules.untake(due) }
        }
    }
}

/** Skips [due], with Undo. */
internal fun skipDose(res: Resources, due: DueDose) {
    AppScope.scope.launch {
        MedSchedules.skip(due)
        UiEvents.show(res.getString(R.string.md_skipped_named, due.dose.medication), res.getString(R.string.bm_undo)) {
            AppScope.scope.launch { MedSchedules.unskip(due) }
        }
    }
}

/** "Creatine 5 g", a dose in one line. */
internal fun doseLine(due: DueDose): String = "${due.dose.medication} ${fmtNum(due.amount)} ${due.dose.unit}".trim()

/** Where [a] is on [date]: "On · day 12 of 56", "Off · on again 3 Dec", "Paused since 4 Oct", "Starts 1 Nov", "Finished". */
internal fun phaseText(res: Resources, s: MedSchedule, a: MedActivation, date: String): String {
    val pause = a.pauses.firstOrNull { it.to == null }
    return when {
        a.start > date -> res.getString(R.string.md_starts, Dates.medium(a.start))
        pause != null && date >= pause.from -> res.getString(R.string.md_paused_since, Dates.medium(pause.from))
        !a.runsOn(date) -> res.getString(R.string.md_ended)
        else -> {
            val st = MedSchedules.phaseOn(s, a, date) ?: return res.getString(R.string.md_finished)
            if (st.phase.on) res.getString(R.string.md_phase_on, st.day, st.days)
            else res.getString(R.string.md_phase_off, Dates.medium(Dates.parse(st.lastDay)!!.plusDays(1).format(Dates.ISO)))
        }
    }
}

/** A schedule's state on the list: running (and where), starting, or not running. */
internal fun scheduleState(res: Resources, plan: MedPlan, s: MedSchedule, date: String): String {
    val a = plan.current(s.id, date) ?: return res.getString(R.string.md_not_running) + " · " + scheduleSummary(res, s)
    return phaseText(res, s, a, date)
}

/** "2 weeks on · 1 week off, repeating". */
internal fun scheduleSummary(res: Resources, s: MedSchedule): String {
    if (s.phases.isEmpty()) return res.getString(R.string.md_no_phases)
    val parts = s.phases.joinToString(" · ") { p ->
        res.getString(if (p.on) R.string.md_summary_on else R.string.md_summary_off, phaseLength(res, p.length, p.unit))
    }
    return if (s.repeats) res.getString(R.string.md_summary_repeating, parts) else parts
}

/** "8 weeks", "1 month". */
internal fun phaseLength(res: Resources, n: Int, unit: com.fitlens.companion.data.PhaseUnit): String = res.getQuantityString(
    when (unit) {
        com.fitlens.companion.data.PhaseUnit.DAYS -> R.plurals.md_days
        com.fitlens.companion.data.PhaseUnit.WEEKS -> R.plurals.md_weeks
        com.fitlens.companion.data.PhaseUnit.MONTHS -> R.plurals.md_months
        com.fitlens.companion.data.PhaseUnit.YEARS -> R.plurals.md_years
    },
    n, n
)

/**
 * A dose due today, opened from its row (#202): Taken or Skip (or undo either), the dose's details, and Change amount
 * (#204), which asks whether the change is for this day only or the rest of the schedule.
 */
@Composable
internal fun DueDoseSheet(snap: Snapshot, due: DueDose, onDismiss: () -> Unit, extra: @Composable () -> Unit = {}) {
    val res = LocalContext.current.resources
    val taken = MedSchedules.takenRecord(snap.medicationView.records, due)
    val skipped = taken == null && snap.medPlan.isSkipped(due.dose.id, due.due)
    var changing by remember { mutableStateOf(false) }
    if (changing) {
        DoseChangeSheet(snap, due, onDismiss)
        return
    }
    com.fitlens.companion.ui.design.FitSheet(
        title = doseLine(due),
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.cancel),
        confirmLabel = stringResource(if (taken != null) R.string.md_untake else R.string.md_take),
        onConfirm = {
            if (taken != null) AppScope.scope.launch { MedSchedules.untake(due) } else takeDose(res, due)
            onDismiss()
        },
        secondaryLabel = stringResource(if (skipped) R.string.md_unskip else R.string.md_skip),
        onSecondary = {
            if (skipped) AppScope.scope.launch { MedSchedules.unskip(due) } else skipDose(res, due)
            onDismiss()
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(res.getString(R.string.md_due_at, Dates.medium(due.date), due.time), style = MaterialTheme.typography.bodyLarge)
            Text(due.schedule.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (taken != null) {
                Text(
                    res.getString(R.string.md_taken_at, taken.time.take(5)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (due.change != null) {
                Text(
                    res.getString(R.string.md_changed_from, "${fmtNum(due.dose.amount)} ${due.dose.unit}".trim()),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!due.dose.comment.isNullOrBlank()) Text("“${due.dose.comment}”", style = MaterialTheme.typography.bodySmall)
            GlassOutlinedButton(onClick = { changing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.md_change_amount))
            }
            extra()
        }
    }
}

/**
 * Changes a dose's amount (#204): **This day only**, or **This day and the rest of this schedule**. The second is a
 * change from that date that the schedule's doses follow; the schedule itself stays as it is. A dose already taken
 * that day takes the new amount too. Undo removes the change and puts the taken value back.
 */
@Composable
internal fun DoseChangeSheet(snap: Snapshot, due: DueDose, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    var amount by remember { mutableStateOf(fmtNum(due.amount)) }
    var dayOnly by remember { mutableStateOf(true) }
    val taken = MedSchedules.takenRecord(snap.medicationView.records, due)
    FitSheet(
        title = stringResource(R.string.md_change_title, due.dose.medication),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.type_save),
        confirmEnabled = amount.trim().replace(',', '.').toDoubleOrNull() != null,
        onConfirm = {
            val value = amount.trim().replace(',', '.').toDoubleOrNull()
            if (value != null) {
                val change = MedDoseChange(0L, due.activation.id, due.dose.id, due.date, if (dayOnly) due.date else null, value)
                val only = dayOnly
                AppScope.scope.launch {
                    val id = MedSchedules.addChange(change)
                    if (taken != null) Store.updateRecord(taken.id, taken.date, taken.time, value, taken.comment)
                    UiEvents.show(
                        res.getString(if (only) R.string.md_changed_day else R.string.md_changed_rest, due.dose.medication),
                        res.getString(R.string.bm_undo)
                    ) {
                        AppScope.scope.launch {
                            MedSchedules.deleteChange(id)
                            if (taken != null) Store.updateRecord(taken.id, taken.date, taken.time, taken.value, taken.comment)
                        }
                    }
                }
            }
            onDismiss()
        }
    ) {
        OutlinedTextField(
            value = amount, onValueChange = { amount = it },
            label = { Text(stringResource(R.string.md_amount_in, due.dose.unit.ifBlank { "-" })) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Column(Modifier.selectableGroup()) {
            listOf(true to R.string.md_change_day_only, false to R.string.md_change_rest).forEach { (only, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.touch)
                        .selectable(selected = dayOnly == only, role = Role.RadioButton) { dayOnly = only },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = dayOnly == only, onClick = null)
                    Text(stringResource(label, Dates.medium(due.date)), Modifier.padding(start = Spacing.sm))
                }
            }
        }
        Text(
            stringResource(R.string.md_change_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Dose changes as rows (#204), "From 12 Oct: Creatine 7.5 g" or "12 Oct only: Creatine 2 g", each removed from its
 * menu with Undo. Shown in a medication's History and on its schedule's run.
 */
internal fun LazyListScope.doseChangeRows(snap: Snapshot, changes: List<MedDoseChange>) {
    if (changes.isEmpty()) return
    item(key = "changes-label") { SectionTitle(stringResource(R.string.md_changes)) }
    items(changes.size, key = { "change-${changes[it].id}" }) { i -> DoseChangeRow(snap, changes[i]) }
}

/** One dose change, with what it replaced. */
@Composable
internal fun DoseChangeRow(snap: Snapshot, c: MedDoseChange) {
    val res = LocalContext.current.resources
    val found = snap.medPlan.dosesById[c.doseId] ?: return
    val schedule = found.first
    val dose = found.second
    val amount = "${fmtNum(c.amount)} ${dose.unit}".trim()
    val was = "${fmtNum(dose.amount)} ${dose.unit}".trim()
    ListRowWithMenu(
        title = if (c.toDate == c.fromDate) res.getString(R.string.md_change_row_day, Dates.medium(c.fromDate), dose.medication, amount)
            else res.getString(R.string.md_change_row_rest, Dates.medium(c.fromDate), dose.medication, amount),
        subtitle = res.getString(R.string.md_change_row_was, was, schedule.name, dose.time),
        menu = listOf(
            MenuAction(stringResource(R.string.md_change_remove)) {
                AppScope.scope.launch {
                    MedSchedules.deleteChange(c.id)
                    UiEvents.show(res.getString(R.string.md_change_removed), res.getString(R.string.bm_undo)) {
                        AppScope.scope.launch { MedSchedules.addChange(c) }
                    }
                }
            }
        ),
        handle = false
    )
}
