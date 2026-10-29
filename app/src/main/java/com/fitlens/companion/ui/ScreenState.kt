package com.fitlens.companion.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A screen's own state (#37): which sheet or dialog is open, filters, a selection. It belongs to the screen's entry
 * in the back stack, so it outlives rotation, and it's kept in that entry's saved state, so it comes back after
 * Android stops FlexNotes in the background. Values must fit in a Bundle (Boolean, Int, Long, String and lists of them).
 *
 * A screen gets its own with `viewModel<DayState>()` and swaps `remember { mutableStateOf(x) }` for `state.saved("key", x)`.
 * A draft with nested lists (a workout or schedule being edited, #304) uses [savedList] and [savedJson], which keep it
 * as JSON text through kotlinx.serialization.
 */
open class ScreenState(private val handle: SavedStateHandle) : ViewModel() {
    private val values = HashMap<String, MutableState<*>>()
    private val lists = HashMap<String, SnapshotStateList<Long>>()
    private val drafts = HashMap<String, Any>()

    /** A value kept under [key], starting at [initial]. */
    @Suppress("UNCHECKED_CAST")
    fun <T> saved(key: String, initial: T): MutableState<T> =
        values.getOrPut(key) { SavedValue(handle, key, handle.get<T>(key) ?: initial) } as MutableState<T>

    /** A list of ids kept under [key], such as a selection. */
    fun savedIds(key: String): SnapshotStateList<Long> = lists.getOrPut(key) {
        val list = mutableStateListOf<Long>().apply { handle.get<LongArray>(key)?.let { addAll(it.toList()) } }
        viewModelScope.launch { snapshotFlow { list.toLongArray() }.collect { handle[key] = it } }
        list
    }

    /**
     * A list of [serializer]'s items kept under [key] as JSON text, such as the days of a workout being edited (#304).
     * It starts as [initial] unless the handle holds a draft that decodes; every change is written back.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> savedList(key: String, serializer: KSerializer<T>, initial: () -> List<T>): SnapshotStateList<T> =
        drafts.getOrPut(key) {
            val codec = ListSerializer(serializer)
            val start = handle.get<String>(key)?.let { runCatching { draftJson.decodeFromString(codec, it) }.getOrNull() } ?: initial()
            val list = mutableStateListOf<T>().apply { addAll(start) }
            viewModelScope.launch { snapshotFlow { list.toList() }.collect { handle[key] = draftJson.encodeToString(codec, it) } }
            list
        } as SnapshotStateList<T>

    /** One value of [serializer]'s type kept under [key] as JSON text, such as a workout's training plan (#304). */
    @Suppress("UNCHECKED_CAST")
    fun <T> savedJson(key: String, serializer: KSerializer<T>, initial: () -> T): MutableState<T> =
        drafts.getOrPut(key) {
            val decoded = handle.get<String>(key)?.let { text -> runCatching { Holder(draftJson.decodeFromString(serializer, text)) }.getOrNull() }
            val start = if (decoded != null) decoded.value else initial()
            val state = mutableStateOf(start)
            viewModelScope.launch { snapshotFlow { state.value }.collect { handle[key] = draftJson.encodeToString(serializer, it) } }
            state
        } as MutableState<T>
}

/** Wraps a decoded value so a null result (a draft of "no plan") isn't mistaken for a failed decode. */
private class Holder<T>(val value: T)

private val draftJson = Json { ignoreUnknownKeys = true }

private class SavedValue<T>(private val handle: SavedStateHandle, private val key: String, initial: T) : MutableState<T> {
    private val state = mutableStateOf(initial)
    override var value: T
        get() = state.value
        set(v) {
            state.value = v
            handle[key] = v
        }
    override fun component1(): T = value
    override fun component2(): (T) -> Unit = { value = it }
}

/** The day log's open sheets and dialogs. */
class DayState(handle: SavedStateHandle) : ScreenState(handle)

/** The photo gallery's filters, grouping and selection. */
class PhotosState(handle: SavedStateHandle) : ScreenState(handle)

/** The photo viewer's open dialogs. */
class PhotoViewerState(handle: SavedStateHandle) : ScreenState(handle)

/** The exercise screen's open sheets and dialogs; a set is kept as its id (#304). */
class SetEntryState(handle: SavedStateHandle) : ScreenState(handle)

/** The workout editor's open sheets and dialogs, and its unsaved draft (#304). */
class WorkoutEditorState(handle: SavedStateHandle) : ScreenState(handle)

/** The schedule editor's open sheets and dialogs, and its unsaved draft (#304). */
class ScheduleEditorState(handle: SavedStateHandle) : ScreenState(handle)

/** Analysis's waiting pinned graph (#304), kept in its saved form so it survives Android stopping the app. */
class AnalysisState(handle: SavedStateHandle) : ScreenState(handle)
