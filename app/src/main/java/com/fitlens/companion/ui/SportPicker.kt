package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.toArgb
import com.fitlens.companion.R
import com.fitlens.companion.data.PlainErrors
import com.fitlens.companion.data.ExerciseTypes
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.SportLibrary
import com.fitlens.companion.data.Workouts
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.SectionLabel
import kotlinx.coroutines.launch

/**
 * "Add sport exercises" in the exercise library (#247): more sports to add on request, grouped by the kind of activity
 * they count as. Nothing is added until the user confirms; a sport the library already has is shown as "Already in
 * your library" and can't be ticked. What is added can be edited or deleted like any exercise.
 */
@Composable
internal fun SportPickerSheet(snap: Snapshot, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val picks = remember(snap.libraryKey) { SportLibrary.picks(snap.exercises.values.map { it.name }) }
    val chosen = remember { mutableStateListOf<String>() }
    val groups = remember(picks) {
        picks.groupBy { it.sport.category }.entries.sortedBy { it.key?.ordinal ?: Int.MAX_VALUE }
    }
    FitSheet(
        title = stringResource(R.string.sports_title),
        onDismiss = onDismiss,
        confirmLabel = if (chosen.isEmpty()) stringResource(R.string.sports_add_none) else pluralStringResource(R.plurals.sports_add_n, chosen.size, chosen.size),
        confirmEnabled = chosen.isNotEmpty(),
        onConfirm = {
            val names = chosen.toList()
            onDismiss()
            AppScope.scope.launch {
                try {
                    val added = Workouts.addSportExtras(names, Brand.GoldLight.toArgb())
                    UiEvents.show(res.getQuantityString(R.plurals.sports_added, added, added))
                } catch (e: Exception) {
                    UiEvents.show(res.getString(R.string.sports_failed, PlainErrors.reason(res, e)))
                }
            }
        }
    ) {
        Text(
            stringResource(R.string.sports_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        groups.forEach { (category, items) ->
            SectionLabel(
                category?.let { activityCategoryText(res, it) } ?: stringResource(R.string.sports_other),
                Modifier.padding(top = Spacing.md)
            )
            items.forEach { pick ->
                val name = pick.sport.name
                val on = name in chosen
                val kind = stringResource(if (pick.sport.type == ExerciseTypes.ROUNDS) R.string.sports_kind_rounds else R.string.sports_kind_session)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.touch)
                        .then(
                            if (pick.inLibrary) Modifier
                            else Modifier.toggleable(value = on, role = Role.Checkbox, onValueChange = { if (it) chosen.add(name) else chosen.remove(name) })
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = on, onCheckedChange = null, enabled = !pick.inLibrary)
                    Column(Modifier.padding(start = Spacing.sm)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (pick.inLibrary) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (pick.inLibrary) stringResource(R.string.sports_already, kind) else kind,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
