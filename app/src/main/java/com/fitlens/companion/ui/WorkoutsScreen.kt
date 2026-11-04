package com.fitlens.companion.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import com.fitlens.companion.data.Routine
import com.fitlens.companion.data.Routines
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.ui.design.ConfirmSheet
import com.fitlens.companion.ui.design.FitTextButton
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.GoldButton
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.OverflowMenu
import com.fitlens.companion.ui.design.TopBarAction
import kotlinx.coroutines.launch

/**
 * The saved workouts (#353), from the day log's ⋮ menu: every workout with its number of days and exercises. Tapping
 * one opens it in the workout editor; + and "Create new workout" start a new one; each row's ⋮ has Edit, Duplicate
 * and Delete (asks first, then offers Undo), as the editor's own menu does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(snap: Snapshot, nav: Nav) {
    val context = LocalContext.current
    // The workout waiting for Delete's confirmation, kept as its id so it survives rotation.
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }
    val create = { nav.push(Screen.WorkoutEditor(0L)) }

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = stringResource(R.string.wks_title),
            onBack = { nav.pop() },
            actions = listOf(TopBarAction(Icons.Filled.Add, stringResource(R.string.lib_create_workout)) { create() })
        )
        if (snap.routines.isEmpty()) {
            EmptyState(stringResource(R.string.wks_empty_title), stringResource(R.string.wks_empty_body)) {
                GoldButton(onClick = { create() }) { Text(stringResource(R.string.lib_create_workout)) }
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                items(snap.routines, key = { it.id }) { w ->
                    WorkoutRow(
                        w,
                        onOpen = { nav.push(Screen.WorkoutEditor(w.id)) },
                        menu = listOf(
                            MenuAction(stringResource(R.string.lib_edit_workout)) { nav.push(Screen.WorkoutEditor(w.id)) },
                            MenuAction(stringResource(R.string.workout_duplicate)) {
                                AppScope.scope.launch {
                                    Routines.copy(w, context.getString(R.string.workout_copy_name, w.name))
                                    UiEvents.show(context.getString(R.string.workout_duplicated, w.name))
                                }
                            },
                            MenuAction(stringResource(R.string.workout_delete)) { deleting = w.id }
                        )
                    )
                }
                item(key = "create") {
                    FitTextButton(onClick = { create() }, modifier = Modifier.padding(horizontal = Spacing.sm)) {
                        Text(stringResource(R.string.lib_create_workout))
                    }
                }
            }
        }
    }

    deleting?.let { snap.routinesById[it] }?.let { w ->
        ConfirmSheet(
            title = stringResource(R.string.workout_delete_title, w.name),
            message = stringResource(R.string.workout_delete_body),
            confirmLabel = stringResource(R.string.workout_delete),
            onDismiss = { deleting = null },
            onConfirm = {
                deleting = null
                AppScope.scope.launch {
                    Routines.delete(w.id)
                    UiEvents.show(context.getString(R.string.workout_deleted, w.name), context.getString(R.string.workout_undo)) {
                        AppScope.scope.launch { Routines.copy(w, w.name) }
                    }
                }
            }
        )
    }
}

/** One workout: its name (wrapping as far as it needs), "N days · N exercises" under it, and a ⋮, over a fine rule. */
@Composable
private fun WorkoutRow(w: Routine, onOpen: () -> Unit, menu: List<MenuAction>) {
    val exercises = w.days.sumOf { it.exercises.size }
    val summary = stringResource(
        R.string.wks_summary,
        pluralStringResource(R.plurals.wks_days, w.days.size, w.days.size),
        pluralStringResource(R.plurals.lib_exercises, exercises, exercises)
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = Spacing.row).clickable(onClick = onOpen).padding(start = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
            Text(w.name, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OverflowMenu(menu, description = stringResource(R.string.cmn_options_for, w.name))
    }
    HorizontalDivider(color = Brand.Hairline)
}
