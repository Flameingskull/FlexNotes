package com.fitlens.companion.ui

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.BlockState
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.PhaseUnit
import com.fitlens.companion.data.PlannedSet
import com.fitlens.companion.data.Routine
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.TrainingBlock
import com.fitlens.companion.data.TrainingBlocks
import com.fitlens.companion.data.TrainingPlan
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.ListRowWithMenu
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SegmentedSwitch
import com.fitlens.companion.ui.design.SettingsSwitchRow
import com.fitlens.companion.ui.design.StepperField

/**
 * Training blocks in the workout editor (#218): the plan's start date, its blocks (tap to edit; ⋮ to duplicate or
 * delete; drag handles while [reordering]), + Add block, and whether it starts again after the last block. With no
 * plan, a short explanation and Add training blocks, which starts from three weeks of building and a deload week.
 * Changes go into the editor's draft through [onChange] and are written with the workout on Save.
 */
@Composable
internal fun TrainingBlocksSection(plan: TrainingPlan?, reordering: Boolean, onChange: (TrainingPlan?) -> Unit) {
    val res = LocalContext.current.resources
    var editing by remember { mutableStateOf<Int?>(null) }
    var pickingStart by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        SectionLabel(stringResource(R.string.tb_title))
        if (plan == null) {
            Text(
                stringResource(R.string.tb_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = Spacing.sm)
            )
            GlassOutlinedButton(onClick = {
                onChange(TrainingBlocks.starter(Dates.today(), res.getString(R.string.tb_build), res.getString(R.string.tb_deload)))
            }) { Text(stringResource(R.string.tb_add_plan)) }
            return@Column
        }
        val blocks = plan.blocks
        fun setBlocks(list: List<TrainingBlock>) =
            onChange(if (list.isEmpty()) null else plan.copy(blocks = TrainingBlocks.normalised(list), repeats = plan.repeats && list.last().unit != PhaseUnit.ONGOING))
        ListRowWithMenu(
            title = stringResource(R.string.tb_starts, Dates.day(plan.start)),
            subtitle = planStatus(res, plan, Dates.today()),
            onClick = { pickingStart = true }
        )
        blocks.forEachIndexed { i, b ->
            ListRowWithMenu(
                title = blockName(res, b, i),
                subtitle = blockSummary(res, b),
                onClick = { editing = i },
                menu = listOf(
                    MenuAction(stringResource(R.string.tb_edit_block)) { editing = i },
                    MenuAction(stringResource(R.string.tb_duplicate_block)) {
                        setBlocks(blocks.toMutableList().apply { add(i + 1, b.copy(unit = if (b.unit == PhaseUnit.ONGOING) PhaseUnit.WEEKS else b.unit)) })
                    },
                    MenuAction(stringResource(R.string.tb_delete_block)) { setBlocks(blocks.filterIndexed { j, _ -> j != i }) }
                ),
                onMoveUp = if (reordering && i > 0) ({ setBlocks(blocks.toMutableList().apply { add(i - 1, removeAt(i)) }) }) else null,
                onMoveDown = if (reordering && i < blocks.lastIndex) ({ setBlocks(blocks.toMutableList().apply { add(i + 1, removeAt(i)) }) }) else null
            )
        }
        TextButton(onClick = { editing = blocks.size }, modifier = Modifier.heightIn(min = Spacing.touch)) {
            Text(stringResource(R.string.tb_add_block))
        }
        if (!plan.ongoing) {
            SettingsSwitchRow(
                title = stringResource(R.string.tb_repeat),
                summary = stringResource(R.string.tb_repeat_summary),
                checked = plan.repeats
            ) { onChange(plan.copy(repeats = it)) }
        }
        TextButton(onClick = { onChange(null) }, modifier = Modifier.heightIn(min = Spacing.touch)) {
            Text(stringResource(R.string.tb_remove_plan))
        }
    }

    val at = editing
    if (plan != null && at != null) {
        val adding = at >= plan.blocks.size
        val block = plan.blocks.getOrNull(at) ?: TrainingBlock(res.getString(R.string.tb_block_n, at + 1), 4, PhaseUnit.WEEKS)
        BlockSheet(
            block = block,
            adding = adding,
            canBeOngoing = at >= plan.blocks.lastIndex,
            onDismiss = { editing = null }
        ) { changed ->
            editing = null
            val list = plan.blocks.toMutableList()
            if (adding) list.add(changed) else list[at] = changed
            onChange(plan.copy(blocks = TrainingBlocks.normalised(list), repeats = plan.repeats && list.last().unit != PhaseUnit.ONGOING))
        }
    }
    if (plan != null && pickingStart) {
        PickDateDialog(plan.start, onDismiss = { pickingStart = false }) { onChange(plan.copy(start = it)) }
    }
}

/** One block's name, length and unit, and whether it's a deload. Ongoing is offered only for the last block. */
@Composable
private fun BlockSheet(block: TrainingBlock, adding: Boolean, canBeOngoing: Boolean, onDismiss: () -> Unit, onDone: (TrainingBlock) -> Unit) {
    var name by remember(block) { mutableStateOf(block.name) }
    var length by remember(block) { mutableStateOf(block.length.toString()) }
    var unit by remember(block) { mutableStateOf(if (!canBeOngoing && block.unit == PhaseUnit.ONGOING) PhaseUnit.WEEKS else block.unit) }
    var deload by remember(block) { mutableStateOf(block.deload) }
    // Changing the planned sets (#235): off by default; turned on, a deload starts at 10% lighter and one set fewer.
    var changing by remember(block) { mutableStateOf(block.adjusts) }
    var pct by remember(block) { mutableStateOf(block.weightPct.toString()) }
    var sets by remember(block) { mutableStateOf(block.setChange.toString()) }
    val units = TrainingBlocks.UNITS.filter { canBeOngoing || it != PhaseUnit.ONGOING }
    val n = length.toIntOrNull()
    val pctValue = pct.toIntOrNull()
    val setsValue = sets.toIntOrNull()
    val valid = (unit == PhaseUnit.ONGOING || (n != null && n in 1..999)) &&
        (!changing || (pctValue != null && pctValue in TrainingBlocks.WEIGHT_PCT && setsValue != null && setsValue in TrainingBlocks.SET_CHANGE))
    FitSheet(
        title = stringResource(if (adding) R.string.tb_new_block else R.string.tb_edit_block),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.tb_done),
        confirmEnabled = valid,
        onConfirm = {
            onDone(
                TrainingBlock(
                    name, if (unit == PhaseUnit.ONGOING) 1 else n ?: 1, unit, deload,
                    weightPct = if (changing) pctValue ?: 0 else 0,
                    setChange = if (changing) setsValue ?: 0 else 0
                )
            )
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(TrainingBlocks.MAX_NAME) },
                label = { Text(stringResource(R.string.tb_block_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SegmentedSwitch(
                options = units.map { unitLabel(it) },
                selected = units.indexOf(unit).coerceAtLeast(0),
                onSelect = { unit = units[it] }
            )
            if (unit != PhaseUnit.ONGOING) {
                StepperField(
                    label = stringResource(R.string.tb_length),
                    value = length,
                    onValue = { v -> length = v.filter { it.isDigit() }.take(3) },
                    onStep = { d -> length = ((length.toIntOrNull() ?: 0) + d).coerceIn(1, 999).toString() },
                    keyboard = KeyboardType.Number
                )
            } else {
                Text(stringResource(R.string.tb_ongoing_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SettingsSwitchRow(
                title = stringResource(R.string.tb_deload_switch),
                summary = stringResource(R.string.tb_deload_summary),
                checked = deload
            ) { deload = it }
            SettingsSwitchRow(
                title = stringResource(R.string.tb_change_switch),
                summary = stringResource(R.string.tb_change_summary),
                checked = changing
            ) { on ->
                changing = on
                if (on && pct.toIntOrNull() == 0 && sets.toIntOrNull() == 0 && deload) {
                    pct = "-10"
                    sets = "-1"
                }
            }
            if (changing) {
                StepperField(
                    label = stringResource(R.string.tb_weight_change),
                    value = pct,
                    onValue = { v -> pct = signedDigits(v, 3) },
                    onStep = { d -> pct = ((pct.toIntOrNull() ?: 0) + d * 5).coerceIn(TrainingBlocks.WEIGHT_PCT).toString() },
                    keyboard = KeyboardType.Number
                )
                StepperField(
                    label = stringResource(R.string.tb_set_change),
                    value = sets,
                    onValue = { v -> sets = signedDigits(v, 2) },
                    onStep = { d -> sets = ((sets.toIntOrNull() ?: 0) + d).coerceIn(TrainingBlocks.SET_CHANGE).toString() },
                    keyboard = KeyboardType.Number
                )
            }
        }
    }
}

/** Typed text kept to an optional leading minus and up to [digits] digits. */
private fun signedDigits(v: String, digits: Int): String {
    val minus = v.trimStart().startsWith("-") || v.trimStart().startsWith("−")
    return (if (minus) "-" else "") + v.filter { it.isDigit() }.take(digits)
}

/** What a block does to the planned sets (#235), such as "weights 10% lighter, 1 set fewer per exercise", or null. */
internal fun blockChangeText(res: Resources, b: TrainingBlock): String? {
    if (!b.adjusts) return null
    val parts = ArrayList<String>()
    if (b.weightPct < 0) parts += res.getString(R.string.tb_weights_down, -b.weightPct)
    if (b.weightPct > 0) parts += res.getString(R.string.tb_weights_up, b.weightPct)
    if (b.setChange < 0) parts += res.getQuantityString(R.plurals.tb_sets_fewer, -b.setChange, -b.setChange)
    if (b.setChange > 0) parts += res.getQuantityString(R.plurals.tb_sets_more, b.setChange, b.setChange)
    return parts.joinToString(", ")
}

/** The training block that changes [workoutId]'s planned sets on [date] (#235), or null when none does. */
internal fun adjustingBlock(snap: Snapshot, workoutId: Long, date: String): TrainingBlock? =
    snap.routinesById[workoutId]?.plan?.let { TrainingBlocks.on(it, date) }?.block?.takeIf { it.adjusts }

/**
 * [exerciseId]'s planned [sets] as [block] changes them (#235), each weight rounded to the exercise's + and − step in
 * its own unit (#15, #7), as set entry steps it. No block leaves them as they are.
 */
internal fun blockAdjusted(snap: Snapshot, block: TrainingBlock?, exerciseId: Long, sets: List<PlannedSet>): List<PlannedSet> {
    if (block == null) return sets
    val globalStepKg = Settings.currentPortable().weightIncrementKg?.takeIf { snap.weightUnitOf(exerciseId) == snap.weightUnit }
    val step = (snap.exercises[exerciseId]?.weightStepKg ?: globalStepKg)?.let { snap.weight(it, exerciseId) } ?: DEFAULT_WEIGHT_STEP
    return TrainingBlocks.adjust(sets, block) { kg -> snap.toKg(TrainingBlocks.roundTo(snap.weight(kg, exerciseId), step), exerciseId) }
}

@Composable
private fun unitLabel(unit: PhaseUnit): String = stringResource(
    when (unit) {
        PhaseUnit.DAYS -> R.string.tb_unit_days
        PhaseUnit.ONGOING -> R.string.tb_unit_ongoing
        else -> R.string.tb_unit_weeks
    }
)

/** A block's name, or "Block 2" when it has none. */
internal fun blockName(res: Resources, b: TrainingBlock, index: Int): String =
    b.name.ifBlank { res.getString(R.string.tb_block_n, index + 1) }

/** "3 weeks", or "1 week · Deload". */
private fun blockSummary(res: Resources, b: TrainingBlock): String {
    val length = phaseLength(res, b.length, b.unit)
    val base = if (b.deload) res.getString(R.string.tb_summary_deload, length) else length
    return blockChangeText(res, b)?.let { res.getString(R.string.tb_summary_change, base, it) } ?: base
}

/** Where the plan is on [date]: "Today: Build, week 2 of 3", "Starts on …" or "Finished". */
private fun planStatus(res: Resources, plan: TrainingPlan, date: String): String {
    if (plan.start > date) return res.getString(R.string.tb_not_started)
    val st = TrainingBlocks.on(plan, date) ?: return res.getString(R.string.tb_finished)
    return res.getString(R.string.tb_today, blockStateText(res, st))
}

/** "Build · week 2 of 3, until Sun 9 Nov", "Deload until Sun 2 Nov" (#237: a date, not a day count), or "Maintain · since Mon 27 Oct" for an ongoing block. */
internal fun blockStateText(res: Resources, st: BlockState): String {
    val name = blockName(res, st.block, st.index - 1)
    val weeks = st.weeks
    val days = st.days
    return when {
        days == null ->
            if (st.firstDay.isNotBlank()) res.getString(R.string.tb_state_since, name, com.fitlens.companion.data.Dates.day(st.firstDay))
            else name
        days % 7 == 0L && weeks != null && weeks > 1 && st.lastDay != null ->
            res.getString(R.string.tb_state_week, name, st.week, weeks, com.fitlens.companion.data.Dates.day(st.lastDay))
        st.lastDay != null -> res.getString(R.string.tb_state_day, name, com.fitlens.companion.data.Dates.day(st.lastDay))
        else -> name
    }
}

/**
 * The day log's training block card (#218): for each workout with blocks running on [date], its name, the block and
 * how far in, a deload hint and what comes next. Tapping opens the workout's editor, where the blocks are changed.
 */
@Composable
internal fun TrainingBlockCard(routine: Routine, st: BlockState, onOpen: () -> Unit) {
    val res = LocalContext.current.resources
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .background(Brand.Surface, FitShapes.card)
            .border(1.dp, if (st.block.deload) Brand.Gold else Brand.Hairline, FitShapes.card)
            .clickable(onClickLabel = stringResource(R.string.tb_card_open, routine.name)) { onOpen() }
            .semantics(mergeDescendants = true) {}
            .heightIn(min = Spacing.row)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Text(
            stringResource(R.string.tb_card_label, routine.name).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(blockStateText(res, st), style = MaterialTheme.typography.titleMedium, color = if (st.block.deload) Brand.Gold else MaterialTheme.colorScheme.onSurface)
        // A block that changes the planned sets (#235) says what it does; a plain deload reminds to go lighter.
        val change = blockChangeText(res, st.block)
        val nextLine = when {
            change != null -> stringResource(R.string.tb_card_change, change)
            st.block.deload -> stringResource(R.string.tb_card_deload)
            else -> null
        }
        nextLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val next = st.next
        val last = st.lastDay
        if (next != null && last != null) {
            val from = Dates.parse(last)?.plusDays(1)?.format(Dates.ISO)
            if (from != null) {
                val nextIndex = if (st.index < st.count) st.index else 0
                Text(
                    stringResource(R.string.tb_card_next, blockName(res, next, nextIndex), Dates.day(from)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
