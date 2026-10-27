package com.fitlens.companion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.fitlens.companion.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

/** A stored `HH:mm` (or `HH:mm:ss`) as a time, or null when it can't be read. */
internal fun parseTime(text: String): LocalTime? =
    runCatching { LocalTime.parse(text.trim().take(5).let { if (it.length == 4) "0$it" else it }) }.getOrNull()

/**
 * A time chosen with the clock dialog (#270), never typed: a typed time was quietly replaced by "now" or 08:00 when it
 * couldn't be read. [time] is the stored `HH:mm`; an unreadable one shows as now, and [onTime] gets a valid `HH:mm`.
 * [is24Hour] is how the field and its dial read (the phone's clock style once #280 passes it in).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeField(
    time: String,
    onTime: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = true
) {
    var open by remember { mutableStateOf(false) }
    val shown = parseTime(time) ?: LocalTime.now()
    val text = if (is24Hour) shown.format(HH_MM) else shown.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    Box(modifier) {
        OutlinedTextField(
            value = text, onValueChange = {}, readOnly = true, singleLine = true,
            label = { Text(label) }, modifier = Modifier.fillMaxWidth()
        )
        // The field itself is read-only; a transparent layer on top opens the dial.
        Box(
            Modifier.matchParentSize().clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                onClickLabel = label, role = Role.Button
            ) { open = true }
        )
    }
    if (open) {
        val state = rememberTimePickerState(shown.hour, shown.minute, is24Hour = is24Hour)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { TimePicker(state = state, colors = fitTimePickerColors()) },
            confirmButton = {
                TextButton(onClick = {
                    // ASCII digits whatever the language, so the stored `HH:mm` always reads back.
                    onTime(String.format(Locale.ROOT, "%02d:%02d", state.hour, state.minute))
                    open = false
                }) { Text(stringResource(R.string.wk_set)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
