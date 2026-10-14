package com.fitlens.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.Energy
import com.fitlens.companion.data.EnergyEstimates
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.relativeDayLabel
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsNote
import kotlin.math.roundToInt

/** An exercise activity's name (#174), as the exercise editor lists it with its MET. */
internal fun activityText(res: android.content.res.Resources, a: Energy.Activity): String = res.getString(
    R.string.lib_activity_item,
    res.getString(
        when (a) {
            Energy.Activity.STRENGTH_LIGHT -> R.string.act_strength_light
            Energy.Activity.STRENGTH_MODERATE -> R.string.act_strength_moderate
            Energy.Activity.STRENGTH_VIGOROUS -> R.string.act_strength_vigorous
            Energy.Activity.CIRCUIT -> R.string.act_circuit
            Energy.Activity.CALISTHENICS -> R.string.act_calisthenics
            Energy.Activity.HIIT -> R.string.act_hiit
            Energy.Activity.WALKING -> R.string.act_walking
            Energy.Activity.RUNNING -> R.string.act_running
            Energy.Activity.CYCLING -> R.string.act_cycling
            Energy.Activity.ROWING -> R.string.act_rowing
            Energy.Activity.ELLIPTICAL -> R.string.act_elliptical
            Energy.Activity.STAIRS -> R.string.act_stairs
            Energy.Activity.SWIMMING -> R.string.act_swimming
            Energy.Activity.SPORTS -> R.string.act_sports
            Energy.Activity.YOGA -> R.string.act_yoga
            Energy.Activity.STRETCHING -> R.string.act_stretching
        }
    ),
    fmtNum(a.met, 1)
)

/** [date]'s calories (#174), worked out once per change to the training, body and library data or the settings. */
@Composable
internal fun rememberDayCalories(snap: Snapshot, date: String): Pair<EnergyEstimates.Day?, EnergyEstimates.Daily> {
    val prefs by Settings.portable.collectAsState()
    return remember(snap.trainingKey, snap.bodyKey, snap.libraryKey, prefs, date) {
        EnergyEstimates.day(snap, prefs, date, energyRest(snap))
    }
}

/**
 * The day log's calories card (#174), after the body values on any date: "Burned ≈ 2,640 kcal · Need ≈ 2,240 kcal".
 * Tapping it opens [CaloriesSheet]. Until the profile and a bodyweight are there, it's one line offering to set them
 * up, naming what's missing.
 */
@Composable
internal fun CaloriesCard(snap: Snapshot, date: String, onOpen: () -> Unit, onSetUp: () -> Unit) {
    val res = LocalContext.current.resources
    val (day, daily) = rememberDayCalories(snap, date)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .background(Brand.Surface, FitShapes.card)
            .border(1.dp, Brand.Hairline, FitShapes.card)
            .clickable(onClickLabel = stringResource(if (day != null) R.string.cal_card_open else R.string.cal_card_set_up)) {
                if (day != null) onOpen() else onSetUp()
            }
            .semantics(mergeDescendants = true) {}
            .heightIn(min = Spacing.row)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Text(
            stringResource(R.string.cal_card_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (day == null) {
            Text(
                stringResource(R.string.cal_card_missing, daily.missing.joinToString(", ") { missingText(res, it) }),
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.GoldLight
            )
        } else {
            val need = day.need
            Text(
                if (need != null) stringResource(R.string.cal_card_value, kcalText(day.burned), kcalText(need))
                else stringResource(R.string.cal_card_burned_only, kcalText(day.burned)),
                style = MaterialTheme.typography.titleMedium
            )
            if (need == null) {
                Text(stringResource(R.string.cal_card_no_target), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * The breakdown behind [CaloriesCard] (#174): resting energy and its equation, daily activity, each exercise's net
 * calories with how they were worked out, the total burned; then the day's need, how it's set (changed right here),
 * and the TDEE calculator.
 */
@Composable
internal fun CaloriesSheet(snap: Snapshot, nav: Nav, date: String, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val prefs by Settings.portable.collectAsState()
    val (day, _) = rememberDayCalories(snap, date)
    FitSheet(title = stringResource(R.string.cal_sheet_title, relativeDayLabel(date)), onDismiss = onDismiss) {
        if (day == null) {
            Text(stringResource(R.string.cal_card_set_up))
            return@FitSheet
        }
        SectionLabel(stringResource(R.string.cal_sheet_burned))
        Figure(stringResource(R.string.tdee_bmr, formulaText(res, day.formula)), stringResource(R.string.tdee_kcal, kcalText(day.bmr)))
        Figure(stringResource(R.string.cal_sheet_activity), stringResource(R.string.tdee_plus_kcal, kcalText(day.activityKcal)))
        day.exercises.forEach { b ->
            val name = snap.exercises[b.bout.exerciseId]?.name ?: stringResource(R.string.day_exercise_fallback, b.bout.exerciseId)
            val minutes = (b.bout.seconds / 60.0).roundToInt()
            val how = when (b.method) {
                Energy.Method.ACSM_WALK -> stringResource(R.string.cal_method_walk)
                Energy.Method.ACSM_RUN -> stringResource(R.string.cal_method_run)
                Energy.Method.CYCLING, Energy.Method.MET -> stringResource(R.string.cal_method_met, fmtNum(b.met ?: 0.0, 1))
            }
            Figure(stringResource(R.string.cal_sheet_exercise, name, minutes, how), stringResource(R.string.tdee_plus_kcal, kcalText(b.kcal)))
        }
        if (day.exercises.isEmpty()) {
            Text(stringResource(R.string.cal_sheet_rest_day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Figure(stringResource(R.string.cal_sheet_total), stringResource(R.string.tdee_kcal, kcalText(day.burned)), big = true)

        SectionLabel(stringResource(R.string.cal_sheet_need))
        SettingsChoiceRow(
            stringResource(R.string.cal_mode),
            listOf(stringResource(R.string.cal_mode_same), stringResource(R.string.cal_mode_training)),
            if (day.mode == EnergyEstimates.MODE_TRAINING) 1 else 0,
            descriptions = listOf(stringResource(R.string.cal_mode_same_desc), stringResource(R.string.cal_mode_training_desc))
        ) { i -> Settings.updatePortable { it.copy(calorieMode = if (i == 1) EnergyEstimates.MODE_TRAINING else EnergyEstimates.MODE_SAME) } }
        val need = day.need
        if (need != null) {
            Figure(stringResource(R.string.cal_sheet_need_value), stringResource(R.string.tdee_kcal, kcalText(need)), big = true)
            prefs.macroProteinG?.let { p ->
                SettingsNote(stringResource(R.string.cal_sheet_macros, p, prefs.macroFatG ?: 0, prefs.macroCarbG ?: 0))
            }
        } else {
            SettingsNote(stringResource(R.string.cal_card_no_target))
        }
        SettingsNote(stringResource(R.string.cal_sheet_note, Energy.TYPICAL_ERROR_PERCENT))
        TextButton(onClick = { onDismiss(); nav.push(Screen.Tdee) }) { Text(stringResource(R.string.cal_sheet_open_tdee)) }
    }
}
