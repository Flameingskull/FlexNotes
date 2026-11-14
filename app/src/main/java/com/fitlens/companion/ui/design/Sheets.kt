@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.fitlens.companion.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.fitlens.companion.ui.Brand
import com.fitlens.companion.ui.Dot
import com.fitlens.companion.ui.FitShapes
import com.fitlens.companion.ui.GoldHairline
import com.fitlens.companion.ui.Spacing
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R

/**
 * Keeps a sheet's scrolling content from dragging the sheet itself (#103). Without it, pulling a list down once it is
 * already at the top, or a fling that runs past the top, is handed on to the modal sheet, which slides away and
 * throws away whatever was chosen or typed. Put it on the scrolling content: whatever the content leaves unconsumed
 * stops here, so scrolling only ever scrolls. The sheet's drag handle still closes it.
 */
private object KeepScrollInSheet : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
}

/** Stands for a `null` value in [rememberDirty], so it never equals the text "null". */
private const val NULL_VALUE = "\u0000null"

/**
 * Whether a sheet's typed values differ from what they were when it first appeared (#334). Pass every value the
 * person can change, as plain values (strings, numbers, booleans, enums, lists): each value's text now is compared
 * with its text at the start, one value at a time, so typing and then undoing the typing is not "unsaved", and
 * `("a, b", "c")` is never taken for `("a", "b, c")`. Kept with `rememberSaveable`, so turning the phone keeps the
 * starting point. Call it where the sheet's own state is declared, and pass the result to [FitSheet]'s `dirty`.
 */
@Composable
fun rememberDirty(vararg draft: Any?): Boolean {
    val now = ArrayList<String>(draft.size)
    draft.forEach { now.add(it?.toString() ?: NULL_VALUE) }
    val atStart = rememberSaveable { now }
    return now != atStart
}

/** Whether a drag, a tap outside or Back may close a sheet: not while it holds unsaved input (#334). */
internal fun sheetMayClose(dirty: Boolean): Boolean = !dirty

/**
 * A modal bottom sheet in the FlexNotes style (#80): [title], gold hairline, scrolling [content] and a sticky
 * button row. The confirm button appears when both [confirmLabel] and [onConfirm] are given; use a specific verb
 * ("Save set", "Delete workout"), never "OK". [destructive] colours the confirm button as an error action.
 * The caller closes the sheet (usually inside [onConfirm]); [onDismiss] runs on Cancel, back or a tap outside.
 * While [dirty] is true (the sheet holds typed values that aren't saved yet, see [rememberDirty]) a drag, a tap
 * outside and Back do nothing, so nothing typed is lost by accident (#334); Cancel and the confirm button still close it.
 * [secondaryLabel] and [onSecondary] add an outlined button before the confirm button, such as "Save and add another".
 * With [expandable] the title row gets a button that takes the sheet to full screen and back, and a drag up on the
 * title, hairline or buttons does the same; [startExpanded] opens it full screen (#331). Both default to off.
 */
@Composable
fun FitSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true,
    dismissLabel: String = stringResource(R.string.cancel),
    destructive: Boolean = false,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    dirty: Boolean = false,
    expandable: Boolean = false,
    startExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    // Read at the moment of the drag or tap, not when the sheet state was made (#334).
    val dirtyNow by rememberUpdatedState(dirty)
    // Full screen is the sheet's own state, kept when the phone is turned (#331).
    var full by rememberSaveable { mutableStateOf(expandable && startExpanded) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = {
            if (it == SheetValue.Hidden && full && !sheetMayClose(dirtyNow)) {
                // With something typed, Back or a drag down from full screen shrinks the sheet and keeps the text (#331).
                full = false
                false
            } else it != SheetValue.Hidden || sheetMayClose(dirtyNow)
        }
    )
    // The sheet's own drag: up goes full screen; down from full screen goes back to the usual height; down at the
    // usual height closes it, unless something is typed (#331, #334). Used on the content column and the handle.
    val onDismissNow by rememberUpdatedState(onDismiss)
    val dragToResize = Modifier.pointerInput(expandable) {
        if (!expandable) return@pointerInput
        val needed = 24.dp.toPx()
        var moved = 0f
        detectVerticalDragGestures(
            onDragStart = { moved = 0f },
            onDragEnd = {
                if (moved < -needed) full = true
                else if (moved > needed) {
                    if (full) full = false else if (sheetMayClose(dirtyNow)) onDismissNow()
                }
            },
            onDragCancel = { moved = 0f },
            onVerticalDrag = { _, amount -> moved += amount }
        )
    }
    val handle: @Composable () -> Unit = {
        if (expandable) Box(Modifier.testTag("sheet-handle").then(dragToResize)) { BottomSheetDefaults.DragHandle() }
        else BottomSheetDefaults.DragHandle()
    }
    ModalBottomSheet(
        onDismissRequest = { if (sheetMayClose(dirtyNow)) onDismiss() },
        modifier = modifier,
        sheetState = sheetState,
        shape = FitShapes.sheet,
        containerColor = Brand.Onyx,
        contentColor = Brand.Ivory,
        dragHandle = handle
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (expandable && full) Modifier.fillMaxHeight().imePadding() else Modifier)
                .then(dragToResize)
                .testTag("fit-sheet")
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.lg, end = if (expandable) Spacing.xs else Spacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (expandable) {
                    IconButton(onClick = { full = !full }) {
                        Icon(
                            if (full) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
                            contentDescription = stringResource(if (full) R.string.sheet_collapse else R.string.sheet_expand),
                            tint = Brand.Gold
                        )
                    }
                }
            }
            GoldHairline(Modifier.padding(top = Spacing.sm, bottom = Spacing.md))
            Column(
                Modifier
                    .weight(1f, fill = expandable && full)
                    .nestedScroll(KeepScrollInSheet)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                content()
            }
            val buttons: @Composable () -> Unit = {
                FitTextButton(onClick = onDismiss) { Text(dismissLabel) }
                val secondary = onSecondary
                if (secondary != null && secondaryLabel != null) {
                    GlassOutlinedButton(onClick = secondary, enabled = confirmEnabled) { Text(secondaryLabel) }
                }
                val confirm = onConfirm
                if (confirm != null && confirmLabel != null) {
                    // Gold text on a dark fill, never black on gold (#104).
                    if (destructive) {
                        Button(
                            onClick = confirm,
                            enabled = confirmEnabled,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) { Text(confirmLabel) }
                    } else {
                        GoldButton(onClick = confirm, enabled = confirmEnabled) { Text(confirmLabel) }
                    }
                }
            }
            if (expandable) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) { buttons() }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) { buttons() }
            }
        }
    }
}

/**
 * A confirmation sheet for destructive or significant actions (#80). [confirmLabel] must name the action
 * ("Delete set", "Replace data"); the sheet closes itself after [onConfirm].
 */
@Composable
fun ConfirmSheet(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = true,
    dismissLabel: String = stringResource(R.string.cancel)
) {
    FitSheet(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        onConfirm = { onConfirm(); onDismiss() },
        dismissLabel = dismissLabel,
        destructive = destructive
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One choice in a [SearchablePicker]. [section] is the chip it files under (a category, for example). */
data class PickerItem(
    val id: Long,
    val title: String,
    val subtitle: String? = null,
    val section: String? = null,
    val color: Color? = null
)

/**
 * A full-height picker sheet (#80) with a search field, section chips (for example categories) and a list.
 *
 * Single-select calls [onPick] with one id as soon as a row is tapped. With [multiSelect], rows get checkboxes and
 * a sticky "Add N" button confirms the choice. The caller closes the sheet in [onPick]. Serves as the category
 * picker too: pass the categories as [PickerItem]s with no sections.
 */
@Composable
fun SearchablePicker(
    title: String,
    items: List<PickerItem>,
    onDismiss: () -> Unit,
    onPick: (List<Long>) -> Unit,
    multiSelect: Boolean = false,
    searchLabel: String = stringResource(R.string.cmn_search),
    emptyText: String = stringResource(R.string.cmn_no_match)
) {
    // The search text, the section chip and the ticked rows are saved, so turning the phone keeps a half-made
    // choice (#311). Only ids are kept, never the rows.
    val chosen = rememberSaveable(
        saver = listSaver<SnapshotStateList<Long>, Long>(save = { it.toList() }, restore = { it.toMutableStateList() })
    ) { mutableStateListOf<Long>() }
    val selectedSuffix = stringResource(R.string.cmn_selected_suffix)
    // With exercises ticked, only Cancel closes the picker: a drag, a tap outside or Back leave it open, so a choice
    // is never lost by accident (#103).
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || chosen.isEmpty() }
    )
    var query by rememberSaveable { mutableStateOf("") }
    var section by rememberSaveable { mutableStateOf<String?>(null) }
    val sections = remember(items) { items.mapNotNull { it.section }.distinct() }
    val q = query.trim()
    val shown = items.filter { item ->
        (section == null || item.section == section) &&
            (q.isEmpty() || item.title.contains(q, ignoreCase = true) || (item.subtitle?.contains(q, ignoreCase = true) == true))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = FitShapes.sheet,
        containerColor = Brand.Onyx,
        contentColor = Brand.Ivory
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Spacing.lg))
            GoldHairline(Modifier.padding(top = Spacing.sm, bottom = Spacing.md))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(searchLabel) },
                leadingIcon = { SearchFieldIcon() },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg)
            )
            if (sections.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    item {
                        FitFilterChip(selected = section == null, onClick = { section = null }, label = { Text(stringResource(R.string.cmn_all)) })
                    }
                    items(sections) { s ->
                        FitFilterChip(selected = section == s, onClick = { section = if (section == s) null else s }, label = { Text(s) })
                    }
                }
            }
            LazyColumn(Modifier.weight(1f).nestedScroll(KeepScrollInSheet)) {
                if (shown.isEmpty()) {
                    item {
                        Text(
                            emptyText,
                            modifier = Modifier.padding(Spacing.lg),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(shown, key = { it.id }) { item ->
                    val isChosen = item.id in chosen
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = Spacing.row)
                            .clickable {
                                if (multiSelect) {
                                    if (isChosen) chosen.remove(item.id) else chosen.add(item.id)
                                } else {
                                    onPick(listOf(item.id))
                                }
                            }
                            .semantics(mergeDescendants = true) {
                                if (multiSelect) contentDescription = item.title + if (isChosen) selectedSuffix else ""
                            }
                            .padding(horizontal = Spacing.lg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val c = item.color
                        if (c != null) {
                            Dot(c, Spacing.md)
                            Spacer(Modifier.width(Spacing.md))
                        }
                        Column(Modifier.weight(1f).padding(vertical = Spacing.sm)) {
                            Text(item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val sub = item.subtitle
                            if (!sub.isNullOrBlank()) {
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (multiSelect) FitCheckbox(checked = isChosen, onCheckedChange = null)
                    }
                }
            }
            if (multiSelect) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FitTextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    GoldButton(onClick = { onPick(chosen.filter { id -> items.any { it.id == id } }) }, enabled = chosen.isNotEmpty()) {
                        Text(if (chosen.isEmpty()) stringResource(R.string.cmn_add) else stringResource(R.string.cmn_add_n, chosen.size))
                    }
                }
            }
        }
    }
}
