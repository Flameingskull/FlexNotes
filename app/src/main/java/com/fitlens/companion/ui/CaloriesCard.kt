package com.fitlens.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.Energy
import com.fitlens.companion.data.EnergyEstimates
import com.fitlens.companion.data.HealthConnect
import com.fitlens.companion.data.Intake
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.relativeDayLabel
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsNote
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
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

/** [date]'s logged intake (#176), each kind's total that counts ([hcPrimary]: Health Connect's first, #186). */
@Composable
private fun rememberIntake(snap: Snapshot, date: String, hcPrimary: Boolean): Map<Intake.Kind, Double> =
    remember(snap.bodyKey, date, hcPrimary) { Intake.totals(snap, date, hcPrimary) }

/** Where [date]'s counted intake came from when it's Health Connect's (#186): "Health Connect · MacrosFirst", or null. */
@Composable
private fun rememberIntakeOrigin(snap: Snapshot, date: String, hcPrimary: Boolean): String? =
    remember(snap.bodyKey, date, hcPrimary) {
        val hc = Intake.healthConnectOn(snap, date, hcPrimary)
        (hc.firstOrNull { Intake.Kind.KCAL.matches(it.name) } ?: hc.firstOrNull())?.let { it.comment ?: HealthConnect.NAME }
    }

/** "540 kcal under" or "120 kcal over": [eaten] against [other]. */
private fun balanceText(res: android.content.res.Resources, eaten: Double, other: Double): String =
    if (eaten <= other) res.getString(R.string.cal_under, kcalText(other - eaten))
    else res.getString(R.string.cal_over, kcalText(eaten - other))

/**
 * The day log's calories card (#174), after the body values on any date: "Burned ≈ 2,640 kcal · Need ≈ 2,240 kcal",
 * then what was eaten against the need (#176). Tapping it opens [CaloriesSheet], where what was eaten is logged.
 * Until the profile and a bodyweight are there, it names what's missing in place of the burned figures.
 */
@Composable
internal fun CaloriesCard(snap: Snapshot, date: String, onOpen: () -> Unit) {
    val res = LocalContext.current.resources
    val prefs by Settings.portable.collectAsState()
    val (day, daily) = rememberDayCalories(snap, date)
    val eaten = rememberIntake(snap, date, prefs.hcIntake)[Intake.Kind.KCAL]
    val origin = rememberIntakeOrigin(snap, date, prefs.hcIntake)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .background(Brand.Surface, FitShapes.card)
            .border(1.dp, Brand.Hairline, FitShapes.card)
            .clickable(onClickLabel = stringResource(R.string.cal_card_open)) { onOpen() }
            .semantics(mergeDescendants = true) {}
            .heightIn(min = Spacing.row)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Text(
            stringResource(R.string.cal_card_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        val need = day?.need
        if (day == null) {
            Text(
                stringResource(R.string.cal_card_missing, daily.missing.joinToString(", ") { missingText(res, it) }),
                style = MaterialTheme.typography.bodyMedium,
                color = Brand.GoldLight
            )
        } else {
            Text(
                if (need != null) stringResource(R.string.cal_card_value, kcalText(day.burned), kcalText(need))
                else stringResource(R.string.cal_card_burned_only, kcalText(day.burned)),
                style = MaterialTheme.typography.titleMedium
            )
            if (need == null) {
                Text(stringResource(R.string.cal_card_no_target), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            eaten != null && need != null -> Text(
                stringResource(
                    if (eaten <= need) R.string.cal_card_eaten_under else R.string.cal_card_eaten_over,
                    kcalText(eaten), kcalText(abs(need - eaten)), kcalText(need)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            eaten != null -> Text(stringResource(R.string.cal_card_eaten, kcalText(eaten)), style = MaterialTheme.typography.bodyMedium)
            else -> Text(stringResource(R.string.cal_card_log_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        origin?.let {
            Text(stringResource(R.string.cal_card_from, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A typed amount, or null when it isn't a number from 0 up to 100,000. */
private fun intakeValue(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it >= 0 && it < 100_000 }

/** A field for one kind of intake. */
@Composable
private fun IntakeField(label: String, text: String, onText: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = text,
        onValueChange = onText,
        label = { Text(label) },
        singleLine = true,
        isError = text.isNotBlank() && intakeValue(text) == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
    )
}

/**
 * The breakdown behind [CaloriesCard]: first what was eaten (#176), typed as the day's totals and saved in place,
 * with Clear and Undo, and how it compares; then (#174) resting energy and its equation, daily activity, each
 * exercise's net calories with how they were worked out, the total burned; then the day's need, how it's set
 * (changed right here), and the TDEE calculator. Without a profile, the burned part offers to set one up.
 */
@Composable
internal fun CaloriesSheet(snap: Snapshot, nav: Nav, date: String, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val prefs by Settings.portable.collectAsState()
    val (day, daily) = rememberDayCalories(snap, date)
    val totals = rememberIntake(snap, date, prefs.hcIntake)
    // The fields edit what the user logged; while Health Connect's totals count for the day (#186) they're shown apart.
    val origin = rememberIntakeOrigin(snap, date, prefs.hcIntake)
    val own = remember(snap.bodyKey, date) { Intake.ownTotals(snap, date) }
    val typed = remember(date) {
        mutableStateMapOf<Intake.Kind, String>().apply { (if (origin != null) own else totals).forEach { (k, v) -> put(k, fmtNum(v, 1)) } }
    }
    val valid = Intake.Kind.entries.all { k -> typed[k].isNullOrBlank() || intakeValue(typed[k].orEmpty()) != null }
    val dayLabel = relativeDayLabel(date)
    val undoLabel = stringResource(R.string.undo)

    /** Replaces the day's logged intake with [values], with Undo back to what was there. Health Connect's stays. */
    fun write(values: Map<Intake.Kind, Double>, message: String) {
        val before = own
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        AppScope.scope.launch { Store.setIntake(date, time, Intake.ownRecordsOn(snap, date).map { it.id }, values) }
        UiEvents.show(message, undoLabel) {
            AppScope.scope.launch {
                val now = Store.snapshot.value ?: return@launch
                Store.setIntake(date, time, Intake.ownRecordsOn(now, date).map { it.id }, before)
            }
        }
        onDismiss()
    }

    FitSheet(
        title = stringResource(R.string.cal_sheet_title, dayLabel),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.cal_eaten_save),
        confirmEnabled = valid,
        onConfirm = {
            val values = Intake.Kind.entries.mapNotNull { k -> typed[k]?.let { intakeValue(it) }?.let { k to it } }.toMap()
            write(values, res.getString(R.string.cal_eaten_saved, dayLabel))
        },
        secondaryLabel = if (own.isNotEmpty()) stringResource(R.string.cal_eaten_clear) else null,
        onSecondary = if (own.isNotEmpty()) {
            { write(emptyMap(), res.getString(R.string.cal_eaten_cleared, dayLabel)) }
        } else null
    ) {
        SectionLabel(stringResource(R.string.cal_sheet_eaten))
        Text(
            stringResource(R.string.cal_sheet_eaten_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        origin?.let { SettingsNote(stringResource(R.string.cal_sheet_hc_note, it)) }
        val fields = listOf(
            Intake.Kind.KCAL to R.string.cal_field_kcal, Intake.Kind.PROTEIN to R.string.cal_field_protein,
            Intake.Kind.FAT to R.string.cal_field_fat, Intake.Kind.CARBS to R.string.cal_field_carbs
        )
        fields.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                pair.forEach { (kind, label) ->
                    IntakeField(stringResource(label), typed[kind].orEmpty(), { typed[kind] = it }, Modifier.weight(1f))
                }
            }
        }
        val eaten = totals[Intake.Kind.KCAL]
        if (eaten != null && day != null) {
            Figure(stringResource(R.string.cal_sheet_vs_burned), balanceText(res, eaten, day.burned))
            day.need?.let { Figure(stringResource(R.string.cal_sheet_vs_need), balanceText(res, eaten, it)) }
        }

        if (day == null) {
            SettingsNote(stringResource(R.string.cal_sheet_set_up_note, daily.missing.joinToString(", ") { missingText(res, it) }))
            TextButton(onClick = { onDismiss(); nav.push(Screen.SettingsPage(SettingsSection.Profile)) }) {
                Text(stringResource(R.string.cal_card_set_up))
            }
        } else {
            SectionLabel(stringResource(R.string.cal_sheet_burned))
            Figure(stringResource(R.string.tdee_bmr, formulaText(res, day.formula)), stringResource(R.string.tdee_kcal, kcalText(day.bmr)))
            Figure(
                day.active?.let {
                    // Health Connect's active calories (#188), less what the day's logged exercise already counts.
                    if (day.activeCovered >= 0.5) stringResource(R.string.cal_sheet_activity_active_less, kcalText(it), kcalText(day.activeCovered))
                    else stringResource(R.string.cal_sheet_activity_active, kcalText(it))
                } ?: day.steps?.let {
                    // A logged walk or run counts its own calories, so its steps are left out here (#181).
                    if (day.walkedSteps >= 0.5) stringResource(R.string.cal_sheet_activity_steps_less, stepsText(it), stepsText(day.walkedSteps))
                    else stringResource(R.string.cal_sheet_activity_steps, stepsText(it))
                } ?: stringResource(R.string.cal_sheet_activity),
                stringResource(R.string.tdee_plus_kcal, kcalText(day.activityKcal))
            )
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
}
