@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import android.content.res.Resources
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MedActivation
import com.fitlens.companion.data.MedDays
import com.fitlens.companion.data.MedDose
import com.fitlens.companion.data.MedPhase
import com.fitlens.companion.data.MedSchedule
import com.fitlens.companion.data.MedSchedules
import com.fitlens.companion.data.Medications
import com.fitlens.companion.data.PhaseUnit
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.DragHandle
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.ReorderToggle
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SegmentedSwitch
import com.fitlens.companion.ui.design.StepperField
import com.fitlens.companion.ui.design.TopBarAction
import com.fitlens.companion.ui.design.raisedGlass
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.max

/**
 * The schedule editor (#201), built like the workout editor: a schedule's name and notes, whether the cycle repeats,
 * then its phases as cards (ON · 8 weeks / OFF · 4 weeks). An on phase has + Add dose and its doses as rows; each
 * phase and dose opens its sheet. The Reorder toggle moves phases and doses (#170); ⋮ has Duplicate and Delete.
 * Nothing is written until Save, and Back with unsaved changes asks first. Under the phases, RUN starts the schedule
 * from a chosen date, or pauses, resumes or ends the run (#202); a running schedule's start date can be changed, or it
 * can start again from day 1, each with Undo (#229). Only the last phase can be ongoing (#228): it can't be moved or
 * duplicated, a new phase goes in before it, and Repeat is hidden while it's there. EARLIER RUNS lists ended runs,
 * whose dates can be changed or which can be deleted, with Undo (#231); no two runs of a schedule may overlap.
 */

/** A phase in the editor with a stable [key]; doses carry their own keys so moving keeps each row with its dose. */
@Serializable
private data class PhaseDraft(val key: Long, val phase: MedPhase, val doseKeys: List<Long>)

/** An [editingDoseAt] dose key meaning a new dose. */
private const val NEW_DOSE = -1L

@Composable
fun ScheduleEditorScreen(snap: Snapshot, nav: Nav, id: Long) {
    val context = LocalContext.current
    val res = context.resources
    val original = remember(id) {
        snap.medPlan.schedulesById[id] ?: MedSchedule(
            0L, "", phases = listOf(MedPhase(0L, true, 8, PhaseUnit.WEEKS), MedPhase(0L, false, 4, PhaseUnit.WEEKS)), repeats = true
        )
    }
    // The draft and its open sheets come back after rotation or a restart in the background (#304). This screen is
    // pushed once per schedule and never replaced by another, so its state needn't be keyed by [id].
    val state = viewModel<ScheduleEditorState>()
    var name by state.saved("name", original.name)
    var notes by state.saved("notes", original.notes.orEmpty())
    var repeats by state.saved("repeats", original.repeats)
    // Stable keys for phases and doses. A restored draft keeps its keys, so new ones start after the highest.
    val phases = state.savedList("phases", PhaseDraft.serializer()) {
        var next = 0L
        original.phases.map { p -> PhaseDraft(next++, p, p.doses.map { next++ }) }
    }
    val keys = remember { longArrayOf(phases.maxOfOrNull { d -> maxOf(d.key, d.doseKeys.maxOrNull() ?: 0L) }?.plus(1) ?: 0L) }
    fun key(): Long = keys[0]++
    var reordering by rememberSaveable(id) { mutableStateOf(false) }
    var editingPhase by state.saved<Long?>("editingPhase", null)
    var addingPhase by state.saved("addingPhase", false)
    // The dose sheet: the phase's key, and the dose's key (-1 adds one). Kept as two ids.
    var editingDoseAt by state.saved<LongArray?>("editingDose", null)
    var confirmLeave by state.saved("confirmLeave", false)
    var confirmDelete by state.saved("confirmDelete", false)
    var startDate by rememberSaveable(id) { mutableStateOf(Dates.today()) }
    var pickStart by state.saved("pickStart", false)
    var confirmEnd by state.saved("confirmEnd", false)
    // A changed start or a restart (#229): first the date picker, then a confirm with the picked date.
    var pickNewStart by state.saved("pickNewStart", false)
    var newStart by rememberSaveable { mutableStateOf<String?>(null) }
    var pickRestart by state.saved("pickRestart", false)
    var restartOn by rememberSaveable { mutableStateOf<String?>(null) }
    // An earlier run being changed (#231): its id, and whether its first day (1, else its last, 0) is picked; one to delete.
    var runEditAt by state.saved<LongArray?>("runEdit", null)
    var runDelete by state.saved<Long?>("runDelete", null)
    // A new schedule first asks how it's taken (#232); new doses then start on the days chosen there.
    var setup by state.saved("setup", id == 0L)
    var newDoseDays by state.savedJson("newDoseDays", MedDays.serializer()) { MedDays() }

    val draft = MedSchedule(original.id, name, notes.ifBlank { null }, repeats, original.sortOrder, phases.map { it.phase })
    val dirty = draft.name != original.name || draft.notes.orEmpty() != original.notes.orEmpty() ||
        draft.repeats != original.repeats || draft.phases != original.phases

    fun updatePhase(phaseKey: Long, change: (PhaseDraft) -> PhaseDraft) {
        val at = phases.indexOfFirst { it.key == phaseKey }
        if (at >= 0) phases[at] = change(phases[at])
    }

    /** Saves the draft and returns its id, or null when it can't be saved yet. */
    suspend fun saveNow(): Long? {
        if (draft.name.isBlank()) {
            UiEvents.show(res.getString(R.string.md_schedule_name_needed))
            return null
        }
        return MedSchedules.save(draft)
    }
    fun save() {
        if (draft.name.isBlank()) {
            UiEvents.show(res.getString(R.string.md_schedule_name_needed))
            return
        }
        nav.pop()
        AppScope.scope.launch {
            MedSchedules.save(draft)
            UiEvents.show(res.getString(R.string.md_schedule_saved, draft.name.trim()))
        }
    }
    BackHandler(enabled = dirty) { confirmLeave = true }

    val current = if (original.id == 0L) null else snap.medPlan.current(original.id)
    // The last run, when it has ended: RUN then offers to start again (#229).
    val runs = if (original.id == 0L) emptyList() else snap.medPlan.activationsBySchedule[original.id].orEmpty()
    val lastRun = runs.lastOrNull()
    // Earlier runs (#231), newest first; the current one is RUN's.
    val pastRuns = runs.filter { it.endedOn != null && it.id != current?.id }.sortedByDescending { it.start }
    val endedRun = lastRun?.takeIf { current == null && it.endedOn != null }
    // An ongoing last phase (#228) stays last.
    val endsOngoing = phases.lastOrNull()?.phase?.unit == PhaseUnit.ONGOING

    if (setup) {
        ScheduleSetup(snap.weekStart, onBack = { nav.pop() }) { start ->
            phases.clear()
            start.phases.forEach { p -> phases.add(PhaseDraft(key(), p, emptyList())) }
            repeats = start.repeats
            newDoseDays = start.days
            setup = false
            // Straight on to the first dose, the one thing the setup can't guess.
            phases.firstOrNull { it.phase.on }?.let { editingDoseAt = longArrayOf(it.key, NEW_DOSE) }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = stringResource(if (id == 0L) R.string.md_schedule_new else R.string.md_schedule_edit),
            onBack = { if (dirty) confirmLeave = true else nav.pop() },
            actions = listOf(TopBarAction(Icons.Filled.Check, stringResource(R.string.md_schedule_save), enabled = name.isNotBlank()) { save() }),
            trailing = {
                if (phases.size > 1 || phases.any { it.phase.doses.size > 1 }) ReorderToggle(reordering) { reordering = it }
            },
            overflow = if (original.id == 0L) emptyList() else listOf(
                MenuAction(stringResource(R.string.md_schedule_duplicate)) {
                    AppScope.scope.launch {
                        MedSchedules.copy(original, res.getString(R.string.workout_copy_name, original.name))
                        UiEvents.show(res.getString(R.string.md_schedule_duplicated, original.name))
                    }
                },
                MenuAction(stringResource(R.string.md_schedule_delete)) { confirmDelete = true }
            )
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = Spacing.xxl)) {
            item(key = "fields") {
                Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.md_schedule_name)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.workout_notes_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (endsOngoing) {
                        Text(stringResource(R.string.md_ongoing_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        SwitchLine(stringResource(R.string.md_repeat), stringResource(R.string.md_repeat_note), repeats) { repeats = it }
                    }
                }
            }
            phases.forEachIndexed { pi, draftPhase ->
                // An ongoing phase never moves, and nothing moves below it.
                val fixed = draftPhase.phase.unit == PhaseUnit.ONGOING
                val belowFixed = pi + 1 <= phases.lastIndex && phases[pi + 1].phase.unit == PhaseUnit.ONGOING
                item(key = "phase-${draftPhase.key}") {
                    PhaseCard(
                        draft = draftPhase,
                        reordering = reordering,
                        onEdit = { editingPhase = draftPhase.key },
                        onAddDose = { editingDoseAt = longArrayOf(draftPhase.key, NEW_DOSE) },
                        onEditDose = { k -> editingDoseAt = longArrayOf(draftPhase.key, k) },
                        onDuplicate = if (fixed) null else ({
                            val p = draftPhase.phase
                            phases.add(pi + 1, PhaseDraft(key(), p.copy(id = 0L, doses = p.doses.map { it.copy(id = 0L) }), p.doses.map { key() }))
                        }),
                        onDelete = { phases.removeAt(pi) },
                        onMoveUp = if (pi > 0 && !fixed) ({ phases.add(pi - 1, phases.removeAt(pi)) }) else null,
                        onMoveDown = if (pi < phases.lastIndex && !fixed && !belowFixed) ({ phases.add(pi + 1, phases.removeAt(pi)) }) else null,
                        onChange = { doses, doseKeys -> updatePhase(draftPhase.key) { it.copy(phase = it.phase.copy(doses = doses), doseKeys = doseKeys) } }
                    )
                }
            }
            item(key = "add-phase") {
                Text(
                    stringResource(R.string.md_add_phase).uppercase(),
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                        .raisedGlass(FitShapes.row, elevation = 2.dp, inset = 6.dp)
                        .clickable { addingPhase = true }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            item(key = "run") {
                Column(Modifier.padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SectionLabel(stringResource(R.string.md_run), Modifier.padding(top = Spacing.lg))
                    if (current == null) {
                        Text(
                            if (endedRun != null) stringResource(R.string.md_run_again_note, Dates.day(Dates.parse(endedRun.endedOn!!)!!.minusDays(1).format(Dates.ISO)))
                            else stringResource(R.string.md_run_note),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        GlassOutlinedButton(onClick = { pickStart = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch)) {
                            Text(stringResource(R.string.md_start_on, Dates.day(startDate)))
                        }
                        GoldButton(
                            onClick = {
                                val start = startDate
                                AppScope.scope.launch {
                                    val saved = (if (dirty || original.id == 0L) saveNow() else original.id) ?: return@launch
                                    val runId = MedSchedules.activate(saved, start)
                                    UiEvents.show(res.getString(R.string.md_started, draft.name.trim(), Dates.day(start)), res.getString(R.string.bm_undo)) {
                                        AppScope.scope.launch { MedSchedules.deleteActivation(MedActivation(runId, saved, start)) }
                                    }
                                }
                                if (dirty || original.id == 0L) nav.pop()
                            },
                            enabled = name.isNotBlank() && phases.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.row)
                        ) { Text(stringResource(if (endedRun != null) R.string.md_start_again else R.string.md_start)) }
                    } else {
                        // The start line (#229): a tap changes the start date; its menu also starts again from day 1.
                        ListRowWithMenu(
                            title = stringResource(if (current.start > Dates.today()) R.string.md_starts else R.string.md_started_on, Dates.day(current.start)),
                            subtitle = phaseText(res, original, current, Dates.today()),
                            onClick = { pickNewStart = true },
                            menu = listOf(
                                MenuAction(stringResource(R.string.md_change_start)) { pickNewStart = true },
                                MenuAction(stringResource(R.string.md_restart)) { pickRestart = true }
                            ),
                            handle = false
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            GlassOutlinedButton(
                                onClick = {
                                    val before = current
                                    AppScope.scope.launch {
                                        if (before.paused) MedSchedules.resume(before) else MedSchedules.pause(before)
                                        UiEvents.show(
                                            res.getString(if (before.paused) R.string.md_resumed else R.string.md_paused, original.name),
                                            res.getString(R.string.bm_undo)
                                        ) { AppScope.scope.launch { MedSchedules.restore(before) } }
                                    }
                                },
                                modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)
                            ) { Text(stringResource(if (current.paused) R.string.md_resume else R.string.md_pause)) }
                            GlassOutlinedButton(onClick = { confirmEnd = true }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)) {
                                Text(stringResource(R.string.md_end))
                            }
                        }
                    }
                }
            }
            // This run's dose changes (#204), each removable with Undo.
            if (current != null) doseChangeRows(snap, snap.medPlan.changes.filter { it.activationId == current.id })
            pastRunRows(snap, pastRuns, onEdit = { a, first -> runEditAt = longArrayOf(a.id, if (first) 1L else 0L) }, onDelete = { runDelete = it.id })
        }
        GoldHairline()
        GoldButton(
            onClick = { save() },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md).heightIn(min = Spacing.row)
        ) { Text(stringResource(R.string.md_schedule_save)) }
    }

    if (addingPhase) {
        // A new phase goes last, or just before an ongoing last phase, which is the only one that can be ongoing.
        PhaseSheet(null, canBeOngoing = !endsOngoing, onDismiss = { addingPhase = false }) { p ->
            if (endsOngoing) phases.add(phases.lastIndex, PhaseDraft(key(), p, emptyList())) else phases.add(PhaseDraft(key(), p, emptyList()))
            addingPhase = false
        }
    }
    editingPhase?.let { k -> phases.firstOrNull { it.key == k } }?.let { d ->
        PhaseSheet(d.phase, canBeOngoing = phases.lastOrNull()?.key == d.key, onDismiss = { editingPhase = null }) { p ->
            updatePhase(d.key) { it.copy(phase = it.phase.copy(on = p.on, length = p.length, unit = p.unit)) }
            editingPhase = null
        }
    }
    editingDoseAt?.let { (phaseKey, doseKey) ->
        val d = phases.firstOrNull { it.key == phaseKey } ?: return@let
        val at = if (doseKey == NEW_DOSE) -1 else d.doseKeys.indexOf(doseKey)
        DoseSheet(snap, d.phase.doses.getOrNull(at), newDoseDays, onDismiss = { editingDoseAt = null }) { dose ->
            updatePhase(phaseKey) {
                if (at >= 0) it.copy(phase = it.phase.copy(doses = it.phase.doses.toMutableList().also { l -> l[at] = dose }))
                else it.copy(phase = it.phase.copy(doses = it.phase.doses + dose), doseKeys = it.doseKeys + key())
            }
            editingDoseAt = null
        }
    }
    if (pickStart) PickDateDialog(startDate, onDismiss = { pickStart = false }) { startDate = it }
    if (pickNewStart && current != null) PickDateDialog(current.start, onDismiss = { pickNewStart = false }) { picked ->
        val moved = MedSchedules.withStart(current, picked)
        when {
            moved == null -> UiEvents.show(res.getString(R.string.md_start_after_end))
            MedSchedules.overlaps(runs, moved) -> UiEvents.show(res.getString(R.string.md_run_overlaps))
            picked != current.start -> newStart = picked
        }
    }
    if (pickRestart) PickDateDialog(Dates.today(), onDismiss = { pickRestart = false }) { restartOn = it }
    val newStartDate = newStart
    if (newStartDate != null && current != null) {
        val date = newStartDate
        val before = current
        ConfirmSheet(
            title = stringResource(R.string.md_change_start_title, Dates.day(date)),
            message = stringResource(R.string.md_change_start_body, Dates.day(before.start), Dates.day(date)),
            confirmLabel = stringResource(R.string.md_change_start_confirm),
            destructive = false,
            onDismiss = { newStart = null },
            onConfirm = {
                AppScope.scope.launch {
                    if (MedSchedules.setStart(before, date)) {
                        UiEvents.show(res.getString(R.string.md_start_changed, original.name, Dates.day(date)), res.getString(R.string.bm_undo)) {
                            AppScope.scope.launch { MedSchedules.restore(before) }
                        }
                    }
                }
            }
        )
    }
    val restartOnDate = restartOn
    if (restartOnDate != null && current != null) {
        val date = restartOnDate
        val before = current
        ConfirmSheet(
            title = stringResource(R.string.md_restart_title, original.name),
            message = stringResource(R.string.md_restart_body, Dates.day(date)),
            confirmLabel = stringResource(R.string.md_restart_confirm),
            destructive = false,
            onDismiss = { restartOn = null },
            onConfirm = {
                AppScope.scope.launch {
                    val runId = MedSchedules.restart(before, date)
                    UiEvents.show(res.getString(R.string.md_restarted, original.name, Dates.day(date)), res.getString(R.string.bm_undo)) {
                        AppScope.scope.launch { MedSchedules.undoRestart(before, runId) }
                    }
                }
            }
        )
    }
    if (confirmEnd && current != null) {
        ConfirmSheet(
            title = stringResource(R.string.md_end_title, original.name),
            message = stringResource(R.string.md_end_body),
            confirmLabel = stringResource(R.string.md_end),
            onDismiss = { confirmEnd = false },
            onConfirm = {
                val before = current
                AppScope.scope.launch {
                    MedSchedules.end(before, Dates.parse(Dates.today())!!.plusDays(1).format(Dates.ISO))
                    UiEvents.show(res.getString(R.string.md_ended_named, original.name), res.getString(R.string.bm_undo)) {
                        AppScope.scope.launch { MedSchedules.restore(before) }
                    }
                }
            }
        )
    }
    runEditAt?.let { (runId, firstFlag) -> runs.firstOrNull { it.id == runId }?.let { it to (firstFlag == 1L) } }?.let { (a, first) ->
        PickDateDialog(if (first) a.start else MedSchedules.lastDayOf(a), onDismiss = { runEditAt = null }) { picked ->
            val moved = if (first) MedSchedules.withStart(a, picked) else MedSchedules.withLastDay(a, picked)
            when {
                moved == null -> UiEvents.show(res.getString(R.string.md_run_dates_invalid))
                MedSchedules.overlaps(runs, moved) -> UiEvents.show(res.getString(R.string.md_run_overlaps))
                moved != a -> AppScope.scope.launch {
                    MedSchedules.restore(moved)
                    UiEvents.show(res.getString(R.string.md_run_changed), res.getString(R.string.bm_undo)) {
                        AppScope.scope.launch { MedSchedules.restore(a) }
                    }
                }
            }
        }
    }
    runDelete?.let { runId -> runs.firstOrNull { it.id == runId } }?.let { a ->
        ConfirmSheet(
            title = stringResource(R.string.md_run_delete_title),
            message = stringResource(R.string.md_run_delete_body),
            confirmLabel = stringResource(R.string.md_run_delete),
            onDismiss = { runDelete = null },
            onConfirm = {
                val changes = snap.medPlan.changes.filter { it.activationId == a.id }
                AppScope.scope.launch {
                    MedSchedules.deleteActivation(a)
                    UiEvents.show(res.getString(R.string.md_run_deleted), res.getString(R.string.bm_undo)) {
                        AppScope.scope.launch { MedSchedules.restoreRun(a, changes) }
                    }
                }
            }
        )
    }
    if (confirmLeave) {
        ConfirmSheet(
            title = stringResource(R.string.workout_discard_title),
            message = stringResource(R.string.md_discard_body),
            confirmLabel = stringResource(R.string.workout_discard),
            onDismiss = { confirmLeave = false },
            onConfirm = { nav.pop() }
        )
    }
    if (confirmDelete) {
        ConfirmSheet(
            title = stringResource(R.string.md_schedule_delete_title, original.name),
            message = stringResource(R.string.md_schedule_delete_body),
            confirmLabel = stringResource(R.string.md_schedule_delete),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                nav.pop()
                AppScope.scope.launch {
                    MedSchedules.delete(original.id)
                    UiEvents.show(res.getString(R.string.md_schedule_deleted, original.name))
                }
            }
        )
    }
}

/**
 * The simple setup of a new schedule (#232): "How do you take it?" with Every day, On set days of the week, Every few
 * days (each ongoing, #228) or In cycles (on and off, repeating). [onDone] gets the phases it builds; the editor then
 * opens with them, so anything can still be changed there.
 */
@Composable
private fun ScheduleSetup(weekStart: Int, onBack: () -> Unit, onDone: (MedSchedules.Starter) -> Unit) {
    val res = LocalContext.current.resources
    var choice by rememberSaveable { mutableStateOf(0) }
    var weekdays by rememberSaveable { mutableStateOf(listOf(1, 3, 5)) }
    var every by rememberSaveable { mutableStateOf("2") }
    var onLength by rememberSaveable { mutableStateOf("8") }
    var onUnit by rememberSaveable { mutableStateOf(PhaseUnit.WEEKS) }
    var offLength by rememberSaveable { mutableStateOf("4") }
    var offUnit by rememberSaveable { mutableStateOf(PhaseUnit.WEEKS) }
    val options = listOf(
        R.string.md_setup_daily to R.string.md_setup_daily_note,
        R.string.md_setup_weekdays to R.string.md_setup_weekdays_note,
        R.string.md_setup_every to R.string.md_setup_every_note,
        R.string.md_setup_cycles to R.string.md_setup_cycles_note
    )
    val ready = when (choice) {
        1 -> weekdays.isNotEmpty()
        2 -> (every.toIntOrNull() ?: 0) >= 2
        3 -> (onLength.toIntOrNull() ?: 0) > 0 && (offLength.toIntOrNull() ?: 0) > 0
        else -> true
    }

    @Composable
    fun lengthPicker(label: String, value: String, onValue: (String) -> Unit, unit: PhaseUnit, onUnitChange: (PhaseUnit) -> Unit) {
        StepperField(
            label = label,
            value = value,
            onValue = { onValue(it.filter { c -> c.isDigit() }.take(3)) },
            onStep = { dir -> onValue(max(1, (value.toIntOrNull() ?: 1) + dir).toString()) },
            keyboard = KeyboardType.Number
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            PhaseUnit.entries.filter { it != PhaseUnit.ONGOING }.forEach { u ->
                FilterChip(
                    selected = unit == u, onClick = { onUnitChange(u) },
                    label = { Text(phaseLength(res, value.toIntOrNull() ?: 1, u).substringAfter(' ')) }
                )
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        FitTopBar(title = stringResource(R.string.md_schedule_new), onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(stringResource(R.string.md_setup_question), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.md_setup_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.selectableGroup()) {
                options.forEachIndexed { i, (title, note) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Spacing.row)
                            .selectable(selected = choice == i, role = Role.RadioButton) { choice = i }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = choice == i, onClick = null)
                        Column(Modifier.padding(start = Spacing.sm)) {
                            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
                            Text(stringResource(note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            when (choice) {
                1 -> Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    MedSchedules.weekOrder(weekStart).forEach { d ->
                        FilterChip(
                            selected = d in weekdays,
                            onClick = { weekdays = if (d in weekdays) weekdays - d else weekdays + d },
                            label = { Text(MedSchedules.weekdayShort(d)) }
                        )
                    }
                }
                2 -> StepperField(
                    label = stringResource(R.string.md_every_how_many),
                    value = every,
                    onValue = { every = it.filter { c -> c.isDigit() }.take(3) },
                    onStep = { dir -> every = max(2, (every.toIntOrNull() ?: 2) + dir).toString() },
                    keyboard = KeyboardType.Number
                )
                3 -> {
                    lengthPicker(stringResource(R.string.md_setup_on_for), onLength, { onLength = it }, onUnit) { onUnit = it }
                    lengthPicker(stringResource(R.string.md_setup_off_for), offLength, { offLength = it }, offUnit) { offUnit = it }
                }
            }
        }
        GoldHairline()
        GoldButton(
            onClick = {
                onDone(
                    when (choice) {
                        1 -> MedSchedules.ongoingStarter(MedDays(weekdays = weekdays.toSet()))
                        2 -> MedSchedules.ongoingStarter(MedDays(every = (every.toIntOrNull() ?: 2).coerceIn(2, 365)))
                        3 -> MedSchedules.cycleStarter(onLength.toIntOrNull() ?: 1, onUnit, offLength.toIntOrNull() ?: 1, offUnit)
                        else -> MedSchedules.ongoingStarter(MedDays())
                    }
                )
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md).heightIn(min = Spacing.row)
        ) { Text(stringResource(R.string.md_setup_next)) }
    }
}

/**
 * EARLIER RUNS (#231): each ended run's dates, how many days it ran and the share of doses taken. Its menu changes
 * its first or last day ([onEdit] with true for the first) or deletes it ([onDelete]), each with Undo.
 */
private fun LazyListScope.pastRunRows(snap: Snapshot, runs: List<MedActivation>, onEdit: (MedActivation, Boolean) -> Unit, onDelete: (MedActivation) -> Unit) {
    if (runs.isEmpty()) return
    item(key = "runs-label") { SectionTitle(stringResource(R.string.md_runs)) }
    items(runs.size, key = { "run-${runs[it].id}" }) { i ->
        val a = runs[i]
        val res = LocalContext.current.resources
        val today = Dates.today()
        val summary = remember(snap.bodyKey, a) { MedSchedules.runSummary(snap.medPlan, snap.medicationView.records, a, today) }
        val days = res.getQuantityString(R.plurals.md_days, summary.days.toInt(), summary.days.toInt())
        ListRowWithMenu(
            title = res.getString(R.string.md_run_dates, Dates.day(a.start), Dates.day(summary.lastDay ?: today)),
            subtitle = days + (summary.adherence?.let { " · " + res.getString(R.string.md_run_taken, it) } ?: ""),
            onClick = { onEdit(a, true) },
            menu = listOf(
                MenuAction(stringResource(R.string.md_change_start)) { onEdit(a, true) },
                MenuAction(stringResource(R.string.md_run_change_end)) { onEdit(a, false) },
                MenuAction(stringResource(R.string.md_run_delete)) { onDelete(a) }
            ),
            handle = false
        )
    }
}

/** A switch with its title and note, the whole row toggling it. */
@Composable
private fun SwitchLine(title: String, note: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.touch).toggleable(value = on, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = null)
    }
}

/** One phase as a card: ON · 8 weeks (or OFF) with + and ⋮ over a gold rule, then its doses. */
@Composable
private fun PhaseCard(
    draft: PhaseDraft,
    reordering: Boolean,
    onEdit: () -> Unit,
    onAddDose: () -> Unit,
    onEditDose: (Long) -> Unit,
    onDuplicate: (() -> Unit)?,
    onDelete: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onChange: (List<MedDose>, List<Long>) -> Unit
) {
    val res = LocalContext.current.resources
    val p = draft.phase
    var menu by rememberSaveable { mutableStateOf(false) }
    val label = phaseLabel(res, p)
    val moves = listOfNotNull(
        onMoveUp?.let { f -> CustomAccessibilityAction(res.getString(R.string.cmn_move_up)) { f(); true } },
        onMoveDown?.let { f -> CustomAccessibilityAction(res.getString(R.string.cmn_move_down)) { f(); true } }
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm).raisedGlass(FitShapes.card)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = res.getString(R.string.md_edit_phase), onClick = onEdit)
                .padding(start = Spacing.lg).semantics { if (moves.isNotEmpty()) customActions = moves },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (p.on) IconButton(onClick = onAddDose) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.md_add_dose_to, label)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.ms_options_for, label)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOfNotNull(
                        MenuAction(stringResource(R.string.md_edit_phase), onClick = onEdit),
                        onDuplicate?.let { MenuAction(stringResource(R.string.md_duplicate_phase), onClick = it) },
                        MenuAction(stringResource(R.string.md_delete_phase), onClick = onDelete)
                    ).forEach { a -> DropdownMenuItem(text = { Text(a.label) }, onClick = { menu = false; a.onClick() }) }
                }
            }
            if (reordering && (onMoveUp != null || onMoveDown != null)) DragHandle(label, Spacing.row * 2, onMoveUp, onMoveDown)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brand.Gold.copy(alpha = 0.7f)))
        if (!p.on) {
            Text(
                stringResource(R.string.md_off_note),
                Modifier.padding(Spacing.lg),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (p.doses.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.md_phase_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                GlassOutlinedButton(onClick = onAddDose, modifier = Modifier.padding(top = Spacing.sm)) { Text(stringResource(R.string.md_add_dose)) }
            }
        }
        if (p.on) p.doses.forEachIndexed { i, d ->
            val doseKey = draft.doseKeys.getOrNull(i) ?: return@forEachIndexed
            fun moved(by: Int): Pair<List<MedDose>, List<Long>> {
                val doses = p.doses.toMutableList().also { l -> l.add(i + by, l.removeAt(i)) }
                val ks = draft.doseKeys.toMutableList().also { l -> l.add(i + by, l.removeAt(i)) }
                return doses to ks
            }
            key(doseKey) {
                ListRowWithMenu(
                    title = "${d.medication} ${fmtNum(d.amount)} ${d.unit}".trim(),
                    subtitle = Dates.time(androidx.compose.ui.platform.LocalContext.current, d.time) + " · " + daysText(res, d.days) + (taperText(res, d)?.let { " · $it" } ?: "") +
                        (d.comment?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    onClick = { onEditDose(doseKey) },
                    menu = listOf(
                        MenuAction(stringResource(R.string.lib_edit)) { onEditDose(doseKey) },
                        MenuAction(stringResource(R.string.md_duplicate_dose)) {
                            onChange(
                                p.doses.toMutableList().also { it.add(i + 1, d.copy(id = 0L)) },
                                draft.doseKeys.toMutableList().also { it.add(i + 1, -(System.nanoTime())) }
                            )
                        },
                        MenuAction(stringResource(R.string.workout_remove)) {
                            onChange(p.doses.filterIndexed { j, _ -> j != i }, draft.doseKeys.filterIndexed { j, _ -> j != i })
                        }
                    ),
                    onMoveUp = if (i > 0) ({ val (a, b) = moved(-1); onChange(a, b) }) else null,
                    onMoveDown = if (i < p.doses.lastIndex) ({ val (a, b) = moved(1); onChange(a, b) }) else null,
                    handle = reordering
                )
            }
        }
    }
}

/** "ON · 8 weeks" or "OFF · 4 weeks". */
internal fun phaseLabel(res: Resources, p: MedPhase): String =
    res.getString(if (p.on) R.string.md_phase_label_on else R.string.md_phase_label_off, phaseLength(res, p.length, p.unit))

/** "Down 5 mg every 7 days, to 10 mg" or "Up 2.5 mg every 14 days", a dose's taper or step-up (#234); null without one. */
internal fun taperText(res: Resources, d: MedDose): String? {
    if (!d.steps) return null
    val every = res.getQuantityString(R.plurals.md_step_every, d.stepEvery, d.stepEvery)
    fun amount(v: Double) = "${fmtNum(v)} ${d.unit}".trim()
    val by = amount(abs(d.step))
    return when {
        d.step < 0 -> res.getString(R.string.md_taper_down, by, every, amount(minOf(d.stepLimit ?: 0.0, d.amount)))
        d.stepLimit != null -> res.getString(R.string.md_taper_up, by, every, amount(maxOf(d.stepLimit, d.amount)))
        else -> res.getString(R.string.md_taper_up_open, by, every)
    }
}

/** "20 → 15 → 10 mg": the amounts a taper or step-up (#234) goes through, up to [max] of them. */
internal fun taperPreview(d: MedDose, max: Int = 6): String {
    val amounts = ArrayList<Double>()
    var day = 0L
    while (amounts.size < max) {
        val a = d.amountOn(day)
        if (amounts.lastOrNull() == a) break
        amounts += a
        day += d.stepEvery.coerceAtLeast(1)
    }
    val more = d.amountOn(day) != amounts.last()
    return amounts.joinToString(" → ") { fmtNum(it) } + (if (more) " → …" else "") + " " + d.unit
}

/** "Every day", "Mon, Wed, Fri", "Every 2 days". */
internal fun daysText(res: Resources, days: MedDays): String = when {
    days.weekdays.isNotEmpty() -> MedSchedules.weekOrder(1).filter { it in days.weekdays }.joinToString(", ") { MedSchedules.weekdayShort(it) }
    days.every > 1 -> res.getString(R.string.md_every_n_days, days.every)
    else -> res.getString(R.string.md_every_day)
}

/**
 * A phase's sheet: on or off, and how long. [existing] null adds one. [canBeOngoing] (the last phase, or a new one
 * after a fixed-length last phase) offers "Ongoing (no end)" for an on phase (#228).
 */
@Composable
private fun PhaseSheet(existing: MedPhase?, canBeOngoing: Boolean, onDismiss: () -> Unit, onSave: (MedPhase) -> Unit) {
    val res = LocalContext.current.resources
    // The sheets' typed fields are saved, so rotating the phone keeps them (#304).
    var on by rememberSaveable { mutableStateOf(existing?.on ?: true) }
    var length by rememberSaveable { mutableStateOf((existing?.length ?: 4).toString()) }
    var unit by rememberSaveable { mutableStateOf(existing?.unit ?: PhaseUnit.WEEKS) }
    val offered = PhaseUnit.entries.filter { it != PhaseUnit.ONGOING || (canBeOngoing && on) }
    // Turning a phase off (or a phase that can't be ongoing) falls back to weeks.
    val chosen = if (unit in offered) unit else PhaseUnit.WEEKS
    val ongoing = chosen == PhaseUnit.ONGOING
    FitSheet(
        title = stringResource(if (existing == null) R.string.md_add_phase else R.string.md_edit_phase),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.type_save),
        confirmEnabled = ongoing || (length.toIntOrNull() ?: 0) > 0,
        onConfirm = {
            val n = if (ongoing) 1 else (length.toIntOrNull() ?: 1).coerceIn(1, 999)
            onSave(MedPhase(existing?.id ?: 0L, on, n, chosen, existing?.doses.orEmpty()))
        }
    ) {
        SegmentedSwitch(listOf(stringResource(R.string.md_on), stringResource(R.string.md_off)), if (on) 0 else 1, { on = it == 0 })
        Text(
            stringResource(if (on) R.string.md_on_note else R.string.md_off_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!ongoing) {
            StepperField(
                label = stringResource(R.string.md_length),
                value = length,
                onValue = { length = it.filter { c -> c.isDigit() }.take(3) },
                onStep = { dir -> length = max(1, (length.toIntOrNull() ?: 1) + dir).toString() },
                keyboard = KeyboardType.Number
            )
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            offered.forEach { u ->
                val n = length.toIntOrNull() ?: 1
                FilterChip(
                    selected = chosen == u, onClick = { unit = u },
                    label = { Text(if (u == PhaseUnit.ONGOING) stringResource(R.string.md_ongoing_choice) else phaseLength(res, n, u).substringAfter(' ')) }
                )
            }
        }
        if (ongoing) {
            Text(stringResource(R.string.md_ongoing_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * A dose's sheet: medication, amount, unit, time, which days, and a comment. [existing] null adds one, on
 * [defaultDays] (the simple setup's choice, #232).
 */
@Composable
private fun DoseSheet(snap: Snapshot, existing: MedDose?, defaultDays: MedDays, onDismiss: () -> Unit, onSave: (MedDose) -> Unit) {
    val res = LocalContext.current.resources
    val names = remember(snap.bodyKey) { snap.medications.map { it.name } }
    var medication by rememberSaveable { mutableStateOf(existing?.medication ?: names.firstOrNull().orEmpty()) }
    fun unitOf(n: String) = snap.medications.firstOrNull { it.name == n }?.unit.orEmpty()
    var amount by rememberSaveable { mutableStateOf(existing?.amount?.let { fmtNum(it) } ?: "") }
    var unit by rememberSaveable { mutableStateOf(existing?.unit ?: unitOf(medication)) }
    var time by rememberSaveable { mutableStateOf(Dates.clock(existing?.time.orEmpty())?.let { String.format(java.util.Locale.ROOT, "%02d:%02d", it.hour, it.minute) } ?: "08:00") }
    // 0 every day, 1 chosen weekdays, 2 every N days.
    val startDays = existing?.days ?: defaultDays
    var mode by rememberSaveable { mutableStateOf(if (startDays.weekdays.isNotEmpty()) 1 else if (startDays.every > 1) 2 else 0) }
    var weekdays by rememberSaveable { mutableStateOf(startDays.weekdays.ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) }) }
    var every by rememberSaveable { mutableStateOf((startDays.every.takeIf { it > 1 } ?: 2).toString()) }
    var comment by rememberSaveable { mutableStateOf(existing?.comment.orEmpty()) }
    // A taper or step-up (#234): 0 the same every day, 1 down, 2 up; by how much, how often, and the floor or ceiling.
    val stepped = existing?.takeIf { it.steps }
    var stepMode by rememberSaveable { mutableStateOf(if (stepped == null) 0 else if (stepped.step < 0) 1 else 2) }
    var stepBy by rememberSaveable { mutableStateOf(stepped?.let { fmtNum(abs(it.step)) }.orEmpty()) }
    var stepEvery by rememberSaveable { mutableStateOf((stepped?.stepEvery ?: 7).toString()) }
    var stepLimit by rememberSaveable { mutableStateOf(stepped?.stepLimit?.let { fmtNum(it) }.orEmpty()) }
    fun number(text: String) = text.trim().replace(',', '.').toDoubleOrNull()

    /** The taper fields as a dose's step, amount and limit: null when they're asked for but not filled in. */
    fun step(): Triple<Double, Int, Double?>? {
        if (stepMode == 0) return Triple(0.0, 0, null)
        val by = number(stepBy)?.takeIf { it > 0 } ?: return null
        val days = (stepEvery.toIntOrNull() ?: 7).coerceIn(1, 365)
        return Triple(if (stepMode == 1) -by else by, days, number(stepLimit)?.takeIf { it >= 0 })
    }

    fun save() {
        val n = medication.trim()
        val a = number(amount)
        val s = step()
        when {
            n.isBlank() || a == null -> UiEvents.show(res.getString(R.string.md_dose_needs))
            s == null -> UiEvents.show(res.getString(R.string.md_step_needs))
            snap.medicationView.inOtherHalf(n) -> UiEvents.show(res.getString(R.string.md_is_body, n))
            !Medications.canBeMedication(n) -> UiEvents.show(res.getString(R.string.md_refused, n))
            else -> {
                val days = when (mode) {
                    1 -> MedDays(weekdays = weekdays.ifEmpty { setOf(1, 2, 3, 4, 5, 6, 7) })
                    2 -> MedDays(every = (every.toIntOrNull() ?: 2).coerceIn(2, 365))
                    else -> MedDays()
                }
                onSave(
                    MedDose(
                        existing?.id ?: 0L, n, a, unit.trim(), MedSchedules.cleanTime(time), days, comment.trim().ifBlank { null },
                        s.first, s.second, s.third
                    )
                )
            }
        }
    }

    FitSheet(
        title = stringResource(if (existing == null) R.string.md_add_dose else R.string.md_edit_dose),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.type_save),
        onConfirm = { save() }
    ) {
        if (names.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                names.forEach { n ->
                    FilterChip(selected = n == medication, onClick = { medication = n; unit = unitOf(n).ifBlank { unit } }, label = { Text(n) })
                }
            }
        }
        OutlinedTextField(
            value = medication, onValueChange = { medication = it },
            label = { Text(stringResource(R.string.md_medication)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = amount, onValueChange = { amount = it }, label = { Text(stringResource(R.string.md_amount)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = unit, onValueChange = { unit = it }, label = { Text(stringResource(R.string.md_unit_short)) }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        TimeField(time = time, onTime = { time = it }, label = stringResource(R.string.bm_time), modifier = Modifier.fillMaxWidth())
        Column(Modifier.selectableGroup()) {
            listOf(R.string.md_every_day, R.string.md_on_weekdays, R.string.md_every_few_days).forEachIndexed { i, label ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Spacing.touch).selectable(selected = mode == i, role = Role.RadioButton) { mode = i },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = mode == i, onClick = null)
                    Text(stringResource(label), Modifier.padding(start = Spacing.sm))
                }
            }
        }
        if (mode == 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                MedSchedules.weekOrder(snap.weekStart).forEach { d ->
                    FilterChip(
                        selected = d in weekdays,
                        onClick = { weekdays = if (d in weekdays) weekdays - d else weekdays + d },
                        label = { Text(MedSchedules.weekdayShort(d)) }
                    )
                }
            }
        }
        if (mode == 2) {
            StepperField(
                label = stringResource(R.string.md_every_how_many),
                value = every,
                onValue = { every = it.filter { c -> c.isDigit() }.take(3) },
                onStep = { dir -> every = max(2, (every.toIntOrNull() ?: 2) + dir).toString() },
                keyboard = KeyboardType.Number
            )
        }
        SectionLabel(stringResource(R.string.md_step_label), Modifier.padding(top = Spacing.sm))
        SegmentedSwitch(
            listOf(stringResource(R.string.md_step_none), stringResource(R.string.md_step_down), stringResource(R.string.md_step_up)),
            stepMode, { stepMode = it }
        )
        if (stepMode != 0) {
            val unitLabel = unit.trim().ifBlank { "-" }
            OutlinedTextField(
                value = stepBy, onValueChange = { stepBy = it }, label = { Text(stringResource(R.string.md_step_by, unitLabel)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
            )
            StepperField(
                label = stringResource(R.string.md_step_every_days),
                value = stepEvery,
                onValue = { stepEvery = it.filter { c -> c.isDigit() }.take(3) },
                onStep = { dir -> stepEvery = max(1, (stepEvery.toIntOrNull() ?: 7) + dir).toString() },
                keyboard = KeyboardType.Number
            )
            OutlinedTextField(
                value = stepLimit, onValueChange = { stepLimit = it },
                label = { Text(stringResource(if (stepMode == 1) R.string.md_step_floor else R.string.md_step_ceiling, unitLabel)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth()
            )
            val a = number(amount)
            val s = step()
            if (a != null && s != null) {
                val preview = MedDose(0L, medication, a, unit.trim(), step = s.first, stepEvery = s.second, stepLimit = s.third)
                Text(stringResource(R.string.md_step_preview, taperPreview(preview)), style = MaterialTheme.typography.bodyMedium)
            }
            Text(stringResource(R.string.md_step_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = comment, onValueChange = { comment = it },
            label = { Text(stringResource(R.string.mse_comment_optional)) }, modifier = Modifier.fillMaxWidth()
        )
    }
}
