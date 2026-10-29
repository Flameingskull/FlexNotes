package com.fitlens.companion.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.fitlens.companion.R
import com.fitlens.companion.data.AsNeeded
import com.fitlens.companion.data.AsNeededState
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.InjectionSites
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.MedExtras
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.GlassOutlinedButton
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.fitlens.companion.ui.design.FitSwitch

/**
 * As-needed medications (#233): ones taken only when needed (pain relief, an inhaler) rather than on a schedule. Today
 * lists them under AS NEEDED with the time since the last dose, how many today against an optional most a day, and
 * Take now, which logs the last amount at once with Undo (a warning asks first once the most a day is reached). A
 * medication's ⋮ menu sets it up ([AsNeededSheet]). Their values are ordinary medication values.
 */

private val HHMMSS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/** AS NEEDED on the Today tab: each as-needed medication with its state and Take now. Nothing when there are none. */
internal fun LazyListScope.asNeededRows(snap: Snapshot, nav: Nav) {
    val meds = AsNeeded.medications(snap.medPlan, snap.medications.map { it.name })
    if (meds.isEmpty()) return
    item(key = "prn-label") { SectionTitle(stringResource(R.string.prn_title)) }
    items(meds.size, key = { "prn-${meds[it].medication}" }) { i ->
        val state = AsNeeded.state(meds[i], snap.medicationView.records, Dates.today())
        AsNeededRow(snap, nav, state)
    }
}

/** One as-needed medication: name, "Last taken 3 h 20 min ago · 1 of 3 today", and Take now. */
@Composable
private fun AsNeededRow(snap: Snapshot, nav: Nav, state: AsNeededState) {
    val res = LocalContext.current.resources
    var confirm by rememberSaveable { mutableStateOf(false) }
    val last = state.last
    fun take() {
        // With nothing logged yet there's no amount to repeat: the medication's page logs the first one.
        if (last == null) nav.push(Screen.Medication(state.medication)) else takeAsNeeded(res, snap, last)
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        Column(Modifier.weight(1f)) {
            Text(state.medication, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(sinceText(res, last), countText(res, state)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.atLimit) {
                Text(
                    stringResource(R.string.prn_limit_reached),
                    style = MaterialTheme.typography.bodySmall, color = Brand.Gold, fontWeight = FontWeight.Bold
                )
            }
        }
        GlassOutlinedButton(
            onClick = { if (state.atLimit && last != null) confirm = true else take() },
            modifier = Modifier.heightIn(min = Spacing.touch)
        ) {
            Text(
                if (last == null) stringResource(R.string.prn_take_first)
                else stringResource(R.string.prn_take_now, "${fmtNum(last.value)} ${last.unit}".trim())
            )
        }
    }
    if (confirm && last != null) {
        ConfirmSheet(
            title = stringResource(R.string.prn_limit_title, state.medication),
            message = stringResource(R.string.prn_limit_body, state.today, state.maxPerDay ?: state.today),
            confirmLabel = stringResource(R.string.prn_take_anyway),
            destructive = false,
            onDismiss = { confirm = false },
            onConfirm = { take() }
        )
    }
}

/** Logs [last]'s amount of its medication now, at the next site when it's injected (#210), with Undo. */
private fun takeAsNeeded(res: Resources, snap: Snapshot, last: MRecord) {
    val site = if (snap.isInjected(last.name)) InjectionSites.suggest(snap.medicationView.records, last.name).key else null
    AppScope.scope.launch {
        val id = MedExtras.takeAsNeeded(last.name, last.unit, Dates.today(), LocalTime.now().format(HHMMSS), last.value, site)
        UiEvents.show(res.getString(R.string.md_taken_named, last.name), res.getString(R.string.bm_undo)) {
            AppScope.scope.launch { Store.deleteRecord(id) }
        }
    }
}

/** "Last taken 25 min ago", "Last taken 3 h 20 min ago", "Last taken 2 days ago", or "Not taken yet". */
internal fun sinceText(res: Resources, last: MRecord?, now: LocalDateTime = LocalDateTime.now()): String {
    val minutes = last?.let { AsNeeded.minutesSince(it, now) } ?: return res.getString(R.string.prn_never)
    return when {
        minutes < 60 -> res.getString(R.string.prn_since_minutes, minutes.toInt())
        minutes < 48 * 60 -> res.getString(R.string.prn_since_hours, (minutes / 60).toInt(), (minutes % 60).toInt())
        else -> (minutes / (24 * 60)).toInt().let { res.getQuantityString(R.plurals.prn_since_days, it, it) }
    }
}

/** "2 today", or "2 of 3 today" with a most a day. */
private fun countText(res: Resources, s: AsNeededState): String =
    s.maxPerDay?.let { res.getString(R.string.prn_count_of, s.today, it) } ?: res.getString(R.string.prn_count, s.today)

/** A medication's as-needed setting (#233): on or off, and the most a day (blank for no limit). Undo puts it back. */
@Composable
internal fun AsNeededSheet(snap: Snapshot, name: String, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val info = snap.medPlan.infoOf(name)
    var on by rememberSaveable { mutableStateOf(info.asNeeded) }
    var most by rememberSaveable { mutableStateOf(info.maxPerDay?.toString().orEmpty()) }
    val mostValue = most.trim().toIntOrNull()
    val valid = most.isBlank() || (mostValue != null && mostValue in 1..99)
    FitSheet(
        title = stringResource(R.string.prn_sheet_title, name),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.type_save),
        confirmEnabled = valid,
        onConfirm = {
            val old = info
            val updated = info.copy(asNeeded = on, maxPerDay = if (on) mostValue else info.maxPerDay)
            AppScope.scope.launch {
                MedExtras.setInfo(updated)
                UiEvents.show(res.getString(if (on) R.string.prn_on else R.string.prn_off, name), res.getString(R.string.bm_undo)) {
                    AppScope.scope.launch { MedExtras.setInfo(old) }
                }
            }
            onDismiss()
        }
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = Spacing.touch).toggleable(value = on, role = Role.Switch) { on = it },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.prn_switch), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.prn_switch_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FitSwitch(checked = on, onCheckedChange = null)
        }
        if (on) {
            OutlinedTextField(
                value = most, onValueChange = { most = it.filter { c -> c.isDigit() }.take(2) },
                label = { Text(stringResource(R.string.prn_most)) },
                supportingText = { Text(stringResource(R.string.prn_most_note)) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
