package com.fitlens.companion.ui.design

import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import com.fitlens.companion.ui.Brand
import kotlinx.coroutines.Job

/**
 * The Reorder toggle (#170) for a top bar's `trailing` slot: drag handles show on the screen's rows only while it's
 * on, and it turns gold to say so. Screens keep [on] in `rememberSaveable` (or their `ScreenState`).
 */
@Composable
fun ReorderToggle(on: Boolean, onToggle: (Boolean) -> Unit) {
    IconToggleButton(checked = on, onCheckedChange = onToggle) {
        Icon(
            FitIcons.Reorder,
            contentDescription = stringResource(R.string.cmn_reorder),
            tint = if (on) Brand.Gold else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A list reordered in place by its drag handles (#85, #170). While a handle is held the rows follow the drag's own
 * order ([of]); it's saved once, when the finger lifts ([end]), and the rows follow the saved order again once it
 * arrives, so a drag doesn't reload the data at every step.
 */
@Stable
class InPlaceOrder<K> {
    private var dragging by mutableStateOf<List<K>?>(null)

    /** The rows' order: [shown] (as saved), or the drag's while a handle is held or its save is on the way. */
    fun of(shown: List<K>): List<K> = dragging?.takeIf { it.size == shown.size && it.toSet() == shown.toSet() } ?: shown

    /**
     * Moves [key] one place up ([by] −1) or down (+1). [move] does the move: a plain swap by default, or a rule that
     * keeps groups together, such as `moveInOrder` for supersets.
     */
    fun step(shown: List<K>, key: K, by: Int, move: (List<K>, Int, Int) -> List<K> = { l, a, b -> swapNeighbour(l, a, b) }) {
        val cur = of(shown)
        val at = cur.indexOf(key)
        if (at >= 0) dragging = move(cur, at, by)
    }

    /** The finger lifted: [save] stores the new order; its job, if any, keeps the drag's order shown until it's done. */
    fun end(shown: List<K>, save: (List<K>) -> Job?) {
        val final = dragging ?: return
        if (final == shown) {
            dragging = null
            return
        }
        val job = save(final)
        if (job == null) dragging = null else job.invokeOnCompletion { dragging = null }
    }

    internal fun clear() {
        dragging = null
    }
}

/** An [InPlaceOrder] for [shown], let go whenever the saved order changes. */
@Composable
fun <K> rememberInPlaceOrder(shown: List<K>): InPlaceOrder<K> {
    val order = remember { InPlaceOrder<K>() }
    LaunchedEffect(shown) { order.clear() }
    return order
}

/** [list] with the item at [at] swapped with its neighbour [by] places away; unchanged at either end. */
fun <K> swapNeighbour(list: List<K>, at: Int, by: Int): List<K> {
    val to = at + by
    if (at !in list.indices || to !in list.indices) return list
    return list.toMutableList().also { it[at] = list[to]; it[to] = list[at] }
}
