@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.OverflowMenu
import com.fitlens.companion.ui.design.StepperField
import android.content.res.Resources
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.PlannedExercise
import com.fitlens.companion.data.PlannedSet
import com.fitlens.companion.data.Routine
import com.fitlens.companion.data.RoutineDay
import com.fitlens.companion.data.Routines
import com.fitlens.companion.data.SetTypes
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.WorkoutDataException
import com.fitlens.companion.data.fmtDuration
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.ReorderToggle
import com.fitlens.companion.ui.design.DragHandle
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.key
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.SearchablePicker
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SegmentedSwitch
import com.fitlens.companion.ui.design.TopBarAction
import com.fitlens.companion.ui.design.raisedGlass
import kotlinx.coroutines.launch

/**
 * The workout editor (#106, FitNotes's routine editor): a workout's name and notes, then its days as cards. Each day
 * has **+** (Add exercise) and a menu (rename, duplicate, move, copy to another workout, delete); each exercise row
 * opens how its sets are filled, and its menu handles supersets, swapping and removing. Nothing is written until
 * Save; Back with unsaved changes asks first.
 */

/** One exercise in the editor, with a stable [key] so moving keeps each row with its exercise. */
private data class Slot(val key: Long, val planned: PlannedExercise)

/** One day in the editor. [id] is the saved day's id (0 for a new day), so logged dates keep pointing at it. */
private data class DayDraft(val key: Long, val id: Long, val name: String, val slots: List<Slot>)

@Composable
fun WorkoutEditorScreen(snap: Snapshot, nav: Nav, id: Long) {
    val context = LocalContext.current
    fun dayN(n: Int): String = context.getString(R.string.workout_day_n, n)
    val original = remember(id) { snap.routinesById[id] ?: Routine(0L, "", days = listOf(RoutineDay(0L, dayN(1)))) }
    var name by remember(id) { mutableStateOf(original.name) }
    var notes by remember(id) { mutableStateOf(original.notes.orEmpty()) }
    // Training blocks (#218), saved with the workout.
    var plan by remember(id) { mutableStateOf(original.plan) }
    // Stable keys for days and rows; a plain counter, since handing one out needn't recompose anything.
    val keys = remember(id) { longArrayOf(0L) }
    fun key(): Long = keys[0]++
    val days = remember(id) {
        mutableStateListOf<DayDraft>().apply {
            original.days.forEach { d -> add(DayDraft(key(), d.id, d.name, d.exercises.map { Slot(key(), it) })) }
        }
    }
    var adding by remember { mutableStateOf<Long?>(null) }
    var editingSets by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var swapping by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var renaming by remember { mutableStateOf<Long?>(null) }
    var copying by remember { mutableStateOf<Long?>(null) }
    // The day whose "Set rest for every exercise" sheet is open (#138).
    var restingDay by remember { mutableStateOf<Long?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    // Set once Save is tapped with no name, so the Name field shows what's missing (#287).
    var nameMissing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Reorder in place (#170): drag handles on the days and their exercises while the toggle is on.
    var reordering by rememberSaveable(id) { mutableStateOf(false) }

    val draft = Routine(
        original.id, name, notes, original.sortOrder,
        days.mapIndexed { i, d -> RoutineDay(d.id, d.name.ifBlank { dayN(i + 1) }, d.slots.map { it.planned }) },
        plan
    )
    val dirty = draft.name != original.name || draft.notes.orEmpty() != original.notes.orEmpty() || draft.days != original.days ||
        draft.plan != original.plan

    fun updateDay(dayKey: Long, change: (DayDraft) -> DayDraft) {
        val at = days.indexOfFirst { it.key == dayKey }
        if (at >= 0) days[at] = change(days[at])
    }
    fun updateSlot(dayKey: Long, slotKey: Long, change: (PlannedExercise) -> PlannedExercise) = updateDay(dayKey) { d ->
        d.copy(slots = d.slots.map { if (it.key == slotKey) it.copy(planned = change(it.planned)) else it })
    }
    fun save() {
        val r = draft
        if (r.name.isBlank()) {
            nameMissing = true
            UiEvents.show(context.getString(R.string.workout_name_needed))
            return
        }
        nav.pop()
        AppScope.scope.launch {
            try {
                val saved = Routines.save(r)
                // A new workout becomes the one the library shows (#21).
                if (r.id == 0L) Settings.updatePortable { it.copy(lastRoutineId = saved) }
                UiEvents.show(context.getString(R.string.workout_saved, r.name.trim()))
            } catch (e: WorkoutDataException) {
                UiEvents.show(e.text(context.resources))
            }
        }
    }
    BackHandler(enabled = dirty) { confirmLeave = true }

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = stringResource(if (id == 0L) R.string.workout_new_title else R.string.workout_edit_title),
            onBack = { if (dirty) confirmLeave = true else nav.pop() },
            actions = listOf(TopBarAction(Icons.Filled.Check, stringResource(R.string.workout_save)) { save() }),
            trailing = {
                if (days.size > 1 || days.any { it.slots.size > 1 } || (plan?.blocks?.size ?: 0) > 1) ReorderToggle(reordering) { reordering = it }
            },
            overflow = if (original.id == 0L) emptyList() else listOf(
                MenuAction(stringResource(R.string.workout_duplicate)) {
                    AppScope.scope.launch {
                        Routines.copy(original, context.getString(R.string.workout_copy_name, original.name))
                        UiEvents.show(context.getString(R.string.workout_duplicated, original.name))
                    }
                },
                MenuAction(stringResource(R.string.workout_delete)) { confirmDelete = true }
            )
        )
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = Spacing.xxl)) {
            item(key = "fields") {
                Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it; if (it.isNotBlank()) nameMissing = false },
                        label = { Text(stringResource(R.string.workout_name_label)) },
                        isError = nameMissing && name.isBlank(),
                        supportingText = if (nameMissing && name.isBlank()) ({ Text(stringResource(R.string.workout_name_needed)) }) else null,
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = notes, onValueChange = { notes = it }, label = { Text(stringResource(R.string.workout_notes_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            days.forEachIndexed { di, day ->
                item(key = "day-${day.key}") {
                    DayCard(
                        snap = snap,
                        day = day,
                        label = day.name.ifBlank { dayN(di + 1) },
                        reordering = reordering,
                        canCopy = snap.routines.any { it.id != original.id },
                        onAdd = { adding = day.key },
                        onRename = { renaming = day.key },
                        onDuplicate = {
                            days.add(di + 1, day.copy(key = key(), id = 0L, name = context.getString(R.string.workout_copy_name, day.name), slots = day.slots.map { Slot(key(), it.planned) }))
                        },
                        onMoveUp = if (di > 0) ({ days.add(di - 1, days.removeAt(di)) }) else null,
                        onMoveDown = if (di < days.lastIndex) ({ days.add(di + 1, days.removeAt(di)) }) else null,
                        onCopy = { copying = day.key },
                        onRest = { restingDay = day.key },
                        onDelete = {
                            // Brought back with Undo, at the same place (#288).
                            val removed = day
                            val at = di
                            days.removeAt(di)
                            UiEvents.show(
                                context.getString(R.string.workout_day_deleted, removed.name.ifBlank { dayN(at + 1) }),
                                context.getString(R.string.workout_undo)
                            ) { days.add(at.coerceAtMost(days.size), removed) }
                        },
                        onSets = { editingSets = day.key to it },
                        onSwap = { swapping = day.key to it },
                        onChange = { slots -> updateDay(day.key) { it.copy(slots = slots) } }
                    )
                }
            }
            item(key = "add-day") {
                // FitNotes's "TAP TO CREATE A NEW DAY" bar under the day cards (#143, its screenshots 26 and 27).
                Text(
                    stringResource(R.string.workout_new_day).uppercase(),
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                        .raisedGlass(FitShapes.row, elevation = 2.dp, inset = 6.dp)
                        .clickable(onClickLabel = stringResource(R.string.workout_new_day_action)) { days.add(DayDraft(key(), 0L, dayN(days.size + 1), emptyList())) }
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    style = MaterialTheme.typography.labelMedium
                )
            }
            // The workout's training blocks (#218), after its days.
            item(key = "blocks") {
                TrainingBlocksSection(plan, reordering) { plan = it }
            }
        }
        GoldHairline()
        GoldButton(
            onClick = { save() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md).heightIn(min = Spacing.row)
        ) { Text(stringResource(R.string.workout_save)) }
    }

    adding?.let { dayKey ->
        SearchablePicker(
            title = stringResource(R.string.workout_add_exercise),
            items = exercisePickerItems(snap),
            multiSelect = true,
            searchLabel = stringResource(R.string.workout_search_exercises),
            onDismiss = { adding = null },
            onPick = { ids ->
                adding = null
                val added = ids.map { Slot(key(), PlannedExercise(it, Routines.FILL_LAST)) }
                updateDay(dayKey) { it.copy(slots = it.slots + added) }
                // One exercise at a time asks straight away how its sets are filled, as FitNotes does.
                added.singleOrNull()?.let { editingSets = dayKey to it.key }
            }
        )
    }
    fun slotAt(at: Pair<Long, Long>?): Slot? =
        at?.let { (dayKey, slotKey) -> days.firstOrNull { it.key == dayKey }?.slots?.firstOrNull { it.key == slotKey } }
    val swapAt = swapping
    val swapSlot = slotAt(swapAt)
    if (swapAt != null && swapSlot != null) {
        val (dayKey, slotKey) = swapAt
        val current: Slot = swapSlot
        SearchablePicker(
            title = stringResource(R.string.workout_swap_title, snap.exercises[current.planned.exerciseId]?.name ?: stringResource(R.string.workout_exercise)),
            items = exercisePickerItems(snap).filter { it.id != current.planned.exerciseId },
            onDismiss = { swapping = null },
            onPick = { ids ->
                swapping = null
                ids.firstOrNull()?.let { to -> updateSlot(dayKey, slotKey) { it.copy(exerciseId = to) } }
            }
        )
    }
    val setsAt = editingSets
    val setsSlot = slotAt(setsAt)
    if (setsAt != null && setsSlot != null) {
        val (dayKey, slotKey) = setsAt
        PlannedSetsSheet(
            snap, setsSlot.planned, onDismiss = { editingSets = null },
            onProfile = { nav.push(Screen.SettingsPage(SettingsSection.Profile)) }
        ) { updated ->
            updateSlot(dayKey, slotKey) { updated }
            editingSets = null
        }
    }
    renaming?.let { dayKey ->
        val day = days.firstOrNull { it.key == dayKey }
        var dayName by remember(dayKey) { mutableStateOf(day?.name.orEmpty()) }
        FitSheet(
            title = stringResource(R.string.workout_rename_day),
            onDismiss = { renaming = null },
            confirmLabel = stringResource(R.string.workout_rename_day),
            confirmEnabled = dayName.isNotBlank(),
            onConfirm = {
                updateDay(dayKey) { it.copy(name = dayName.trim()) }
                renaming = null
            }
        ) {
            OutlinedTextField(
                value = dayName, onValueChange = { dayName = it }, label = { Text(stringResource(R.string.workout_day_name_label)) },
                singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
    }
    val restDay = restingDay?.let { dayKey -> days.firstOrNull { it.key == dayKey } }
    if (restDay != null) {
        val day: DayDraft = restDay
        DayRestSheet(day.name.ifBlank { stringResource(R.string.workout_this_day) }, onDismiss = { restingDay = null }) { rest, after ->
            // One rest for every exercise of the day (#138): it replaces each exercise's and each set's own.
            updateDay(day.key) { d ->
                d.copy(slots = d.slots.map { s ->
                    // A Rounds set's rest is the rest between its rounds, so it stays (#246).
                    val rounds = ExerciseTypes.isRounds(snap.exercises[s.planned.exerciseId]?.type ?: -1)
                    s.copy(planned = s.planned.copy(restSeconds = rest, restAfterSeconds = after, sets = s.planned.sets.map { if (rounds) it else it.copy(restSeconds = null) }))
                })
            }
            restingDay = null
        }
    }
    val copyDay = copying?.let { dayKey -> days.firstOrNull { it.key == dayKey } }
    if (copyDay != null) {
        val day: DayDraft = copyDay
        FitSheet(title = stringResource(R.string.workout_copy_day_title, day.name), onDismiss = { copying = null }) {
            snap.routines.filter { it.id != original.id }.forEach { r ->
                Text(
                    r.name,
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.row)
                        .clickable(onClickLabel = stringResource(R.string.workout_copy_to, r.name)) {
                            copying = null
                            AppScope.scope.launch {
                                val added = Routines.addDay(r.id, day.name, day.slots.map { it.planned })
                                UiEvents.show(context.getString(R.string.workout_copied_day, day.name, r.name), context.getString(R.string.workout_undo)) {
                                    AppScope.scope.launch { Routines.deleteDay(added) }
                                }
                            }
                        }
                        .padding(vertical = Spacing.md),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
    if (confirmLeave) {
        ConfirmSheet(
            title = stringResource(R.string.workout_discard_title),
            message = stringResource(R.string.workout_discard_body),
            confirmLabel = stringResource(R.string.workout_discard),
            onDismiss = { confirmLeave = false },
            onConfirm = { nav.pop() }
        )
    }
    if (confirmDelete) {
        ConfirmSheet(
            title = stringResource(R.string.workout_delete_title, original.name),
            message = stringResource(R.string.workout_delete_body),
            confirmLabel = stringResource(R.string.workout_delete),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                nav.pop()
                AppScope.scope.launch {
                    Routines.delete(original.id)
                    UiEvents.show(context.getString(R.string.workout_deleted, original.name), context.getString(R.string.workout_undo)) {
                        AppScope.scope.launch { Routines.copy(original, original.name) }
                    }
                }
            }
        )
    }
}

/** One day as a card: its name with + and a menu, then its exercises in order. */
@Composable
private fun DayCard(
    snap: Snapshot,
    day: DayDraft,
    label: String,
    reordering: Boolean,
    canCopy: Boolean,
    onAdd: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onCopy: () -> Unit,
    onRest: () -> Unit,
    onDelete: () -> Unit,
    onSets: (Long) -> Unit,
    onSwap: (Long) -> Unit,
    onChange: (List<Slot>) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val slots = day.slots
    // A day moves by its own height at each step of its handle (#170).
    var cardHeight by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val res = LocalContext.current.resources
    val dayActions = listOfNotNull(
        onMoveUp?.let { f -> CustomAccessibilityAction(res.getString(R.string.workout_move_up)) { f(); true } },
        onMoveDown?.let { f -> CustomAccessibilityAction(res.getString(R.string.workout_move_down)) { f(); true } }
    )
    // FitNotes's day card (#143, its screenshots 26 and 33): the day's name with + and ⋮ over a rule, then each
    // exercise with how its sets are filled, its ⋮ and a drag handle.
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .raisedGlass(FitShapes.card)
            .onSizeChanged { cardHeight = it.height }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = Spacing.lg).semantics { if (dayActions.isNotEmpty()) customActions = dayActions },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.workout_add_exercise_to, label)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.workout_more_for, label)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    listOfNotNull(
                        MenuAction(stringResource(R.string.workout_rename_day), onClick = onRename),
                        MenuAction(stringResource(R.string.workout_duplicate_day), onClick = onDuplicate),
                        if (canCopy && slots.isNotEmpty()) MenuAction(stringResource(R.string.workout_copy_to_another), onClick = onCopy) else null,
                        if (slots.isNotEmpty()) MenuAction(stringResource(R.string.workout_rest_every_exercise), onClick = onRest) else null,
                        MenuAction(stringResource(R.string.workout_delete_day), onClick = onDelete)
                    ).forEach { a ->
                        DropdownMenuItem(text = { Text(a.label) }, onClick = { menu = false; a.onClick() })
                    }
                }
            }
            if (reordering && (onMoveUp != null || onMoveDown != null)) {
                val step = with(density) { cardHeight.toDp() }.coerceAtLeast(Spacing.row)
                DragHandle(label, step, onMoveUp, onMoveDown)
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Brand.Gold.copy(alpha = 0.7f)))
        if (slots.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(Spacing.lg), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.workout_day_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                GlassOutlinedButton(onClick = onAdd, modifier = Modifier.padding(top = Spacing.sm)) {
                    Text(stringResource(R.string.day_add_exercise))
                }
            }
        }
        val groups = slots.map { it.planned.superset }.filter { it > 0 }.distinct()
        slots.forEachIndexed { i, slot ->
            fun withPlanned(at: Int, p: PlannedExercise) = slots.toMutableList().also { it[at] = slots[at].copy(planned = p) }
            // Keyed by the slot, so a row being dragged keeps its handle as the list reorders under it (#170).
            key(slot.key) {
            ListRowWithMenu(
                title = snap.exercises[slot.planned.exerciseId]?.name ?: stringResource(R.string.workout_exercise_title),
                subtitle = planSummary(LocalContext.current.resources, snap, slot.planned) +
                    (if (slot.planned.superset > 0) "  ·  " + stringResource(R.string.workout_superset, ('A' + groups.indexOf(slot.planned.superset)).toString()) else "") +
                    // Its prescribed rest on a line of its own, "Rest 90 s · then 2 min" (#138).
                    (restSummary(LocalContext.current.resources, slot.planned, !ExerciseTypes.isRounds(snap.exercises[slot.planned.exerciseId]?.type ?: -1))?.let { "\n$it" } ?: ""),
                leading = { Dot(categoryColour(snap.categoryOf(slot.planned.exerciseId)?.colour ?: 0), Spacing.md) },
                onClick = { onSets(slot.key) },
                menu = listOf(
                    MenuAction(stringResource(R.string.workout_sets)) { onSets(slot.key) },
                    MenuAction(stringResource(R.string.workout_superset_next), enabled = i < slots.lastIndex) {
                        val next = slots[i + 1].planned
                        val g = slot.planned.superset.takeIf { it > 0 } ?: next.superset.takeIf { it > 0 }
                            ?: ((slots.maxOfOrNull { it.planned.superset } ?: 0) + 1)
                        onChange(withPlanned(i, slot.planned.copy(superset = g)).also { it[i + 1] = it[i + 1].copy(planned = next.copy(superset = g)) })
                    },
                    MenuAction(stringResource(R.string.workout_superset_remove), enabled = slot.planned.superset > 0) {
                        val g = slot.planned.superset
                        val out = withPlanned(i, slot.planned.copy(superset = 0))
                        // A group left with one exercise dissolves.
                        if (out.count { it.planned.superset == g } < 2) {
                            out.indices.filter { out[it].planned.superset == g }
                                .forEach { j -> out[j] = out[j].copy(planned = out[j].planned.copy(superset = 0)) }
                        }
                        onChange(out)
                    },
                    MenuAction(stringResource(R.string.workout_swap_exercise)) { onSwap(slot.key) },
                    MenuAction(stringResource(R.string.workout_remove)) {
                        // Brought back with Undo (#288).
                        val before = slots
                        val removedName = snap.exercises[slot.planned.exerciseId]?.name ?: res.getString(R.string.workout_exercise_title)
                        onChange(slots.filter { it.key != slot.key })
                        UiEvents.show(res.getString(R.string.workout_exercise_removed, removedName), res.getString(R.string.workout_undo)) {
                            onChange(before)
                        }
                    }
                ),
                // Supersets move together (#170), as on the day log.
                onMoveUp = if (i > 0) ({ onChange(moveSlot(slots, i, -1)) }) else null,
                onMoveDown = if (i < slots.lastIndex) ({ onChange(moveSlot(slots, i, 1)) }) else null,
                handle = reordering
            )
            }
        }
    }
}

/** [slots] with the one at [at] moved one place up ([by] −1) or down (+1), its superset moving with it ([moveInOrder]). */
private fun moveSlot(slots: List<Slot>, at: Int, by: Int): List<Slot> {
    val byKey = slots.associateBy { it.key }
    return moveInOrder(slots.map { it.key }, { k -> byKey[k]?.planned?.superset ?: 0 }, at, by).map { byKey.getValue(it) }
}

/**
 * How an exercise's sets read in the editor and on the library's day cards. Its rest is [restSummary] (#138). These
 * summaries take their words from `strings.xml` (#94), so callers pass `LocalContext.current.resources`.
 */
fun planSummary(res: Resources, snap: Snapshot, p: PlannedExercise): String {
    return when (p.fill) {
        Routines.FILL_NONE -> res.getString(R.string.plan_log_as_you_go)
        Routines.FILL_PLANNED -> Routines.describe(res, snap, p.sets.filter { !it.isEmpty }, p.exerciseId)
        else -> {
            val last = Routines.resolve(snap, p, "9999-12-31")
            if (last.isEmpty()) res.getString(R.string.plan_copy_none)
            else res.getString(R.string.plan_copy, Routines.describe(res, snap, last, p.exerciseId))
        }
    }
}

/** A rest length as the editor shows it: "90 s", "2 min", "2 min 30 s" (#138). */
fun restLabel(res: Resources, seconds: Int): String = when {
    seconds < 60 -> res.getString(R.string.rest_label_seconds, seconds)
    seconds % 60 == 0 -> res.getString(R.string.rest_label_minutes, seconds / 60)
    else -> res.getString(R.string.rest_label_min_sec, seconds / 60, seconds % 60)
}

/**
 * An exercise's prescribed rest in one line (#138), "Rest 90 s · then 2 min": the rest between its sets (one length,
 * or "varies" when its sets each have their own), then the rest before the next exercise. Null when none is set.
 */
fun restSummary(res: Resources, p: PlannedExercise, setRests: Boolean = true): String? {
    // "Copy previous rest" (#151) is one choice for the whole exercise.
    if (Routines.copiesRest(p)) return res.getString(R.string.rest_summary_copy)
    val ownRests = if (setRests && p.fill == Routines.FILL_PLANNED) p.sets.mapNotNull { it.restSeconds }.distinct() else emptyList()
    val between = when {
        ownRests.size > 1 -> res.getString(R.string.rest_summary_varies)
        ownRests.size == 1 -> res.getString(R.string.rest_summary_between, restLabel(res, ownRests[0]))
        p.restSeconds != null -> res.getString(R.string.rest_summary_between, restLabel(res, p.restSeconds))
        else -> null
    }
    val after = p.restAfterSeconds?.let { res.getString(R.string.rest_summary_then, restLabel(res, it)) }
    return listOfNotNull(between, after).joinToString(" · ").ifEmpty { null }
}

// ---------------------------------------------------------------------------------------------------------
// One exercise's sets
// ---------------------------------------------------------------------------------------------------------

/** A prescribed set as the user types it, in their own units. */
private data class SetDraft(
    val weight: String = "",
    val reps: String = "",
    val distance: String = "",
    val time: String = "",
    val type: Int = SetTypes.WORKING,
    /** The rest after this set in seconds, or m:ss; blank for none of its own (#138). */
    val rest: String = "",
    /** The custom type's metric (#155); blank copies it from last time. */
    val metric: String = "",
    /** A Session's (#240) hard or live minutes as typed, its intensity, type and cardio zone; [time] is then in minutes. */
    val live: String = "",
    val intensity: Int? = null,
    val sessionType: Int? = null,
    val zone: Int? = null,
    /** A Session or Rounds set's own counts as typed (#266); blank for none. */
    val countA: String = "",
    val countB: String = ""
)

private fun num(s: String): Double = s.trim().replace(',', '.').toDoubleOrNull() ?: 0.0

/** Accepts `90`, `1:30` or `1:02:03`. */
private fun seconds(s: String): Int {
    val parts = s.trim().split(":").map { it.trim().toIntOrNull() ?: 0 }
    return when (parts.size) {
        0 -> 0
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        else -> parts[0] * 3600 + parts[1] * 60 + parts[2]
    }
}

/**
 * Chooses how an exercise's sets are filled when its day is logged, as FitNotes asks (#106): **Copy previous sets**,
 * **Use predefined sets** (a list of sets; a blank weight or reps copies it from last time), or **Don't populate any
 * sets**. The fields follow the exercise's type, as on the Track tab.
 *
 * Below them, its prescribed rest (#138): one rest for every set ("Same rest for every set", the only choice unless
 * its sets are predefined) or a rest per set, and the rest before the next exercise. "Default" leaves either one
 * unset, so the exercise's own rest and then the global one apply.
 */
@Composable
private fun PlannedSetsSheet(
    snap: Snapshot, planned: PlannedExercise, onDismiss: () -> Unit, onProfile: () -> Unit, onDone: (PlannedExercise) -> Unit
) {
    val ex = snap.exercises[planned.exerciseId]
    val type = ex?.type ?: ExerciseTypes.WEIGHT_REPS
    // A Session's length is typed in minutes, with its own fields (#240, #241).
    val isSession = ExerciseTypes.isSession(type)
    // Rounds (#246): the set's reps are its rounds, its time the round length and its rest the rest between rounds.
    val isRounds = ExerciseTypes.isRounds(type)
    // A user-defined type's own metric (#14), predefined like the other values (#155).
    val metricDef = ExerciseTypes.metricOf(type)
    val last = remember(snap.trainingKey, planned.exerciseId) {
        Routines.resolve(snap, planned.copy(fill = Routines.FILL_LAST), "9999-12-31")
    }
    var fill by remember { mutableStateOf(planned.fill) }
    val start = planned.sets.ifEmpty { last }.ifEmpty { listOf(PlannedSet()) }
    val rows = remember {
        mutableStateListOf<SetDraft>().apply {
            start.forEach { s ->
                val rd = if (isRounds) RoundsDraft.of(s.reps, s.durationSec, s.restSeconds, s.liveSec) else null
                add(
                    if (rd != null) SetDraft(reps = rd.rounds, time = rd.length, type = s.setType, rest = rd.rest, live = rd.hard, zone = s.zone, countA = countText(s.countA), countB = countText(s.countB)) else SetDraft(
                        weight = s.weightKg.takeIf { it != 0.0 }?.let { fmtNum(snap.weight(it, planned.exerciseId), 2) } ?: "",
                        reps = s.reps.takeIf { it > 0 }?.toString() ?: "",
                        distance = s.distance.takeIf { it > 0 }?.let { fmtNum(it, 2) } ?: "",
                        time = s.durationSec.takeIf { it > 0 }?.let { if (isSession) minutesText(it) else fmtDuration(it) } ?: "",
                        type = s.setType,
                        rest = s.restSeconds?.toString() ?: "",
                        metric = s.metric?.let { fmtNum(it, 2) } ?: "",
                        live = s.liveSec?.let { minutesText(it) } ?: "",
                        intensity = s.intensity,
                        sessionType = s.sessionType,
                        zone = s.zone,
                        countA = countText(s.countA),
                        countB = countText(s.countB)
                    )
                )
            }
        }
    }
    // Prescribed rest (#138). Per-set rests only exist for predefined sets.
    // "Copy previous rest" (#151) takes every rest from the exercise's most recent workout, as "Copy previous sets"
    // does for its sets.
    var copyRest by remember { mutableStateOf(Routines.copiesRest(planned)) }
    var sameRest by remember { mutableStateOf(planned.sets.none { it.restSeconds != null }) }
    var rest by remember { mutableStateOf(planned.restSeconds?.takeIf { it > 0 }) }
    var restAfter by remember { mutableStateOf(planned.restAfterSeconds?.takeIf { it > 0 }) }
    val fallbackRest = ex?.restSeconds ?: Settings.currentPortable().restSeconds
    val perSet = fill == Routines.FILL_PLANNED && !sameRest && !copyRest && !isRounds
    // What "Copy previous rest" would copy today, in the same words as the exercise row.
    val res = LocalContext.current.resources
    val previousRest = remember(snap.trainingKey, planned.exerciseId) {
        val copying = planned.copy(fill = Routines.FILL_LAST, restSeconds = Routines.REST_PREVIOUS, restAfterSeconds = Routines.REST_PREVIOUS)
        val r = Routines.resolveRest(snap, copying, "9999-12-31")
        restSummary(
            res,
            PlannedExercise(
                planned.exerciseId, Routines.FILL_PLANNED, Routines.resolve(snap, copying, "9999-12-31"),
                restSeconds = r.restSeconds, restAfterSeconds = r.restAfterSeconds
            )
        )
    }

    FitSheet(
        title = ex?.name ?: stringResource(R.string.workout_sets),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.workout_done),
        onConfirm = {
            // Set type rides along with each row; a row counts when it has any value.
            val sets = rows.map {
                if (isRounds) {
                    val d = RoundsDraft(it.reps, it.time, it.rest, it.live)
                    return@map PlannedSet(
                        reps = d.roundCount, durationSec = d.totalSec, setType = it.type, restSeconds = d.restSec.takeIf { r -> r > 0 },
                        liveSec = d.hardSec, zone = it.zone, countA = countValue(it.countA), countB = countValue(it.countB)
                    )
                }
                PlannedSet(
                    snap.toKg(num(it.weight), planned.exerciseId), it.reps.trim().toIntOrNull() ?: 0, num(it.distance),
                    if (isSession) minutesToSeconds(it.time) else seconds(it.time), it.type,
                    restSeconds = if (perSet) seconds(it.rest).takeIf { r -> r in REST_MIN..REST_MAX } else null,
                    // Blank is no value; 0 is a real one for a custom metric (#155).
                    metric = if (metricDef != null) it.metric.trim().replace(',', '.').toDoubleOrNull() else null,
                    // Hard or live minutes can't be longer than the session (#240).
                    liveSec = if (isSession && it.live.isNotBlank()) minutesToSeconds(it.live).coerceAtMost(minutesToSeconds(it.time)) else null,
                    intensity = if (isSession) it.intensity else null,
                    sessionType = if (isSession) it.sessionType else null,
                    zone = if (isSession) it.zone else null,
                    countA = if (isSession) countValue(it.countA) else null,
                    countB = if (isSession) countValue(it.countB) else null
                )
            }.filter { !it.isEmpty }
            onDone(
                planned.copy(
                    fill = fill,
                    sets = if (fill == Routines.FILL_PLANNED) sets else planned.sets.map { if (isRounds) it else it.copy(restSeconds = null) },
                    restSeconds = if (copyRest) Routines.REST_PREVIOUS else if (perSet) null else rest,
                    restAfterSeconds = if (copyRest) Routines.REST_PREVIOUS else restAfter
                )
            )
        }
    ) {
        Text(
            stringResource(R.string.workout_fill_question),
            style = MaterialTheme.typography.bodyLarge
        )
        FillChoice(
            stringResource(R.string.workout_fill_last),
            if (last.isEmpty()) stringResource(R.string.workout_fill_last_none)
            else stringResource(R.string.workout_fill_last_body, Routines.describe(res, snap, last, planned.exerciseId)),
            fill == Routines.FILL_LAST
        ) { fill = Routines.FILL_LAST }
        FillChoice(stringResource(R.string.workout_fill_planned), stringResource(R.string.workout_fill_planned_body), fill == Routines.FILL_PLANNED) {
            fill = Routines.FILL_PLANNED
        }
        FillChoice(stringResource(R.string.workout_fill_none), stringResource(R.string.workout_fill_none_body), fill == Routines.FILL_NONE) {
            fill = Routines.FILL_NONE
        }
        if (fill == Routines.FILL_PLANNED) {
            Text(
                stringResource(R.string.workout_fill_blank_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val unit = snap.weightUnitOf(planned.exerciseId)
            val wStep = ex?.weightStepKg?.let { snap.weight(it, planned.exerciseId) } ?: 2.5
            rows.forEachIndexed { i, r ->
                // FitNotes's SET N block (its screenshot 20): the set's number and ⋮, then a stepper per field.
                Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.workout_set_n, i + 1).uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    OverflowMenu(
                        listOf(
                            MenuAction(stringResource(R.string.workout_duplicate_set)) { rows.add(i + 1, r) },
                            MenuAction(stringResource(R.string.workout_remove_set), enabled = rows.size > 1) { rows.removeAt(i) }
                        ),
                        description = stringResource(R.string.workout_set_options, i + 1)
                    )
                }
                HorizontalDivider(color = Brand.Hairline)
                fun step(text: String, by: Double, digits: Int) = fmtNum(kotlin.math.max(0.0, num(text) + by), digits).let { if (it == "0") "" else it }
                if (ExerciseTypes.usesWeight(type)) {
                    StepperField(stringResource(R.string.workout_field_weight, unit), r.weight, { rows[i] = rows[i].copy(weight = it) }, { d -> rows[i] = rows[i].copy(weight = step(rows[i].weight, d * wStep, 2)) })
                }
                if (ExerciseTypes.usesReps(type)) {
                    StepperField(stringResource(R.string.workout_field_reps), r.reps, { rows[i] = rows[i].copy(reps = it) }, { d -> rows[i] = rows[i].copy(reps = step(rows[i].reps, d.toDouble(), 0)) }, keyboard = KeyboardType.Number)
                }
                if (ExerciseTypes.usesDistance(type)) {
                    StepperField(stringResource(R.string.workout_field_distance, snap.distanceUnit(planned.exerciseId)), r.distance, { rows[i] = rows[i].copy(distance = it) }, { d -> rows[i] = rows[i].copy(distance = step(rows[i].distance, d * 0.5, 2)) })
                }
                if (isSession) {
                    SessionFields(
                        date = com.fitlens.companion.data.Dates.today(),
                        durationText = r.time, onDuration = { rows[i] = rows[i].copy(time = it) },
                        liveText = r.live, onLive = { rows[i] = rows[i].copy(live = it) },
                        intensity = r.intensity, onIntensity = { rows[i] = rows[i].copy(intensity = it) },
                        sessionType = r.sessionType, onSessionType = { rows[i] = rows[i].copy(sessionType = it) },
                        zone = r.zone, onZone = { rows[i] = rows[i].copy(zone = it) },
                        onOpenProfile = onProfile,
                        fields = ex?.sessionFields
                    )
                }
                if (isRounds) {
                    RoundsFields(
                        date = com.fitlens.companion.data.Dates.today(),
                        draft = RoundsDraft(r.reps, r.time, r.rest, r.live),
                        onDraft = { d -> rows[i] = rows[i].copy(reps = d.rounds, time = d.length, rest = d.rest, live = d.hard) },
                        zone = r.zone, onZone = { rows[i] = rows[i].copy(zone = it) },
                        onOpenProfile = onProfile
                    )
                }
                if (isSession || isRounds) CountFields(
                    names = ex?.countNames.orEmpty(),
                    aText = r.countA, onA = { rows[i] = rows[i].copy(countA = it) },
                    bText = r.countB, onB = { rows[i] = rows[i].copy(countB = it) }
                )
                if (!isSession && !isRounds && ExerciseTypes.usesDuration(type)) {
                    StepperField(stringResource(R.string.workout_field_time), r.time, { rows[i] = rows[i].copy(time = it) }, { d ->
                        val next = kotlin.math.max(0, seconds(rows[i].time) + d * 15)
                        rows[i] = rows[i].copy(time = if (next == 0) "" else fmtDuration(next))
                    }, keyboard = KeyboardType.Text)
                }
                if (metricDef != null) {
                    // As on the Track tab: the metric's name and unit, stepping by 1.
                    val metricUnit = metricDef.metricUnit?.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
                    StepperField(metricDef.metricName.orEmpty() + metricUnit, r.metric, { rows[i] = rows[i].copy(metric = it) }, { d ->
                        rows[i] = rows[i].copy(metric = fmtNum(kotlin.math.max(0.0, num(rows[i].metric) + d), 2))
                    })
                }
                if (perSet) {
                    StepperField(stringResource(R.string.workout_field_rest), r.rest, { t -> rows[i] = rows[i].copy(rest = t.filter { c -> c.isDigit() }) }, { d ->
                        rows[i] = rows[i].copy(rest = (kotlin.math.max(0, (rows[i].rest.toIntOrNull() ?: 0) + d * 5)).takeIf { it > 0 }?.toString() ?: "")
                    }, keyboard = KeyboardType.Number)
                }
            }
            // FitNotes's ADD SET + row.
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Spacing.touch)
                    .clickable(onClickLabel = stringResource(R.string.workout_add_set)) { rows.add(rows.lastOrNull() ?: SetDraft()) }
                    .padding(vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.workout_add_set).uppercase(), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(Icons.Filled.Add, contentDescription = null, tint = Brand.Gold)
            }
        }
        SectionLabel(stringResource(R.string.workout_rest), Modifier.padding(top = Spacing.md))
        // The same choice as the sets above (#151): copy last time's rest, or set it here.
        FillChoice(
            stringResource(R.string.workout_rest_copy),
            previousRest?.let { stringResource(R.string.workout_rest_copy_body, it.replaceFirstChar { c -> c.lowercase() }) }
                ?: stringResource(R.string.workout_rest_copy_none, restLabel(res, fallbackRest)),
            copyRest
        ) { copyRest = true }
        FillChoice(stringResource(R.string.workout_rest_set), stringResource(R.string.workout_rest_set_body), !copyRest) { copyRest = false }
        if (!copyRest) {
            if (fill == Routines.FILL_PLANNED && !isRounds) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.workout_rest_same), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = sameRest, onCheckedChange = { sameRest = it })
                }
            }
            if (perSet) {
                Text(
                    stringResource(R.string.workout_rest_per_set_hint, restLabel(res, fallbackRest)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                RestLengthStepper(
                    seconds = rest ?: fallbackRest,
                    onChange = { rest = it },
                    label = stringResource(R.string.workout_rest_between),
                    isDefault = rest == null,
                    onDefault = { rest = null }
                )
            }
            RestLengthStepper(
                seconds = restAfter ?: rest ?: fallbackRest,
                onChange = { restAfter = it },
                label = stringResource(R.string.workout_rest_after),
                isDefault = restAfter == null,
                onDefault = { restAfter = null }
            )
        }
    }
}

/**
 * "Set rest for every exercise" on a day's menu (#138): one rest between sets and one before the next exercise, for
 * every exercise of the day. "Default" leaves either unset; "Copy previous rest" (#151) copies each one's last rest.
 */
@Composable
private fun DayRestSheet(dayName: String, onDismiss: () -> Unit, onDone: (Int?, Int?) -> Unit) {
    val fallback = Settings.currentPortable().restSeconds
    var rest by remember { mutableStateOf<Int?>(null) }
    var after by remember { mutableStateOf<Int?>(null) }
    // "Copy previous rest" (#151) for the whole day: each exercise takes its rest from its own last workout.
    var copyRest by remember { mutableStateOf(false) }
    FitSheet(
        title = stringResource(R.string.workout_day_rest_title, dayName),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.workout_day_rest_confirm),
        onConfirm = { if (copyRest) onDone(Routines.REST_PREVIOUS, Routines.REST_PREVIOUS) else onDone(rest, after) }
    ) {
        Text(
            stringResource(R.string.workout_day_rest_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FillChoice(stringResource(R.string.workout_rest_copy), stringResource(R.string.workout_day_rest_copy_body), copyRest) { copyRest = true }
        FillChoice(stringResource(R.string.workout_rest_set), stringResource(R.string.workout_day_rest_set_body), !copyRest) { copyRest = false }
        if (!copyRest) {
            RestLengthStepper(
                seconds = rest ?: fallback,
                onChange = { rest = it },
                label = stringResource(R.string.workout_rest_between),
                isDefault = rest == null,
                onDefault = { rest = null }
            )
            RestLengthStepper(
                seconds = after ?: rest ?: fallback,
                onChange = { after = it },
                label = stringResource(R.string.workout_rest_after),
                isDefault = after == null,
                onDefault = { after = null }
            )
        }
    }
}

/** One of FitNotes's fill choices (its screenshot 23): a radio button, the choice and what it does. */
@Composable
private fun FillChoice(title: String, description: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
