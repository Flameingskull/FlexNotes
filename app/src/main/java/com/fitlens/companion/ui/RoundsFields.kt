package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.CardioZones
import com.fitlens.companion.data.Rounds
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.DropdownPill
import com.fitlens.companion.ui.design.StepperField

private const val ROUND_STEP_SEC = 30
private const val REST_STEP_SEC = 15

/** The typed fields of a Rounds set (#246), as the user types them. */
internal data class RoundsDraft(
    val rounds: String = "",
    /** One round's length in minutes ("3", "2.5") or "m:ss". */
    val length: String = "",
    /** The rest between rounds in seconds, or "m:ss". */
    val rest: String = "",
    val hard: String = ""
) {
    val roundCount: Int get() = rounds.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0
    val lengthSec: Int get() = roundsTextToSeconds(length)
    val restSec: Int get() = roundsRestToSeconds(rest)
    val hardCount: Int get() = hard.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0

    /** Total time, worked out from the rounds, their length and the rests; 0 until there are rounds and a length. */
    val totalSec: Int get() = Rounds.totalSec(roundCount, lengthSec, restSec)

    /** The hard time to store, or null when no round was hard. */
    val hardSec: Int? get() = Rounds.hardSec(roundCount, hardCount, lengthSec)

    /** More hard rounds than rounds. */
    val hardTooMany: Boolean get() = hardCount > roundCount

    val isBlank: Boolean get() = roundCount == 0 && lengthSec == 0

    companion object {
        /** A draft from a stored set's values (rounds, total time, rest between rounds, hard time). */
        fun of(rounds: Int, totalSec: Int, restSec: Int?, liveSec: Int?): RoundsDraft {
            if (rounds <= 0 || totalSec <= 0) return RoundsDraft(rounds = rounds.takeIf { it > 0 }?.toString() ?: "")
            val len = Rounds.roundSec(rounds, totalSec, restSec)
            return RoundsDraft(
                rounds = rounds.toString(),
                length = roundsSecondsToText(len),
                rest = restSec?.takeIf { it > 0 }?.toString() ?: "",
                hard = Rounds.hardRounds(rounds, liveSec, len).takeIf { it > 0 }?.toString() ?: ""
            )
        }
    }
}

private fun stepSeconds(current: Int, dir: Int, by: Int): Int = (current + dir * by).coerceAtLeast(0)

/**
 * The fields of a Rounds set (#246), shared by the Track tab and the workout editor's predefined sets: how many
 * rounds, how long each is, the rest between them, how many were hard, how hard the whole thing felt (only when
 * [onRpe] is given, because a workout can't predefine a rating) and the cardio zone. The total time is worked out and
 * shown under them. Emits its fields into the caller's column so they space like the other fields.
 */
@Composable
internal fun RoundsFields(
    date: String,
    draft: RoundsDraft,
    onDraft: (RoundsDraft) -> Unit,
    zone: Int?,
    onZone: (Int?) -> Unit,
    onOpenProfile: () -> Unit,
    rpe: Double? = null,
    onRpe: ((Double?) -> Unit)? = null
) {
    val res = LocalContext.current.resources
    var zoneHelp by remember { mutableStateOf(false) }

    StepperField(
        label = stringResource(R.string.rounds_field_rounds),
        value = draft.rounds,
        onValue = { onDraft(draft.copy(rounds = it.filter { c -> c.isDigit() })) },
        onStep = { dir -> onDraft(draft.copy(rounds = (draft.roundCount + dir).coerceAtLeast(0).takeIf { it > 0 }?.toString() ?: "")) },
        keyboard = KeyboardType.Number
    )
    StepperField(
        label = stringResource(R.string.rounds_field_length),
        value = draft.length,
        onValue = { onDraft(draft.copy(length = it)) },
        onStep = { dir -> onDraft(draft.copy(length = roundsSecondsToText(stepSeconds(draft.lengthSec, dir, ROUND_STEP_SEC)))) },
        keyboard = KeyboardType.Text
    )
    StepperField(
        label = stringResource(R.string.rounds_field_rest),
        value = draft.rest,
        onValue = { onDraft(draft.copy(rest = it)) },
        onStep = { dir -> onDraft(draft.copy(rest = stepSeconds(draft.restSec, dir, REST_STEP_SEC).takeIf { it > 0 }?.toString() ?: "")) },
        keyboard = KeyboardType.Text
    )
    StepperField(
        label = stringResource(R.string.rounds_field_hard),
        value = draft.hard,
        onValue = { onDraft(draft.copy(hard = it.filter { c -> c.isDigit() })) },
        onStep = { dir -> onDraft(draft.copy(hard = (draft.hardCount + dir).coerceAtLeast(0).takeIf { it > 0 }?.toString() ?: "")) },
        keyboard = KeyboardType.Number
    )
    if (draft.hardTooMany) {
        Text(
            stringResource(R.string.rounds_hard_too_many),
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    if (draft.totalSec > 0) {
        Text(
            stringResource(R.string.rounds_total, fmtNum(draft.totalSec / 60.0, 1)) +
                (draft.hardSec?.let { " · " + stringResource(R.string.rounds_total_hard, fmtNum(it / 60.0, 1)) } ?: ""),
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (onRpe != null) {
        // The rating is held as typed while it is being typed, and re-read whenever it changes from outside.
        var text by remember(rpe) { mutableStateOf(rpe?.let { fmtNum(it, 1) } ?: "") }
        StepperField(
            label = stringResource(R.string.rounds_field_rpe),
            value = text,
            onValue = { t ->
                text = t
                val v = t.trim().replace(',', '.')
                if (v.isEmpty()) onRpe(null)
                else v.toDoubleOrNull()?.let { onRpe(Math.round(it.coerceIn(0.0, 10.0) * 2.0) / 2.0) }
            },
            onStep = { dir -> onRpe(if (rpe == null) 5.0 else (rpe + dir * 0.5).coerceIn(0.0, 10.0)) }
        )
        Text(
            if (rpe == null) stringResource(R.string.session_rpe_unrated) else sessionRpeWord(res, rpe),
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    val notSet = stringResource(R.string.session_not_set)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val zones = CardioZones.all
        DropdownPill(
            stringResource(R.string.session_zone),
            listOf(notSet) + zones.map { zoneText(res, it) },
            zones.indexOf(zone) + 1
        ) { i -> onZone(zones.getOrNull(i - 1)) }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { zoneHelp = true }) {
            Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.session_zone_help))
        }
    }
    if (zoneHelp) ZoneHelpSheet(date, onDismiss = { zoneHelp = false }, onOpenProfile = onOpenProfile)
}
