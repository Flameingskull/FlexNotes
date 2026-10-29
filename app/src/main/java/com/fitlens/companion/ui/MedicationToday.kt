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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import com.fitlens.companion.data.MedDoseChange
import com.fitlens.companion.data.InjectionSite
import com.fitlens.companion.data.InjectionSites
import com.fitlens.companion.data.MedExtras
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
import com.fitlens.companion.data.PhaseUnit
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.Haptics
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.SectionLabel
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.fitlens.companion.ui.design.FitCheckbox
import com.fitlens.companion.ui.design.FitRadioButton

private val HHMMSS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * The Medications **Today** tab (#227, from #202): every dose due today across the running schedules with a done box
 * like a set's (#19), each schedule's phase ("On until Mon 3 Nov", "Off · on again Wed 3 Dec", #237), missed doses flagged;
 * supply warnings (#209) and SYMPTOMS (#207, [onSymptoms] opens the rating sheet). With nothing running it says so and
 * [onSchedules] opens the Schedules tab. [onDose] opens a dose's sheet.
 */
internal fun LazyListScope.medicationTodayTab(snap: Snapshot, nav: Nav, onSchedules: () -> Unit, onSymptoms: () -> Unit, onDose: (DueDose) -> Unit) {
    val plan = snap.medPlan
    val today = Dates.today()
    val due = MedSchedules.dueOn(plan, today)
    val running = plan.activations.filter { a -> plan.schedulesById.containsKey(a.scheduleId) && a.start <= today && a.runsOn(today) }
    item(key = "today-label") { SectionTitle(stringResource(R.string.md_today)) }
    if (running.isEmpty()) {
        item(key = "today-idle") {
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(
                    stringResource(if (plan.schedules.isEmpty()) R.string.md_today_no_schedules else R.string.md_today_not_running),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GlassOutlinedButton(onClick = onSchedules, modifier = Modifier.heightIn(min = Spacing.touch)) {
                    Text(stringResource(R.string.md_go_schedules))
                }
            }
        }
    }
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
    if (running.isNotEmpty() && due.isEmpty()) {
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
    // Medications taken only when needed (#233), each with Take now.
    asNeededRows(snap, nav)
    // Medications running low, run out or expiring (#209), then today's symptom ratings (#207).
    supplyAlertRows(snap, nav)
    symptomRows(snap, onSymptoms)
}

/** The Medications **Schedules** tab (#227, from #201): each schedule with its state, and + New schedule. */
internal fun LazyListScope.medicationSchedulesTab(snap: Snapshot, nav: Nav) {
    val plan = snap.medPlan
    val today = Dates.today()
    item(key = "schedules") { SectionTitle(stringResource(R.string.md_schedules)) }
    if (plan.schedules.isEmpty()) {
        item(key = "schedules-none") {
            Text(
                stringResource(R.string.md_schedules_none),
                Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
}

/**
 * One dose due today (#238): its time at the start, the medication's name in the space left (wrapping onto a second
 * line when it's long, so every row keeps the same layout), the amount under it, Missed or Skipped, and a done box like
 * a set's. TalkBack reads the row as one sentence; the done box is its own stop.
 */
@Composable
private fun DueDoseRow(snap: Snapshot, due: DueDose, index: Int, onOpen: () -> Unit) {
    val res = LocalContext.current.resources
    // The time in the phone's clock style (#280); the column is wider for "10:05 PM" so every row lines up.
    val clockText = Dates.time(LocalContext.current, due.time)
    val twelveHour = !android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val view = LocalView.current
    val taken = MedSchedules.takenRecord(snap.medicationView.records, due)
    val skipped = taken == null && snap.medPlan.isSkipped(due.dose.id, due.due)
    val missed = taken == null && !skipped && due.date == Dates.today() && due.time < LocalTime.now().toString().take(5)
    val amount = "${fmtNum(due.amount)} ${due.dose.unit}".trim()
    val hint = when {
        skipped -> res.getString(R.string.md_skipped)
        missed -> res.getString(R.string.md_missed)
        else -> null
    }
    val spoken = listOfNotNull(
        "${res.getString(R.string.md_dose)} $index",
        res.getString(R.string.body_at_time, clockText),
        due.dose.medication,
        amount,
        hint,
        due.dose.comment?.takeIf { it.isNotBlank() }
    ).joinToString(", ")
    val rule = Brand.Hairline
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg)
            .drawBehind {
                val y = size.height - 1.dp.toPx() / 2
                drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .heightIn(min = Spacing.touch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(onClick = onOpen)
                .clearAndSetSemantics {
                    contentDescription = spoken
                    role = Role.Button
                    onClick(label = null) { onOpen(); true }
                }
                .padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                clockText,
                Modifier.widthIn(min = if (twelveHour) 88.dp else 56.dp),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
                Text(due.dose.medication, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(amount, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (!due.dose.comment.isNullOrBlank()) {
                    Text(
                        "“${due.dose.comment}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (hint != null) {
                Text(
                    hint,
                    Modifier.padding(start = Spacing.sm).widthIn(max = 96.dp),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        val doneLabel = res.getString(R.string.md_take) + ", " + doseLine(due)
        FitCheckbox(
            checked = taken != null,
            onCheckedChange = { on ->
                if (on && snap.isInjected(due.dose.medication)) {
                    // An injected dose needs its site (#210): the sheet asks, with the next site chosen.
                    onOpen()
                } else if (on) {
                    Haptics.confirm(view)
                    takeDose(res, due)
                } else {
                    AppScope.scope.launch { MedSchedules.untake(due) }
                }
            },
            modifier = Modifier.semantics { contentDescription = doneLabel }
        )
    }
}

/** Takes [due] now (or at its due time on another day), at [site] for an injected dose (#210), with Undo. */
internal fun takeDose(res: Resources, due: DueDose, site: InjectionSite? = null) {
    val time = if (due.date == Dates.today()) LocalTime.now().format(HHMMSS) else due.time + ":00"
    AppScope.scope.launch {
        MedSchedules.take(due, time, site = site)
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

/**
 * Where [a] is on [date], as dates people plan by (#237), never a day count: "On until Mon 3 Nov", "Off · on again
 * Tue 4 Nov", "Off until Fri 14 Nov, then it ends", "Ongoing since Mon 6 Oct", "Paused since Sat 4 Oct",
 * "Starts Sat 1 Nov", "Finished".
 */
internal fun phaseText(res: Resources, s: MedSchedule, a: MedActivation, date: String): String {
    val pause = a.pauses.firstOrNull { it.to == null }
    return when {
        a.start > date -> res.getString(R.string.md_starts, Dates.day(a.start))
        pause != null && date >= pause.from -> res.getString(R.string.md_paused_since, Dates.day(pause.from))
        !a.runsOn(date) -> res.getString(R.string.md_ended)
        else -> {
            val st = MedSchedules.phaseOn(s, a, date) ?: return res.getString(R.string.md_finished)
            val last = st.lastDay
            val phases = s.phases.filter { it.length > 0 }
            // Another phase follows unless this is the last one of a schedule that doesn't repeat.
            val more = s.repeats || phases.indexOf(st.phase) < phases.size - 1
            when {
                // An ongoing phase (#228) has no end: when the run began says more than a count of days.
                last == null -> if (st.phase.on) res.getString(R.string.md_phase_on_ongoing, Dates.day(a.start))
                    else res.getString(R.string.md_phase_off_ongoing)
                st.phase.on -> res.getString(R.string.md_phase_on, Dates.day(last))
                !more -> res.getString(R.string.md_phase_off_last, Dates.day(last))
                else -> res.getString(R.string.md_phase_off, Dates.day(Dates.plusDays(last, 1)))
            }
        }
    }
}

/** A schedule's state on the list: running (and where), starting, or not running. */
internal fun scheduleState(res: Resources, plan: MedPlan, s: MedSchedule, date: String): String {
    val a = plan.current(s.id, date) ?: return res.getString(R.string.md_not_running) + " · " + scheduleSummary(res, s)
    return phaseText(res, s, a, date)
}

/** "2 weeks on · 1 week off, repeating", or "2 weeks on · then on, ongoing" (#228). */
internal fun scheduleSummary(res: Resources, s: MedSchedule): String {
    if (s.phases.isEmpty()) return res.getString(R.string.md_no_phases)
    val parts = s.phases.joinToString(" · ") { p ->
        if (p.unit == PhaseUnit.ONGOING) res.getString(if (p.on) R.string.md_summary_on_ongoing else R.string.md_summary_off_ongoing)
        else res.getString(if (p.on) R.string.md_summary_on else R.string.md_summary_off, phaseLength(res, p.length, p.unit))
    }
    return if (s.repeats && !s.ongoing) res.getString(R.string.md_summary_repeating, parts) else parts
}

/** "8 weeks", "1 month", or "ongoing" (#228). */
internal fun phaseLength(res: Resources, n: Int, unit: PhaseUnit): String = when (unit) {
    PhaseUnit.DAYS -> res.getQuantityString(R.plurals.md_days, n, n)
    PhaseUnit.WEEKS -> res.getQuantityString(R.plurals.md_weeks, n, n)
    PhaseUnit.MONTHS -> res.getQuantityString(R.plurals.md_months, n, n)
    PhaseUnit.YEARS -> res.getQuantityString(R.plurals.md_years, n, n)
    PhaseUnit.ONGOING -> res.getString(R.string.md_ongoing)
}

/**
 * A dose due today, opened from its row (#202): Taken or Skip (or undo either), the dose's details, and Change amount
 * (#204), which asks whether the change is for this day only or the rest of the schedule.
 */
@Composable
internal fun DueDoseSheet(snap: Snapshot, due: DueDose, onDismiss: () -> Unit, extra: @Composable () -> Unit = {}) {
    val res = LocalContext.current.resources
    val taken = MedSchedules.takenRecord(snap.medicationView.records, due)
    val skipped = taken == null && snap.medPlan.isSkipped(due.dose.id, due.due)
    var changing by rememberSaveable { mutableStateOf(false) }
    // An injected dose's site (#210): the next one to begin with, or where a taken dose went.
    val injected = snap.isInjected(due.dose.medication)
    val suggested = remember(snap.bodyKey, due.dose.medication) {
        InjectionSites.suggest(snap.medicationView.records, due.dose.medication)
    }
    var site by rememberSaveable(taken?.id) { mutableStateOf(if (taken != null) InjectionSite.of(taken.site) else suggested) }
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
            if (taken != null) AppScope.scope.launch { MedSchedules.untake(due) } else takeDose(res, due, if (injected) site else null)
            onDismiss()
        },
        secondaryLabel = stringResource(if (skipped) R.string.md_unskip else R.string.md_skip),
        onSecondary = {
            if (skipped) AppScope.scope.launch { MedSchedules.unskip(due) } else skipDose(res, due)
            onDismiss()
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(res.getString(R.string.md_due_at, Dates.day(due.date), Dates.time(LocalContext.current, due.time)), style = MaterialTheme.typography.bodyLarge)
            Text(due.schedule.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (taken != null) {
                Text(
                    res.getString(R.string.md_taken_at, Dates.time(LocalContext.current, taken.time)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (due.change != null) {
                Text(
                    res.getString(R.string.md_changed_from, "${fmtNum(due.planned)} ${due.dose.unit}".trim()),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // A tapering dose (#234) says how it changes.
            taperText(res, due.dose)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!due.dose.comment.isNullOrBlank()) Text("“${due.dose.comment}”", style = MaterialTheme.typography.bodySmall)
            GlassOutlinedButton(onClick = { changing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.md_change_amount))
            }
            if (injected) {
                // Before taking it, the site to record; once taken, a tap moves it there at once.
                SitePicker(snap, due.dose.medication, site) { picked ->
                    site = picked
                    if (taken != null) AppScope.scope.launch { MedExtras.setSite(taken.id, picked) }
                }
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
    var amount by rememberSaveable { mutableStateOf(fmtNum(due.amount)) }
    var dayOnly by rememberSaveable { mutableStateOf(true) }
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
                    FitRadioButton(selected = dayOnly == only, onClick = null)
                    Text(stringResource(label, Dates.day(due.date)), Modifier.padding(start = Spacing.sm))
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
        title = if (c.toDate == c.fromDate) res.getString(R.string.md_change_row_day, Dates.day(c.fromDate), dose.medication, amount)
            else res.getString(R.string.md_change_row_rest, Dates.day(c.fromDate), dose.medication, amount),
        subtitle = res.getString(R.string.md_change_row_was, was, schedule.name, Dates.time(LocalContext.current, dose.time)),
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
