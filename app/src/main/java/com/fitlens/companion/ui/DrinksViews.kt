package com.fitlens.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Drinks
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SettingsChoiceRow
import com.fitlens.companion.ui.design.SettingsNote
import com.fitlens.companion.ui.design.relativeDayLabel
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** A typical serving's name (#221). */
private fun servingName(res: android.content.res.Resources, s: Drinks.Serving): String = res.getString(
    when (s) {
        Drinks.Serving.ESPRESSO -> R.string.dr_espresso
        Drinks.Serving.COFFEE -> R.string.dr_coffee
        Drinks.Serving.TEA -> R.string.dr_tea
        Drinks.Serving.ENERGY_DRINK -> R.string.dr_energy_drink
        Drinks.Serving.COLA -> R.string.dr_cola
    }
)

/** "1,750 ml" / "190 mg". */
private fun amountText(v: Double, kind: Drinks.Kind): String = "${fmtNum(v, 0)} ${kind.unit}"

/** A typed "HH:mm", or null. */
private fun typedTime(text: String): String? =
    Drinks.minutesOf(text)?.takeIf { Regex("""\d{1,2}:\d{2}""").matches(text.trim()) }?.let { "%02d:%02d:00".format(it / 60, it % 60) }

/**
 * The day log's water and caffeine card (#221), after readiness: the day's totals and the caffeine estimated to be
 * still active at bedtime, with the sleep that night when it's logged. Tapping it opens [DrinksSheet].
 */
@Composable
internal fun DrinksCard(snap: Snapshot, date: String, onOpen: () -> Unit) {
    val res = LocalContext.current.resources
    val prefs by Settings.portable.collectAsState()
    val water = remember(snap.bodyKey, date) { Drinks.total(snap, date, Drinks.Kind.WATER) }
    val caffeine = remember(snap.bodyKey, date) { Drinks.total(snap, date, Drinks.Kind.CAFFEINE) }
    val atBed = remember(snap.bodyKey, date, prefs.bedtime) { Drinks.atBedtime(snap, date, prefs.bedtime) }
    val sleep = remember(snap.bodyKey, date, prefs.hcSleep) { Drinks.sleepAfter(snap, date, prefs.hcSleep) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .background(Brand.Surface, FitShapes.card)
            .border(1.dp, Brand.Hairline, FitShapes.card)
            .clickable(onClickLabel = stringResource(R.string.dr_card_open)) { onOpen() }
            .semantics(mergeDescendants = true) {}
            .heightIn(min = Spacing.row)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Text(
            stringResource(R.string.dr_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (water <= 0 && caffeine <= 0) {
            Text(stringResource(R.string.dr_card_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                stringResource(R.string.dr_card_value, amountText(water, Drinks.Kind.WATER), amountText(caffeine, Drinks.Kind.CAFFEINE)),
                style = MaterialTheme.typography.titleMedium
            )
            atBed?.let {
                Text(
                    if (sleep != null) stringResource(R.string.dr_at_bed_sleep, fmtNum(it, 0), prefs.bedtime, fmtNum(sleep, 1))
                    else stringResource(R.string.dr_at_bed, fmtNum(it, 0), prefs.bedtime),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

/** One logged drink: time, kind and amount; tap edits it, the bin deletes it with Undo. */
@Composable
private fun DrinkRow(r: MRecord, onOpen: () -> Unit, onDelete: () -> Unit) {
    val res = LocalContext.current.resources
    val kind = Drinks.Kind.entries.firstOrNull { it.matches(r.name) } ?: Drinks.Kind.WATER
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.touch).clickable(onClickLabel = stringResource(R.string.dr_edit)) { onOpen() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(r.time.take(5), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = Spacing.md))
        Text(
            res.getString(if (kind == Drinks.Kind.WATER) R.string.dr_water else R.string.dr_caffeine),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(amountText(r.value, kind), style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.dr_delete), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Water and caffeine for [date] (#221): the time, quick amounts and servings that log a drink at once, a typed
 * amount, the day's drinks (each opens to edit, or deletes with Undo), the bedtime, and each recent night's caffeine
 * at bedtime next to the sleep that followed.
 */
@Composable
internal fun DrinksSheet(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val prefs by Settings.portable.collectAsState()
    var time by rememberSaveable(date) {
        mutableStateOf(if (date == Dates.today()) LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")) else "12:00")
    }
    var amount by rememberSaveable(date) { mutableStateOf("") }
    var editing by remember { mutableStateOf<MRecord?>(null) }
    val at = typedTime(time)
    val typed = amount.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 && it < 10_000 }
    val drinks = remember(snap.bodyKey, date) { Drinks.recordsOn(snap, date).sortedBy { it.time } }
    val nights = remember(snap.bodyKey, date, prefs.bedtime, prefs.hcSleep) { Drinks.nights(snap, date, prefs.bedtime, prefs.hcSleep) }
    val dayLabel = relativeDayLabel(date)

    fun add(kind: Drinks.Kind, v: Double) {
        val t = at ?: return
        AppScope.scope.launch { Store.addDrink(date, t, kind, v) }
        UiEvents.show(res.getString(R.string.dr_added, amountText(v, kind), t.take(5)))
    }

    FitSheet(title = stringResource(R.string.dr_sheet_title, dayLabel), onDismiss = onDismiss, dismissLabel = stringResource(R.string.dr_done)) {
        SectionLabel(stringResource(R.string.dr_log))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            OutlinedTextField(
                value = time,
                onValueChange = { time = it },
                label = { Text(stringResource(R.string.dr_time)) },
                singleLine = true,
                isError = at == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text(stringResource(R.string.dr_amount)) },
                singleLine = true,
                isError = amount.isNotBlank() && typed == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f)
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GlassOutlinedButton(onClick = { typed?.let { add(Drinks.Kind.WATER, it); amount = "" } }, enabled = typed != null && at != null) {
                Text(stringResource(R.string.dr_add_water_ml))
            }
            GlassOutlinedButton(onClick = { typed?.let { add(Drinks.Kind.CAFFEINE, it); amount = "" } }, enabled = typed != null && at != null) {
                Text(stringResource(R.string.dr_add_caffeine_mg))
            }
        }
        Text(stringResource(R.string.dr_water), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Drinks.WATER_AMOUNTS.forEach { v ->
                AssistChip(onClick = { add(Drinks.Kind.WATER, v) }, enabled = at != null, label = { Text(amountText(v, Drinks.Kind.WATER)) })
            }
        }
        Text(stringResource(R.string.dr_caffeine), style = MaterialTheme.typography.titleSmall)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Drinks.Serving.entries.forEach { s ->
                AssistChip(
                    onClick = { add(Drinks.Kind.CAFFEINE, s.mg) },
                    enabled = at != null,
                    label = { Text(stringResource(R.string.dr_serving, servingName(res, s), fmtNum(s.mg, 0))) }
                )
            }
        }

        SectionLabel(stringResource(R.string.dr_today_list, dayLabel))
        if (drinks.isEmpty()) {
            Text(stringResource(R.string.dr_none), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            drinks.forEach { r -> DrinkRow(r, onOpen = { editing = r }, onDelete = { deleteBodyValue(res, r) }) }
            Figure(stringResource(R.string.dr_water), amountText(Drinks.total(snap, date, Drinks.Kind.WATER), Drinks.Kind.WATER))
            Figure(stringResource(R.string.dr_caffeine), amountText(Drinks.total(snap, date, Drinks.Kind.CAFFEINE), Drinks.Kind.CAFFEINE))
        }

        SectionLabel(stringResource(R.string.dr_bed_and_sleep))
        val bedIdx = Drinks.BEDTIMES.indexOf(prefs.bedtime).coerceAtLeast(0)
        SettingsChoiceRow(stringResource(R.string.dr_bedtime), Drinks.BEDTIMES, bedIdx) { i ->
            Settings.updatePortable { it.copy(bedtime = Drinks.BEDTIMES[i]) }
        }
        if (nights.isEmpty()) {
            SettingsNote(stringResource(R.string.dr_no_nights))
        } else {
            nights.forEach { n ->
                Figure(
                    relativeDayLabel(n.date),
                    stringResource(
                        R.string.dr_night_value,
                        n.caffeineMg?.let { amountText(it, Drinks.Kind.CAFFEINE) } ?: stringResource(R.string.dr_no_caffeine),
                        n.sleepHours?.let { stringResource(R.string.rd_part_sleep_value, fmtNum(it, 1)) } ?: stringResource(R.string.dr_no_sleep)
                    )
                )
            }
        }
        SettingsNote(stringResource(R.string.dr_note, fmtNum(Drinks.HALF_LIFE_HOURS, 0)))
    }

    editing?.let { r -> MeasurementEntrySheet(snap, r.date, existing = r) { editing = null } }
}
