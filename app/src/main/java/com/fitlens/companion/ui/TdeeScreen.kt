package com.fitlens.companion.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.fitlens.companion.R
import com.fitlens.companion.data.BodyFat
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Energy
import com.fitlens.companion.data.Energy.Goal
import com.fitlens.companion.data.EnergyEstimates
import com.fitlens.companion.data.Intake
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.GoldButton
import com.fitlens.companion.ui.design.SettingsActionRow
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsGroup
import com.fitlens.companion.ui.design.SettingsNote
import com.fitlens.companion.ui.design.SettingsNumberRow
import java.util.Locale
import kotlin.math.roundToInt

/** Kilocalories with thousands separators, whole: "2,640". */
internal fun kcalText(kcal: Double): String = String.format(Locale.getDefault(), "%,d", kcal.roundToInt())

internal fun formulaText(res: Resources, f: Energy.Formula): String = res.getString(
    when (f) {
        Energy.Formula.AUTO -> R.string.tdee_formula_auto
        Energy.Formula.MIFFLIN -> R.string.tdee_formula_mifflin
        Energy.Formula.KATCH -> R.string.tdee_formula_katch
    }
)

internal fun effortText(res: Resources, e: Energy.Effort): String = res.getString(
    when (e) {
        Energy.Effort.LIGHT -> R.string.tdee_effort_light
        Energy.Effort.MODERATE -> R.string.tdee_effort_moderate
        Energy.Effort.VIGOROUS -> R.string.tdee_effort_vigorous
    }
)

internal fun missingText(res: Resources, m: Energy.Missing): String = res.getString(
    when (m) {
        Energy.Missing.SEX -> R.string.tdee_missing_sex
        Energy.Missing.AGE -> R.string.tdee_missing_age
        Energy.Missing.HEIGHT -> R.string.tdee_missing_height
        Energy.Missing.BODYWEIGHT -> R.string.tdee_missing_bodyweight
        Energy.Missing.LIFESTYLE -> R.string.tdee_missing_lifestyle
    }
)

internal fun goalText(res: Resources, g: Goal): String = when (g) {
    Goal.Maintain -> res.getString(R.string.tdee_goal_maintain)
    is Goal.LoseRate -> res.getString(R.string.tdee_goal_rate, fmtNum(g.percentPerWeek, 2))
    is Goal.LoseDeficit -> res.getString(R.string.tdee_goal_deficit, g.percent)
    Goal.Recomp -> res.getString(R.string.tdee_goal_recomp)
    Goal.LeanBulk -> res.getString(R.string.tdee_goal_lean)
    Goal.Bulk -> res.getString(R.string.tdee_goal_bulk)
    is Goal.Custom -> res.getString(R.string.tdee_goal_custom)
}

internal fun warningText(res: Resources, w: Energy.Warning, sex: BodyFat.Sex?): String = when (w) {
    Energy.Warning.BELOW_BMR -> res.getString(R.string.tdee_warn_bmr)
    Energy.Warning.BELOW_FLOOR -> res.getString(R.string.tdee_warn_floor, kcalText(Energy.floorKcal(sex).toDouble()))
    Energy.Warning.FAST_LOSS -> res.getString(R.string.tdee_warn_fast)
}

/** The rest after a set, as the exercise screen times it ([restFor]), for the energy estimates. */
internal fun energyRest(snap: Snapshot) = EnergyEstimates.Rest { date, ex, set, last -> restFor(snap, date, ex, set, last) }

/** Every goal the calculator offers, in its order; [custom] is the custom target's current value. */
private fun goals(custom: Int): List<Goal> =
    listOf(Goal.Maintain) + Goal.RATES.map { Goal.LoseRate(it) } + Goal.DEFICITS.map { Goal.LoseDeficit(it) } +
        listOf(Goal.Recomp, Goal.LeanBulk, Goal.Bulk, Goal.Custom(custom))

/**
 * The TDEE calculator (#173): YOUR DETAILS (the profile and the latest bodyweight and body fat, each changed right
 * here), RESULT (resting energy and its equation, daily activity, the average day's training, the daily total), GOAL
 * (each goal with its calories and expected weekly change), MACROS, then Set as my target, with Undo. Every figure is
 * an estimate, and missing inputs are named rather than guessed.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TdeeScreen(snap: Snapshot, nav: Nav) {
    val prefs by Settings.portable.collectAsState()
    val res = LocalContext.current.resources
    val today = Dates.today()
    val rest = remember(snap) { energyRest(snap) }
    val daily = remember(snap.trainingKey, snap.bodyKey, snap.libraryKey, prefs) { EnergyEstimates.daily(snap, prefs, today, rest) }
    val sex = BodyFat.Sex.of(prefs.profileSex)
    var logging by remember { mutableStateOf<String?>(null) }
    val goal = Goal.decode(prefs.calorieGoal) ?: Goal.Maintain
    val customKcal = (goal as? Goal.Custom)?.kcal ?: prefs.calorieTargetKcal ?: daily.tdee?.roundToInt() ?: 2000

    Column(Modifier.fillMaxSize()) {
        FitTopBar(title = stringResource(R.string.tdee_title), onBack = { nav.pop() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = Spacing.xl)) {
            SettingsGroup(stringResource(R.string.tdee_group_details))
            ProfileRows(prefs)
            // The latest body values, logged right here when missing or out of date.
            val bw = EnergyEstimates.bodyweight(snap, today)
            SettingsActionRow(
                stringResource(R.string.tdee_bodyweight),
                stringResource(R.string.tdee_bodyweight_summary, EnergyEstimates.BODYWEIGHT_DAYS),
                value = bw?.second?.let { r -> stringResource(R.string.tdee_value_on, "${fmtNum(r.value)} ${r.unit}".trim(), Dates.medium(r.date)) }
                    ?: stringResource(R.string.tdee_not_logged)
            ) { logging = snap.bodyweightName ?: "Bodyweight" }
            val bf = EnergyEstimates.bodyFat(snap, today)
            SettingsActionRow(
                stringResource(R.string.tdee_body_fat),
                stringResource(R.string.tdee_body_fat_summary, Energy.BODY_FAT_DAYS),
                value = bf?.second?.let { r -> stringResource(R.string.tdee_value_on, "${fmtNum(r.value, 1)} %", Dates.medium(r.date)) }
                    ?: stringResource(R.string.tdee_none_recent)
            ) { logging = EnergyEstimates.bodyFatName(snap) ?: BodyFat.NAME }
            if (prefs.profileHeightCm == null) {
                EnergyEstimates.heightMeasured(snap, today)?.let { cm ->
                    SettingsNote(stringResource(R.string.tdee_height_from_measurement, heightText(cm, prefs.lengthUnit)))
                }
            }

            SettingsGroup(stringResource(R.string.tdee_group_result))
            SettingsChoiceRow(
                stringResource(R.string.tdee_formula),
                Energy.Formula.entries.map { formulaText(res, it) },
                Energy.Formula.of(prefs.bmrFormula).ordinal,
                summary = stringResource(R.string.tdee_formula_summary)
            ) { i -> Settings.updatePortable { it.copy(bmrFormula = Energy.Formula.entries[i].key) } }
            val missing = daily.missing
            if (missing.isNotEmpty()) {
                SettingsNote(stringResource(R.string.tdee_missing, missing.joinToString(", ") { missingText(res, it) }), error = true)
            }
            daily.resting.bmr?.let { bmr ->
                Figure(stringResource(R.string.tdee_bmr, formulaText(res, daily.resting.formula)), stringResource(R.string.tdee_kcal, kcalText(bmr)))
            }
            val base = daily.baseKcal
            val bmr = daily.resting.bmr
            if (base != null && bmr != null && daily.lifestyle != null) {
                Figure(
                    stringResource(R.string.tdee_activity, lifestyleText(res, daily.lifestyle), fmtNum(daily.lifestyle.factor, 2)),
                    stringResource(R.string.tdee_plus_kcal, kcalText(base - bmr))
                )
            }
            daily.training?.let { t ->
                Figure(
                    if (t.fromLogs) stringResource(R.string.tdee_training_logged, Energy.AVERAGE_DAYS)
                    else stringResource(R.string.tdee_training_planned),
                    stringResource(R.string.tdee_plus_kcal, kcalText(t.kcalPerDay))
                )
                if (!t.fromLogs) TrainingPlanRows(prefs.trainingPlan)
            }
            daily.estimate?.let { e ->
                Figure(stringResource(R.string.tdee_total), stringResource(R.string.tdee_kcal, kcalText(e)), big = !daily.usesMeasured)
            }
            SettingsNote(stringResource(R.string.tdee_estimate_note, Energy.TYPICAL_ERROR_PERCENT))
            // The TDEE measured from what was eaten and the weight trend (#176), and which one the goal uses.
            val measured = daily.measured
            val p = daily.progress
            if (measured != null) {
                Figure(stringResource(R.string.tdee_measured), stringResource(R.string.tdee_kcal, kcalText(measured.tdee)), big = daily.usesMeasured)
                SettingsNote(
                    stringResource(
                        R.string.tdee_measured_note, Intake.WINDOW_DAYS, kcalText(measured.intakeKcal),
                        fmtSigned(snap.weight(measured.kgPerWeek), 2), snap.weightUnit
                    )
                )
                SettingsChoiceRow(
                    stringResource(R.string.tdee_source),
                    listOf(stringResource(R.string.tdee_source_estimate), stringResource(R.string.tdee_source_measured)),
                    if (daily.usesMeasured) 1 else 0,
                    descriptions = listOf(stringResource(R.string.tdee_source_estimate_desc), stringResource(R.string.tdee_source_measured_desc))
                ) { i ->
                    Settings.updatePortable { it.copy(tdeeSource = if (i == 1) EnergyEstimates.SOURCE_MEASURED else EnergyEstimates.SOURCE_ESTIMATE) }
                }
            } else if (p.enough) {
                SettingsNote(stringResource(R.string.tdee_measured_odd), error = true)
            } else {
                SettingsNote(
                    stringResource(
                        R.string.tdee_measured_progress, Intake.MIN_INTAKE_DAYS, Intake.WINDOW_DAYS, Intake.MIN_WEIGH_INS,
                        p.intakeDays, p.weighIns, p.spanDays
                    )
                )
            }
            val tdee = daily.tdee

            val kg = daily.kg
            if (tdee != null && bmr != null && kg != null) {
                SettingsGroup(stringResource(R.string.tdee_group_goal))
                Column(Modifier.selectableGroup()) {
                    goals(customKcal).forEach { g ->
                        val t = Energy.target(g, tdee, kg)
                        val chosen = g::class == goal::class && (g !is Goal.LoseRate || g == goal) && (g !is Goal.LoseDeficit || g == goal)
                        GoalRow(
                            goalText(res, g),
                            stringResource(
                                R.string.tdee_goal_detail, kcalText(t),
                                fmtSigned(Energy.weeklyChangeKg(t, tdee).let { snap.weight(it) }, 2), snap.weightUnit
                            ),
                            chosen
                        ) { Settings.updatePortable { it.copy(calorieGoal = g.encode()) } }
                    }
                }
                if (goal is Goal.Custom) {
                    SettingsNumberRow(
                        stringResource(R.string.tdee_custom_kcal),
                        goal.kcal.coerceIn(1000, 6000),
                        1000..6000,
                        format = { kcalText(it.toDouble()) }
                    ) { v -> Settings.updatePortable { it.copy(calorieGoal = Goal.Custom(v).encode()) } }
                }
                val target = Energy.target(goal, tdee, kg)
                Energy.warnings(target, bmr, tdee, kg, sex).forEach { w -> SettingsNote(warningText(res, w, sex), error = true) }

                SettingsGroup(stringResource(R.string.tdee_group_macros))
                val m = Energy.macros(target, kg, goal)
                Figure(stringResource(R.string.tdee_protein, fmtNum(Energy.proteinPerKg(goal), 1)), stringResource(R.string.tdee_grams_kcal, m.proteinG, kcalText(m.proteinG * 4.0)))
                Figure(stringResource(R.string.tdee_fat), stringResource(R.string.tdee_grams_kcal, m.fatG, kcalText(m.fatG * 9.0)))
                Figure(stringResource(R.string.tdee_carbs), stringResource(R.string.tdee_grams_kcal, m.carbG, kcalText(m.carbG * 4.0)))
                SettingsNote(stringResource(R.string.tdee_macros_note))

                prefs.calorieTargetKcal?.let { now ->
                    SettingsNote(stringResource(R.string.tdee_current_target, kcalText(now.toDouble())))
                }
                val setDone = stringResource(R.string.tdee_target_set, kcalText(target))
                val undo = stringResource(R.string.undo)
                GoldButton(
                    onClick = {
                        val before = prefs
                        Settings.updatePortable {
                            it.copy(calorieTargetKcal = target.roundToInt(), macroProteinG = m.proteinG, macroFatG = m.fatG, macroCarbG = m.carbG)
                        }
                        UiEvents.show(setDone, undo) {
                            Settings.updatePortable {
                                it.copy(
                                    calorieTargetKcal = before.calorieTargetKcal, macroProteinG = before.macroProteinG,
                                    macroFatG = before.macroFatG, macroCarbG = before.macroCarbG
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md).heightIn(min = Spacing.row)
                ) { Text(stringResource(R.string.tdee_set_target)) }
            }
        }
    }
    logging?.let { name -> MeasurementEntrySheet(snap, today, initialName = name) { logging = null } }
}

/** A planned week of training (#173), used until two weeks are logged: sessions, minutes and effort. */
@Composable
private fun TrainingPlanRows(encoded: String?) {
    val res = LocalContext.current.resources
    val plan = Energy.Plan.decode(encoded) ?: Energy.Plan.DEFAULT
    SettingsNumberRow(stringResource(R.string.tdee_plan_sessions), plan.sessions, 0..14) { v ->
        Settings.updatePortable { it.copy(trainingPlan = plan.copy(sessions = v).encode()) }
    }
    SettingsNumberRow(stringResource(R.string.tdee_plan_minutes), plan.minutes, 0..300) { v ->
        Settings.updatePortable { it.copy(trainingPlan = plan.copy(minutes = v).encode()) }
    }
    SettingsChoiceRow(
        stringResource(R.string.tdee_plan_effort),
        Energy.Effort.entries.map { effortText(res, it) },
        plan.effort.ordinal
    ) { i -> Settings.updatePortable { it.copy(trainingPlan = plan.copy(effort = Energy.Effort.entries[i]).encode()) } }
}

/** A label and its figure on one line; [big] for the total. */
@Composable
internal fun Figure(label: String, value: String, big: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.touch).padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = Spacing.md))
        Text(
            value,
            style = if (big) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleMedium,
            color = if (big) Brand.Gold else MaterialTheme.colorScheme.onSurface
        )
    }
}

/** One goal as a radio row: its name, then its calories and expected weekly change. */
@Composable
private fun GoalRow(title: String, detail: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Spacing.row)
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f).padding(start = Spacing.md)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
