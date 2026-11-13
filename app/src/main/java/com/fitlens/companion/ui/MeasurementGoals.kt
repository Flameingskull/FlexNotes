package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import com.fitlens.companion.data.Settings
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.MeasurementGoals
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.ListRowWithMenu
import kotlinx.coroutines.launch
import com.fitlens.companion.ui.design.FitFilterChip

/**
 * Whether a move from [from] to [to] in this measurement is good (true), bad (false) or has no colour (null) (#330):
 * the measurement's own direction decides, including closer to or further from a specific value; with no direction
 * [plainNoGoal] (the user's Settings choice) decides. A null [def] has no direction. One rule for every screen.
 */
fun measurementVerdict(def: MeasurementDef?, plainNoGoal: Boolean, from: Double, to: Double): Boolean? =
    MeasurementGoals.verdict(def?.goalType ?: MeasurementGoals.NONE, def?.goalValue ?: 0.0, from, to, plainNoGoal)

/**
 * A measurement's good-or-bad rule for its graph, with the key that explains it, as one value (#370). The move colours
 * are [measurementVerdict]'s. The key says "Closer / Further" (TARGET) for a specific value with a target typed, taken
 * from [def] and not from probing the rule; otherwise it follows the rule's direction ([chartKeyMode]).
 */
fun measurementRule(def: MeasurementDef?, plainNoGoal: Boolean): MoveRule {
    val verdict: (Double, Double) -> Boolean? = { from, to -> measurementVerdict(def, plainNoGoal, from, to) }
    val specificValue = def != null && def.goalType == MeasurementGoals.TARGET && def.goalValue > 0
    return MoveRule(if (specificValue) ChartKeyMode.TARGET else chartKeyMode(verdict), verdict)
}

/** The user's choice for measurements with no direction (#330): true = plain, false = green for a rise and red for a fall. Changes show at once. */
@Composable
fun plainWhenNoGoal(): Boolean = Settings.portable.collectAsState().value.plainNoGoal

/** The user's choice of what a body value's change is measured from (#372): true = the last value on an earlier day (the default). Changes show at once. */
@Composable
fun changeFromEarlierDay(): Boolean = Settings.portable.collectAsState().value.changeFromEarlierDay

/**
 * The value [record]'s change is measured from (#372), or null for the first one. One rule for the Track row, History
 * and the day log, so a value is coloured the same way on each: with [fromEarlierDay] the last value of [history] on
 * an earlier day than [record]'s; otherwise the value just before it in [history], even on the same day.
 * [history] is one measurement's records, oldest first.
 */
fun previousValue(history: List<MRecord>, record: MRecord, fromEarlierDay: Boolean): MRecord? =
    if (fromEarlierDay) history.lastOrNull { it.date.take(10) < record.date.take(10) }
    else history.getOrNull(history.indexOfFirst { it.id == record.id } - 1)

/**
 * The colour of a change between two values of a measurement (#27, #330): green when it is good, red when it is bad
 * ([measurementVerdict]), the normal text colour otherwise (unchanged, or plain by the user's choice). The signed
 * number and arrow next to it still carry the meaning, so colour is never the only signal. [plainNoGoal] is the
 * user's Settings choice, read once per pane by the caller ([plainWhenNoGoal]) so no row reads it again (#370).
 */
@Composable
fun changeColour(def: MeasurementDef?, plainNoGoal: Boolean, from: Double, to: Double): Color =
    verdictColour(measurementVerdict(def, plainNoGoal, from, to), MaterialTheme.colorScheme.onSurface)

/** The colour for a verdict (#330): green for good, red for bad, [none] for no colour. The one place a verdict becomes a colour. */
fun verdictColour(good: Boolean?, none: Color): Color = when (good) {
    true -> Brand.Rise
    false -> Brand.Fall
    null -> none
}

/**
 * A change between two values of a measurement, in full figures (#120) and short enough to sit on one line (#128):
 * the direction, the amount in its unit, since when, and the value it moved from, "▼ 0.7 kg since 21 Aug · was
 * 113.25 kg". An unchanged value reads "No change since 18 Sept". Never a bare number or a percentage.
 */
fun changeText(res: Resources, prev: MRecord, now: MRecord): String {
    val d = now.value - prev.value
    val unit = now.unit.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
    val since = Dates.day(prev.date)
    if (d == 0.0) return res.getString(R.string.mg_no_change, since)
    val arrow = if (d > 0) "▲" else "▼"
    return res.getString(R.string.mg_change, arrow, "${fmtNum(kotlin.math.abs(d))}$unit", since, "${fmtNum(prev.value)}$unit")
}

/** A short description of a measurement's goal, worded as the Track tab's line: "Which way is good? Down, to 80 kg" (#330). */
fun goalText(res: Resources, def: MeasurementDef?): String = res.getString(R.string.mg_ask, goalAnswer(res, def))

/** A goal type's answer to "Which way is good?" on its chip: "Up is good", "A specific value", "Neither". */
private fun goalTypeText(res: Resources, t: Int): String = res.getString(
    when (t) {
        MeasurementGoals.INCREASE -> R.string.mg_type_increase
        MeasurementGoals.DECREASE -> R.string.mg_type_decrease
        MeasurementGoals.TARGET -> R.string.mg_type_target
        else -> R.string.mg_type_none
    }
)

/** The answer to "Which way is good?" for a measurement, in a few words: "Up", "Up, to 85 kg", "Down", "Closer to 85 kg" or "Not set". */
fun goalAnswer(res: Resources, def: MeasurementDef?): String {
    val target = def?.let { "${fmtNum(it.goalValue)} ${it.unit}".trim() }.orEmpty()
    val hasTarget = (def?.goalValue ?: 0.0) > 0
    return when (def?.goalType) {
        MeasurementGoals.INCREASE -> if (hasTarget) res.getString(R.string.mg_answer_up_to, target) else res.getString(R.string.mg_answer_up)
        MeasurementGoals.DECREASE -> if (hasTarget) res.getString(R.string.mg_answer_down_to, target) else res.getString(R.string.mg_answer_down)
        MeasurementGoals.TARGET -> if (hasTarget) res.getString(R.string.mg_answer_target, target) else res.getString(R.string.mg_answer_none)
        else -> res.getString(R.string.mg_answer_none)
    }
}

/**
 * A line under a measurement's figures that says which way is good and opens the sheet to change it (#330), so a value's
 * colour can be explained and changed in one tap from the Track tab, History and Graph. Draws nothing without a [def].
 */
@Composable
fun GoalLine(def: MeasurementDef?, modifier: Modifier = Modifier) {
    if (def == null) return
    val res = LocalContext.current.resources
    var open by rememberSaveable { mutableStateOf(false) }
    Text(
        stringResource(R.string.mg_ask, goalAnswer(res, def)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.touch)
            .clickable(onClickLabel = stringResource(R.string.mg_ask_action)) { open = true }
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
    )
    if (open) MeasurementGoalSheet(def) { open = false }
}

/**
 * Sets a measurement's goal: increase, decrease or a specific value, with an optional target for the first two.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeasurementGoalSheet(def: MeasurementDef, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    var type by rememberSaveable { mutableIntStateOf(def.goalType.takeIf { it in MeasurementGoals.all } ?: MeasurementGoals.NONE) }
    var text by rememberSaveable { mutableStateOf(if (def.goalValue > 0) fmtNum(def.goalValue) else "") }
    // The no-direction colour choice (#330) is held here and written with Save, so Cancel keeps the old value.
    val savedPlain = plainWhenNoGoal()
    var plain by rememberSaveable { mutableStateOf(savedPlain) }
    // What a body value's change is measured from (#372), held here and written with Save, like the choice above.
    val savedFromDay = changeFromEarlierDay()
    var fromDay by rememberSaveable { mutableStateOf(savedFromDay) }
    val value = text.trim().replace(',', '.').toDoubleOrNull()
    val needsValue = type == MeasurementGoals.TARGET
    FitSheet(
        title = stringResource(R.string.mg_title, def.name),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.goals_save),
        confirmEnabled = !needsValue || (value != null && value > 0),
        onConfirm = {
            val t = type
            val v = if (t == MeasurementGoals.NONE) 0.0 else value ?: 0.0
            AppScope.scope.launch { Store.setMeasurementGoal(def.name, def.unit, t, v) }
            if (t == MeasurementGoals.NONE) Settings.updatePortable { it.copy(plainNoGoal = plain) }
            if (!def.medication && fromDay != savedFromDay) Settings.updatePortable { it.copy(changeFromEarlierDay = fromDay) }
            onDismiss()
        }
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(MeasurementGoals.INCREASE, MeasurementGoals.DECREASE, MeasurementGoals.TARGET, MeasurementGoals.NONE).forEach { t ->
                FitFilterChip(selected = type == t, onClick = { type = t }, label = { Text(goalTypeText(res, t)) })
            }
        }
        if (type != MeasurementGoals.NONE) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(if (needsValue) R.string.goals_target_unit else R.string.mg_target_optional, def.unit)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (type == MeasurementGoals.NONE) {
            // One preference for every measurement with no direction; the same choice is in Settings (#330).
            Text(stringResource(R.string.mg_plain_title), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FitFilterChip(
                    selected = !plain,
                    onClick = { plain = false },
                    label = { Text(stringResource(R.string.mg_plain_off)) }
                )
                FitFilterChip(
                    selected = plain,
                    onClick = { plain = true },
                    label = { Text(stringResource(R.string.mg_plain_on)) }
                )
            }
            Text(
                stringResource(R.string.mg_plain_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!def.medication) {
            // The same choice as Settings: what the change under a body value is measured from (#372).
            Text(stringResource(R.string.settings_change_from), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FitFilterChip(selected = fromDay, onClick = { fromDay = true }, label = { Text(stringResource(R.string.change_from_day)) })
                FitFilterChip(selected = !fromDay, onClick = { fromDay = false }, label = { Text(stringResource(R.string.change_from_value)) })
            }
        }
        Text(
            stringResource(R.string.mg_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
