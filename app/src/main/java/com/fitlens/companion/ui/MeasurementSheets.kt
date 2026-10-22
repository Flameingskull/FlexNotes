package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.GlassOutlinedButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.fitlens.companion.data.BodyFat
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.Medications
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.FitSheet
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val HHMMSS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/**
 * Deletes a body value at once, with Undo (#164, #166): any value, typed in or imported. Undo adds it back as the
 * user's own; an imported original stays skipped by later imports.
 */
fun deleteBodyValue(res: Resources, r: MRecord) {
    AppScope.scope.launch {
        Store.deleteRecord(r.id)
        UiEvents.show(res.getString(R.string.bm_deleted), res.getString(R.string.bm_undo)) {
            AppScope.scope.launch { Store.addManualRecord(r.name, r.unit, r.date, r.time, r.value, r.comment) }
        }
    }
}

/**
 * Logs a body measurement, or edits one (#27, #88): the measurement, value, date, time and an optional comment, as a
 * sheet over the screen showing it (#164, #169). [existing] opens a value to change, move to another date or time, or
 * delete, whatever its source: an imported value edited here stays as edited after the next import (#166).
 */
@Composable
fun MeasurementEntrySheet(
    snap: Snapshot,
    date: String,
    initialName: String? = null,
    existing: MRecord? = null,
    onDismiss: () -> Unit
) {
    val res = LocalContext.current.resources
    val names = remember(snap.bodyKey) {
        (snap.usedMeasurements.map { it.name } + snap.measurementDefs.filter { it.enabled }.map { it.name }).distinct()
    }
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: initialName ?: names.firstOrNull() ?: if (snap.showsMedications) "" else "Bodyweight") }
    var value by remember(existing?.id) { mutableStateOf(existing?.value?.let { fmtNum(it) } ?: "") }
    var comment by remember(existing?.id) { mutableStateOf(existing?.comment ?: "") }
    var theDate by remember(existing?.id) { mutableStateOf(existing?.date ?: date) }
    var timeText by remember(existing?.id) {
        mutableStateOf(existing?.time?.take(5)?.takeIf { it.isNotBlank() } ?: LocalTime.now().format(HHMM))
    }
    var pickDate by remember { mutableStateOf(false) }
    var calculating by remember { mutableStateOf(false) }
    val unit = existing?.unit ?: snap.measurementDefs.firstOrNull { it.name == name }?.unit
        ?: snap.recordsByName[name]?.lastOrNull()?.unit ?: ""
    // A new Body fat value starts from the figure the latest measurements on or before its date give (#194). Seeded
    // when the measurement, date or inputs change, never over what was typed in between; taken back out if another
    // measurement is chosen while it's still untouched.
    val bodyFat = existing == null && BodyFat.isBodyFat(name)
    val estimate = if (existing == null) rememberBodyFatEstimate(snap, theDate) else null
    val lengthUnit = Settings.portable.collectAsState().value.lengthUnit
    val prefill = if (bodyFat) estimate?.percent?.let { fmtNum(it, 1) to estimateComment(res, estimate, lengthUnit) } else null
    var seeded by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(bodyFat, theDate, prefill) {
        val was = seeded
        if (prefill != null) {
            value = prefill.first
            comment = prefill.second
        } else if (was != null && value == was.first && comment == was.second) {
            value = ""
            comment = ""
        }
        seeded = prefill
    }

    fun save() {
        val v = value.trim().replace(',', '.').toDoubleOrNull()
        val n = name.trim()
        if (v == null || n.isBlank()) {
            UiEvents.show(res.getString(R.string.bm_enter_number))
            return
        }
        // A value goes into the half of the split this sheet shows (#199): a name from the other half is refused rather
        // than quietly logged where this screen can't show it.
        val medication = snap.showsMedications
        if (existing == null && snap.inOtherHalf(n)) {
            UiEvents.show(res.getString(if (medication) R.string.md_is_body else R.string.md_is_medication, n))
            return
        }
        // A new name typed on Medications becomes a medication first, so its value never lands on the Body tracker.
        val newMedication = existing == null && medication && snap.medications.none { it.name == n }
        if (newMedication && !Medications.canBeMedication(n)) {
            UiEvents.show(res.getString(R.string.md_refused, n))
            return
        }
        // The time typed in, or now when it can't be read.
        val time = runCatching { LocalTime.parse(timeText.trim()) }.getOrNull()?.format(HHMMSS) ?: LocalTime.now().format(HHMMSS)
        val c = comment.trim().ifBlank { null }
        val d = theDate
        AppScope.scope.launch {
            if (newMedication) Store.setMedication(n, unit, true)
            if (existing == null) Store.addManualRecord(n, unit, d, time, v, c)
            else Store.updateRecord(existing.id, d, time, v, c)
        }
        onDismiss()
    }

    FitSheet(
        title = when {
            existing != null -> stringResource(R.string.mse_edit, existing.name)
            snap.showsMedications -> stringResource(R.string.md_log)
            else -> stringResource(R.string.mse_log)
        },
        onDismiss = onDismiss,
        dismissLabel = stringResource(R.string.cancel),
        confirmLabel = stringResource(R.string.type_save),
        onConfirm = { save() },
        secondaryLabel = if (existing != null) stringResource(R.string.lib_delete) else null,
        onSecondary = if (existing != null) ({ deleteBodyValue(res, existing); onDismiss() }) else null
    ) {
        if (existing == null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                names.forEach { n -> FilterChip(selected = n == name, onClick = { name = n }, label = { Text(n) }) }
            }
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(stringResource(if (snap.showsMedications) R.string.md_medication else R.string.mse_measurement)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
            )
        }
        OutlinedTextField(
            value = value, onValueChange = { value = it },
            label = { Text(if (unit.isNotBlank()) stringResource(R.string.mse_value_unit, unit) else stringResource(R.string.body_value)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        // Body fat can be worked out from the tape measurements (#153).
        if (bodyFat) {
            BodyFatPrefillNote(estimate, theDate)
            TextButton(onClick = { calculating = true }) { Text(stringResource(R.string.bm_calculate)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GlassOutlinedButton(onClick = { pickDate = true }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)) {
                Text(Dates.medium(theDate))
            }
            OutlinedTextField(
                value = timeText, onValueChange = { timeText = it },
                label = { Text(stringResource(R.string.bm_time)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = comment, onValueChange = { comment = it },
            label = { Text(stringResource(R.string.mse_comment_optional)) }, modifier = Modifier.fillMaxWidth()
        )
    }
    if (calculating) {
        BodyFatCalculatorSheet(snap, theDate, onDismiss = { calculating = false }) { p, c ->
            value = fmtNum(p, 1)
            comment = c
        }
    }
    if (pickDate) PickDateDialog(theDate, onDismiss = { pickDate = false }) { theDate = it }
}
