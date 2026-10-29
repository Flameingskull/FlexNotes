package com.fitlens.companion.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import com.fitlens.companion.data.Medications
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.LabMarkers
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import kotlinx.coroutines.launch
import com.fitlens.companion.ui.design.FitSwitch
import com.fitlens.companion.ui.design.FitAlertDialog
import com.fitlens.companion.ui.design.FitFilterChip

/**
 * Creates or edits a custom measurement (#3), from the Measurements screen (#88), or a medication from Medications
 * ([medication], #200). "This is a medication" moves it between the two (#199), except for the names other features
 * read ([Medications.canBeMedication]). A lab marker ([lab], #211) offers the common markers and its reference range
 * instead of the medication switch and the FitNotes link.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomMetricEditor(snap: Snapshot, existing: MeasurementDef?, medication: Boolean = false, lab: Boolean = false, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: "") }
    var link by remember { mutableStateOf(existing?.link ?: "") }
    var isMedication by remember { mutableStateOf(existing?.medication ?: medication) }
    val isLab = existing?.lab ?: lab
    var low by remember { mutableStateOf(existing?.refLow?.let { fmtNum(it) } ?: "") }
    var high by remember { mutableStateOf(existing?.refHigh?.let { fmtNum(it) } ?: "") }
    val suggestions = remember(snap.bodyKey) {
        if (isLab && existing == null) LabMarkers.suggestions(snap.allCustomNames + snap.measurementDefs.map { it.name }) else emptyList()
    }
    val allowed = Medications.canBeMedication(name)
    val fitNotes = snap.fitNotesMeasurementNames
    val res = LocalContext.current.resources

    FitAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    existing != null -> stringResource(R.string.mse_edit, existing.name)
                    isLab -> stringResource(R.string.lab_new)
                    isMedication -> stringResource(R.string.md_new)
                    else -> stringResource(R.string.cm_new)
                }
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // The common markers (#211): a tap fills the name and the unit labs usually use.
                if (suggestions.isNotEmpty()) {
                    Text(stringResource(R.string.lab_common), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        suggestions.forEach { (n, u) ->
                            FitFilterChip(selected = name == n, onClick = { name = n; unit = u }, label = { Text(n) })
                        }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.rb_sort_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = unit, onValueChange = { unit = it },
                    label = { Text(stringResource(if (isMedication) R.string.md_unit else R.string.cm_unit)) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                // A dose's unit is free text (#200); these are the usual ones.
                if (isMedication) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Medications.UNITS.forEach { u -> FitFilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) }) }
                    }
                }
                if (isLab) {
                    Text(stringResource(R.string.lab_range_label), style = MaterialTheme.typography.labelLarge)
                    RangeFields(unit, low, high, { low = it }, { high = it })
                    Text(
                        stringResource(R.string.lab_range_note),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!isLab) Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(value = isMedication && allowed, enabled = allowed, role = Role.Switch) { isMedication = it },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.md_switch), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(if (allowed || name.isBlank()) R.string.md_switch_note else R.string.md_switch_refused),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FitSwitch(checked = isMedication && allowed, onCheckedChange = null, enabled = allowed)
                }
                if (!isLab) {
                    Text(stringResource(R.string.cm_fill_from), style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FitFilterChip(selected = link.isBlank(), onClick = { link = "" }, label = { Text(stringResource(R.string.cm_same_name)) })
                        fitNotes.forEach { n -> FitFilterChip(selected = link == n, onClick = { link = n }, label = { Text(n) }) }
                    }
                    Text(
                        if (link.isBlank()) stringResource(R.string.cm_same_name_note) else stringResource(R.string.cm_link_note, link),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                // Custom names are unique across body measurements and medications (#199).
                val clash = snap.allCustomNames.any { it.equals(n, true) && it != existing?.name }
                when {
                    n.isBlank() -> UiEvents.show(res.getString(R.string.cm_enter_name))
                    clash -> UiEvents.show(res.getString(R.string.cm_clash, n))
                    isLab && !rangeValid(low, high) -> UiEvents.show(res.getString(R.string.lab_range_invalid))
                    else -> {
                        val u = unit.trim()
                        val l = link.ifBlank { null }
                        val orig = existing?.name
                        val med = !isLab && isMedication && Medications.canBeMedication(n)
                        val lo = parseRangeValue(low)
                        val hi = parseRangeValue(high)
                        AppScope.scope.launch {
                            if (isLab) Store.saveCustomMetric(orig, n, u, null, medication = false, lab = true, setRange = true, refLow = lo, refHigh = hi)
                            else Store.saveCustomMetric(orig, n, u, l, medication = med)
                        }
                        onDismiss()
                    }
                }
            }) { Text(stringResource(R.string.type_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
