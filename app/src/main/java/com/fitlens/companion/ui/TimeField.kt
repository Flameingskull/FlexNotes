package com.fitlens.companion.ui

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import java.time.LocalTime
import java.util.Locale
import com.fitlens.companion.ui.design.FitAlertDialog
import com.fitlens.companion.ui.design.FitTextButton

/**
 * A time chosen with the clock dialog (#270), never typed: a typed time was quietly replaced by "now" or 08:00 when it
 * couldn't be read. [time] is the stored `HH:mm`; an unreadable one shows as now, and [onTime] gets a valid `HH:mm`.
 * [is24Hour] is how the field and its dial read; by default the phone's own clock style (#280).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeField(
    time: String,
    onTime: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current)
) {
    var open by remember { mutableStateOf(false) }
    val shown = Dates.clock(time) ?: LocalTime.now()
    val text = Dates.time(String.format(Locale.ROOT, "%02d:%02d", shown.hour, shown.minute), is24Hour)
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
        ClockDialog(
            title = label, hour = shown.hour, minute = shown.minute, is24Hour = is24Hour,
            onDismiss = { open = false }
        ) { picked ->
            // ASCII digits whatever the language, so the stored `HH:mm` always reads back.
            onTime(String.format(Locale.ROOT, "%02d:%02d", picked.hour, picked.minute))
            open = false
        }
    }
}

/**
 * The one clock dialog (#309): a dial for [hour] and [minute] that reads as 12 or 24 hours per [is24Hour] (the
 * phone's own style by default, #280), titled [title]. Set calls [onPicked] with the chosen time and leaves closing to
 * the caller; Cancel and a tap outside call [onDismiss]. Used by [TimeField], Workout Time and the reminders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClockDialog(
    title: String,
    hour: Int,
    minute: Int,
    onDismiss: () -> Unit,
    is24Hour: Boolean = DateFormat.is24HourFormat(LocalContext.current),
    onPicked: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(hour, minute, is24Hour = is24Hour)
    FitAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { TimePicker(state = state, colors = fitTimePickerColors()) },
        confirmButton = {
            FitTextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)) }) { Text(stringResource(R.string.wk_set)) }
        },
        dismissButton = { FitTextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
