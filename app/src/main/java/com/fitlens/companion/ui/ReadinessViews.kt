package com.fitlens.companion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.HealthConnect
import com.fitlens.companion.data.Readiness
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.TrainingLoad
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.SettingsNote
import com.fitlens.companion.ui.design.relativeDayLabel
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import com.fitlens.companion.ui.design.FitFilterChip

/** A readiness score's word (#216): Ready, Steady or Take it easy. */
internal fun readinessBand(res: android.content.res.Resources, score: Int): String = res.getString(
    when {
        score >= Readiness.HIGH -> R.string.rd_band_ready
        score >= Readiness.LOW -> R.string.rd_band_steady
        else -> R.string.rd_band_easy
    }
)

/** "72 · Ready". */
internal fun readinessText(res: android.content.res.Resources, score: Int): String =
    res.getString(R.string.rd_score_value, score, readinessBand(res, score))

/** "Up 6 on your 7-day average of 66", or the same day as the average. */
internal fun readinessChangeText(res: android.content.res.Resources, score: Int, average: Double): String {
    val avg = average.roundToInt()
    return when {
        score > avg -> res.getString(R.string.rd_vs_week_up, score - avg, avg)
        score < avg -> res.getString(R.string.rd_vs_week_down, avg - score, avg)
        else -> res.getString(R.string.rd_vs_week_same, avg)
    }
}

/** A check-in rating's name. */
internal fun checkInName(res: android.content.res.Resources, item: Readiness.Item): String = res.getString(
    when (item) {
        Readiness.Item.SLEEP -> R.string.rd_item_sleep
        Readiness.Item.SORENESS -> R.string.rd_item_soreness
        Readiness.Item.MOOD -> R.string.rd_item_mood
        Readiness.Item.STRESS -> R.string.rd_item_stress
        Readiness.Item.ENERGY -> R.string.rd_item_energy
    }
)

/** What 1 and 5 mean for a rating. */
private fun checkInEnds(res: android.content.res.Resources, item: Readiness.Item): String = res.getString(
    when (item) {
        Readiness.Item.SLEEP -> R.string.rd_ends_sleep
        Readiness.Item.SORENESS -> R.string.rd_ends_soreness
        Readiness.Item.MOOD -> R.string.rd_ends_mood
        Readiness.Item.STRESS -> R.string.rd_ends_stress
        Readiness.Item.ENERGY -> R.string.rd_ends_energy
    }
)

/** Everything readiness reads, worked out once per change to the body data or the Health Connect sources. */
@Composable
internal fun rememberReadinessInputs(snap: Snapshot): Readiness.Inputs {
    val prefs by Settings.portable.collectAsState()
    return remember(snap.bodyKey, prefs.hcHrv, prefs.hcRestingHr, prefs.hcSleep) { Readiness.inputs(snap, prefs) }
}

/** Every day's readiness score, oldest first. */
@Composable
internal fun rememberReadinessHistory(snap: Snapshot): List<Readiness.Score> {
    val inputs = rememberReadinessInputs(snap)
    return remember(inputs) { Readiness.history(inputs) }
}

/** [date]'s load figures for the day log's card (#217, #242): the day itself and any jump over the last training day. */
private class CardLoad(val day: TrainingLoad.Day?, val jump: Double?)

/** [date]'s training load, by Session load once sessions are logged and otherwise by volume; null without enough logged. */
@Composable
private fun rememberLoadOn(snap: Snapshot, date: String): CardLoad =
    remember(snap.setsKey, date) {
        val day = Dates.epochDay(date)
        val load = TrainingLoad.daily(snap.statSets, TrainingLoad.preferredMeasure(snap), TrainingLoad.sessionTest(snap))
        CardLoad(TrainingLoad.series(load, day, day).singleOrNull(), TrainingLoad.jumpOn(load, day))
    }

/**
 * The day log's readiness card (#216), after the calories: the day's score and how it compares with the week
 * before, or an invitation to check in. A load spike that day (#217) is named under it. Tapping it opens
 * [ReadinessSheet]; Home Screen Settings can hide it.
 */
@Composable
internal fun ReadinessCard(snap: Snapshot, date: String, onOpen: () -> Unit) {
    val res = LocalContext.current.resources
    val history = rememberReadinessHistory(snap)
    val day = Dates.epochDay(date)
    val score = history.firstOrNull { it.day == day }
    val checkedIn = score?.part(Readiness.Source.CHECK_IN) != null
    val load = rememberLoadOn(snap, date)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm)
            .background(Brand.Surface, FitShapes.card)
            .border(1.dp, Brand.Hairline, FitShapes.card)
            .clickable(onClickLabel = stringResource(R.string.rd_card_open)) { onOpen() }
            .semantics(mergeDescendants = true) {}
            .heightIn(min = Spacing.row)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Text(
            stringResource(R.string.rd_title).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (score != null) {
            Text(readinessText(res, score.score), style = MaterialTheme.typography.titleMedium)
            Readiness.weekAverage(history, day)?.let { avg ->
                Text(
                    readinessChangeText(res, score.score, avg),
                    style = MaterialTheme.typography.bodyMedium,
                    color = deltaColour(score.score - avg.roundToInt().toDouble())
                )
            }
        }
        if (!checkedIn) {
            Text(stringResource(R.string.rd_card_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (load.day?.spike == true) {
            Text(
                stringResource(R.string.load_spike_short, fmtNum(load.day.ratio ?: 0.0, 2)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        // A big rise over the last training day (#242), worded like the spike above.
        load.jump?.let { rise ->
            Text(
                stringResource(R.string.load_jump_short, (rise * 100).roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** One rating's 1–5 chips; tapping the chosen one again clears it. */
@Composable
private fun RatingRow(item: Readiness.Item, value: Double?, onValue: (Double?) -> Unit) {
    val res = LocalContext.current.resources
    val name = checkInName(res, item)
    Column(Modifier.fillMaxWidth()) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        Text(checkInEnds(res, item), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            (Readiness.MIN..Readiness.MAX).forEach { r ->
                val chosen = value != null && abs(value - r) < 0.01
                val label = res.getString(R.string.rd_rating_desc, name, r)
                FitFilterChip(
                    selected = chosen,
                    onClick = { onValue(if (chosen) null else r.toDouble()) },
                    label = { Text("$r") },
                    modifier = Modifier.semantics { contentDescription = label }
                )
            }
        }
    }
}

/**
 * The check-in for [date] (#216): five ratings saved in place (Save, Clear the day, both with Undo), then the score
 * they make with what else is logged, part by part, worked out as the ratings change.
 */
@Composable
internal fun ReadinessSheet(snap: Snapshot, date: String, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val inputs = rememberReadinessInputs(snap)
    val day = Dates.epochDay(date)
    val saved = remember(snap.bodyKey, date) { Readiness.checkInOn(snap, date) }
    val typed = remember(date) { mutableStateMapOf<Readiness.Item, Double>().apply { putAll(saved) } }
    val preview = Readiness.score(
        inputs.copy(checkIns = inputs.checkIns - day + (if (typed.isEmpty()) emptyMap() else mapOf(day to typed.toMap()))),
        day
    )
    val dayLabel = relativeDayLabel(date)
    val undoLabel = stringResource(R.string.undo)

    /** Replaces the day's check-in with [values], with Undo back to what was there. */
    fun write(values: Map<Readiness.Item, Double>, message: String) {
        val before = saved
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        AppScope.scope.launch { Store.setCheckIn(date, time, Readiness.recordsOn(snap, date).map { it.id }, values) }
        UiEvents.show(message, undoLabel) {
            AppScope.scope.launch {
                val now = Store.snapshot.value ?: return@launch
                Store.setCheckIn(date, time, Readiness.recordsOn(now, date).map { it.id }, before)
            }
        }
        onDismiss()
    }

    FitSheet(
        title = stringResource(R.string.rd_sheet_title, dayLabel),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.rd_save),
        confirmEnabled = typed.isNotEmpty(),
        onConfirm = { write(typed.toMap(), res.getString(R.string.rd_saved, dayLabel)) },
        secondaryLabel = if (saved.isNotEmpty()) stringResource(R.string.rd_clear) else null,
        onSecondary = if (saved.isNotEmpty()) {
            { write(emptyMap(), res.getString(R.string.rd_cleared, dayLabel)) }
        } else null
    ) {
        SectionLabel(stringResource(R.string.rd_check_in))
        Text(stringResource(R.string.rd_sheet_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Readiness.Item.entries.forEach { item ->
            RatingRow(item, typed[item]) { v -> if (v == null) typed.remove(item) else typed[item] = v }
        }

        SectionLabel(stringResource(R.string.rd_score))
        if (preview == null) {
            SettingsNote(stringResource(R.string.rd_no_score))
        } else {
            Figure(stringResource(R.string.rd_title), readinessText(res, preview.score), big = true)
            preview.parts.forEach { p ->
                when (p.source) {
                    Readiness.Source.CHECK_IN -> Figure(stringResource(R.string.rd_part_check_in), stringResource(R.string.rd_part_check_in_value, fmtNum(p.value, 1)))
                    Readiness.Source.HRV -> Figure(
                        stringResource(R.string.rd_part_hrv),
                        stringResource(R.string.rd_part_vs_usual, fmtNum(p.value, 0), HealthConnect.Names.HRV_UNIT, fmtNum(p.usual ?: 0.0, 0))
                    )
                    Readiness.Source.RESTING_HR -> Figure(
                        stringResource(R.string.rd_part_rhr),
                        stringResource(R.string.rd_part_vs_usual, fmtNum(p.value, 0), HealthConnect.Names.RESTING_HR_UNIT, fmtNum(p.usual ?: 0.0, 0))
                    )
                    Readiness.Source.SLEEP -> Figure(stringResource(R.string.rd_part_sleep), stringResource(R.string.rd_part_sleep_value, fmtNum(p.value, 1)))
                }
            }
        }
        if (preview == null || preview.part(Readiness.Source.HRV) == null || preview.part(Readiness.Source.RESTING_HR) == null) {
            SettingsNote(stringResource(R.string.rd_heart_note, Readiness.MIN_BASELINE))
        }
        SettingsNote(stringResource(R.string.rd_note))
    }
}
