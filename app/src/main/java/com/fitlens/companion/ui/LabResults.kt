package com.fitlens.companion.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.FitSheet
import kotlinx.coroutines.launch

/**
 * Reference ranges (#211): any body measurement, lab marker or vital can have one, set from the Body tracker's
 * Measurements editor or the measurement's ⋮. A value outside it is flagged Low or High wherever it's listed, with an
 * arrow so the flag never relies on colour, and the graph shades the range.
 */

/** "Range 1–3 mmol/L", "Range up to 3 mmol/L" or "Range from 1 mmol/L", or null when no range is set. */
internal fun rangeText(res: Resources, def: MeasurementDef?): String? {
    if (def == null || !def.hasRange) return null
    val unit = def.unit.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
    val low = def.refLow
    val high = def.refHigh
    return when {
        low != null && high != null -> res.getString(R.string.lab_range_both, fmtNum(low), "${fmtNum(high)}$unit")
        high != null -> res.getString(R.string.lab_range_high, "${fmtNum(high)}$unit")
        else -> res.getString(R.string.lab_range_low, "${fmtNum(low ?: 0.0)}$unit")
    }
}

/** "▲ High" or "▼ Low" for a value outside [def]'s range, or null for one in it or without a range. */
internal fun flagText(res: Resources, def: MeasurementDef?, value: Double): String? = when (def?.rangeFlag(value)) {
    1 -> res.getString(R.string.lab_flag_high)
    -1 -> res.getString(R.string.lab_flag_low)
    else -> null
}

/** The flag for a value outside its range, in the theme's error colour, or nothing. */
@Composable
internal fun RangeFlag(def: MeasurementDef?, value: Double, modifier: Modifier = Modifier) {
    val text = flagText(LocalContext.current.resources, def, value) ?: return
    Text(text, modifier, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, maxLines = 1)
}

/** A typed number, with a comma read as a decimal point; blank is none. */
internal fun parseRangeValue(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

/** The low and high fields shared by [ReferenceRangeSheet] and the lab marker editor, in [unit]. */
@Composable
internal fun RangeFields(unit: String, low: String, high: String, onLow: (String) -> Unit, onHigh: (String) -> Unit) {
    val u = unit.trim()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = low, onValueChange = onLow,
            label = { Text(if (u.isBlank()) stringResource(R.string.lab_low) else stringResource(R.string.lab_low_unit, u)) },
            singleLine = true, isError = low.isNotBlank() && parseRangeValue(low) == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
        OutlinedTextField(
            value = high, onValueChange = onHigh,
            label = { Text(if (u.isBlank()) stringResource(R.string.lab_high) else stringResource(R.string.lab_high_unit, u)) },
            singleLine = true, isError = high.isNotBlank() && parseRangeValue(high) == null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1f)
        )
    }
}

/** The range is usable: each end blank or a number, and the low end below the high one. */
internal fun rangeValid(low: String, high: String): Boolean {
    val l = parseRangeValue(low)
    val h = parseRangeValue(high)
    if (low.isNotBlank() && l == null) return false
    if (high.isNotBlank() && h == null) return false
    return l == null || h == null || l < h
}

/** Sets or clears [def]'s reference range, in the unit it's shown in. */
@Composable
internal fun ReferenceRangeSheet(def: MeasurementDef, onDismiss: () -> Unit) {
    var low by remember { mutableStateOf(def.refLow?.let { fmtNum(it) } ?: "") }
    var high by remember { mutableStateOf(def.refHigh?.let { fmtNum(it) } ?: "") }
    FitSheet(
        title = stringResource(R.string.lab_range_title, def.name),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.type_save),
        confirmEnabled = rangeValid(low, high),
        onConfirm = {
            val l = parseRangeValue(low)
            val h = parseRangeValue(high)
            AppScope.scope.launch { Store.setReferenceRange(def.name, def.unit, l, h) }
            onDismiss()
        },
        secondaryLabel = if (def.hasRange) stringResource(R.string.lab_range_clear) else null,
        onSecondary = if (def.hasRange) ({
            AppScope.scope.launch { Store.setReferenceRange(def.name, def.unit, null, null) }
            onDismiss()
        }) else null
    ) {
        RangeFields(def.unit, low, high, { low = it }, { high = it })
        Text(
            stringResource(R.string.lab_range_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
