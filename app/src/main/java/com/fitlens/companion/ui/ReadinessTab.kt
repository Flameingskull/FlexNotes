package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.Readiness
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.TrainingLoad
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.DropdownPill
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.SectionLabel
import java.time.LocalDate
import kotlin.math.roundToInt

/** Neither direction of a ratio is better on its own: points stay plain. */
private val noBetter: (Double, Double) -> Boolean? = { _, _ -> null }

/**
 * One graph of the Readiness tab: chart type and full screen in a row over it, the picked point's day and value under
 * it. [graphId] remembers the chart type; [goal] draws a line at a threshold.
 */
@Composable
private fun ReadinessGraph(
    title: String,
    graphId: String,
    points: List<ChartPoint>,
    format: (Double) -> String,
    goal: Double? = null,
    better: (Double, Double) -> Boolean? = ::higherIsBetter,
    fromZero: Boolean = true,
    /** A shaded reference range with a line at each end (#242: the good band of the load ratio). */
    range: Pair<Double?, Double?>? = null
) {
    val res = LocalContext.current.resources
    val (kind, setKind) = rememberChartKind(graphId)
    var sel by remember(points) { mutableStateOf<Int?>(null) }
    var fullScreen by remember { mutableStateOf(false) }
    val series = listOf(LineSeries(title, points))
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        DropdownPill(stringResource(R.string.an_chart_type), ChartKind.entries.map { it.text(res) }, kind.ordinal) { i -> setKind(ChartKind.entries[i]) }
        Spacer(Modifier.weight(1f))
        ExpandGraphButton { fullScreen = true }
    }
    FitChart(
        series,
        Modifier.padding(horizontal = 8.dp),
        kind = kind,
        goal = goal,
        range = range,
        selected = sel?.let { ChartSelection(0, it) },
        onSelect = { sel = it.index; ChartHints.tapped() },
        yFormat = format,
        yFromZero = fromZero,
        better = better,
        onExpand = { ChartHints.expanded(); fullScreen = true }
    )
    sel?.let { points.getOrNull(it) }?.let { p -> AnalysisNote(res.getString(R.string.an_label_value, Dates.long(p.date), format(p.y))) }
    if (fullScreen) {
        FullScreenChart(
            title,
            onDismiss = { fullScreen = false },
            valueZoom = kind != ChartKind.BAR,
            footer = {
                sel?.let { points.getOrNull(it) }?.let { p ->
                    Text(
                        res.getString(R.string.an_label_value, Dates.long(p.date), format(p.y)),
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        ) { vp, h, resetZoom ->
            FitChart(
                series,
                kind = kind,
                height = h,
                goal = goal,
                range = range,
                selected = sel?.let { ChartSelection(0, it) },
                onSelect = { sel = it.index },
                yFormat = format,
                yFromZero = fromZero,
                better = better,
                viewport = vp,
                onExpand = resetZoom
            )
        }
    }
}

/**
 * Internal against external load (#242): each week's Session load, hard minutes and volume on one graph. The three
 * are in different units, so each is drawn as a share of its own best week; picking a week lists its real figures.
 */
@Composable
private fun InternalExternalGraph(snap: Snapshot, weeks: List<TrainingLoad.Week>) {
    val res = LocalContext.current.resources
    var sel by remember(weeks) { mutableStateOf<ChartSelection?>(null) }
    val volumeOf: (TrainingLoad.Week) -> Double = { snap.weight(it.volumeKg, null) }
    val parts = listOf(
        Triple(res.getString(R.string.load_ie_session), { w: TrainingLoad.Week -> w.sessionLoad }, 0),
        Triple(res.getString(R.string.load_ie_hard), { w: TrainingLoad.Week -> w.hardMinutes }, 1),
        Triple(res.getString(R.string.load_ie_volume, snap.weightUnit), { w: TrainingLoad.Week -> volumeOf(w) }, 2)
    )
    val series = parts.map { (name, value, _) ->
        val best = weeks.maxOf { value(it) }
        LineSeries(name, weeks.map { w ->
            ChartPoint(w.endDay, if (best > 0) value(w) / best * 100.0 else 0.0, LocalDate.ofEpochDay(w.endDay).format(Dates.ISO))
        })
    }
    FitChart(
        series,
        Modifier.padding(horizontal = 8.dp),
        selected = sel,
        onSelect = { sel = it; ChartHints.tapped() },
        yFormat = { fmtNum(it, 0) + "%" },
        yFromZero = true,
        better = noBetter
    )
    val picked = sel?.let { weeks.getOrNull(it.index) }
    AnalysisNote(
        if (picked == null) res.getString(R.string.load_ie_hint)
        else res.getString(
            R.string.load_ie_week, Dates.day(LocalDate.ofEpochDay(picked.endDay).format(Dates.ISO)),
            fmtNum(picked.sessionLoad, 0), fmtNum(picked.hardMinutes, 0), fmtNum(volumeOf(picked), 0), snap.weightUnit
        )
    )
}

/**
 * Analysis → Readiness (#216, #217): today's readiness with Check in, the score over time, then training load: the
 * acute and chronic load, the acute:chronic ratio with a spike warning, and monotony and strain, each graphed over
 * the chosen range.
 */
@Composable
internal fun ReadinessTab(snap: Snapshot) {
    val res = LocalContext.current.resources
    var rangeIdx by rememberSaveable { mutableIntStateOf(1) }
    var measureIdx by rememberSaveable { mutableIntStateOf(0) }
    var checkIn by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now()
    val todayDay = today.toEpochDay()
    val todayIso = today.format(Dates.ISO)
    val history = rememberReadinessHistory(snap)
    val measure = TrainingLoad.Measure.entries[measureIdx]
    val isSession = remember(snap.libraryKey) { TrainingLoad.sessionTest(snap) }
    val loadByDay = remember(snap.setsKey, snap.libraryKey, measure) { TrainingLoad.daily(snap.statSets, measure, isSession) }
    // "All" starts at the first thing logged.
    val range = RANGES[rangeIdx].second
    val from = if (range > 0) todayDay - range + 1
    else listOfNotNull(history.firstOrNull()?.day, loadByDay.keys.minOrNull()).minOrNull() ?: todayDay
    val load = remember(loadByDay, from, todayDay) { TrainingLoad.series(loadByDay, from, todayDay) }
    val unit = when (measure) {
        TrainingLoad.Measure.VOLUME -> snap.weightUnit
        TrainingLoad.Measure.SETS -> res.getString(R.string.load_sets_unit)
        TrainingLoad.Measure.SESSION -> res.getString(R.string.load_session_unit)
    }
    val shownLoad: (Double) -> Double = { if (measure == TrainingLoad.Measure.VOLUME) snap.weight(it, null) else it }
    val loadText: (Double) -> String = { res.getString(R.string.load_per_day, fmtNum(shownLoad(it), if (measure == TrainingLoad.Measure.VOLUME) 0 else 1), unit) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DropdownPill(stringResource(R.string.ch_range), RANGES.map { rangeName(res, it.first) }, rangeIdx) { rangeIdx = it }
        }

        // ---------- Readiness (#216) ----------
        SectionLabel(stringResource(R.string.rd_title), Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
        val todayScore = history.lastOrNull()?.takeIf { it.day == todayDay }
        if (todayScore != null) {
            Figure(stringResource(R.string.rd_today), readinessText(res, todayScore.score), big = true)
            Readiness.weekAverage(history, todayDay)?.let { avg ->
                AnalysisNote(readinessChangeText(res, todayScore.score, avg), deltaColour(todayScore.score - avg.roundToInt().toDouble()))
            }
        } else {
            AnalysisNote(stringResource(R.string.rd_not_today))
        }
        GlassOutlinedButton(onClick = { checkIn = todayIso }, modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.xs)) {
            Text(stringResource(if (todayScore?.part(Readiness.Source.CHECK_IN) != null) R.string.rd_edit_check_in else R.string.rd_check_in_now))
        }
        val scorePoints = history.filter { it.day >= from }.map { ChartPoint(it.day, it.score.toDouble(), LocalDate.ofEpochDay(it.day).format(Dates.ISO)) }
        if (scorePoints.isEmpty()) {
            AnalysisNote(stringResource(R.string.rd_empty))
        } else {
            ReadinessGraph(stringResource(R.string.rd_title), "analysis:readiness", scorePoints, { fmtNum(it, 0) })
            val avg = scorePoints.map { it.y }.average()
            AnalysisNote(res.getQuantityString(R.plurals.rd_range_average, scorePoints.size, avg.roundToInt(), scorePoints.size))
        }

        // ---------- Training load (#217) ----------
        SectionLabel(stringResource(R.string.load_title), Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            DropdownPill(
                stringResource(R.string.load_measure),
                listOf(
                    stringResource(R.string.load_measure_volume), stringResource(R.string.load_measure_sets),
                    stringResource(R.string.load_measure_session)
                ),
                measureIdx
            ) { measureIdx = it }
        }
        val now = load.lastOrNull()
        // Sessions with no rating can't be given a load, so the screen says how many are left out (#242).
        if (measure == TrainingLoad.Measure.SESSION) {
            val unrated = TrainingLoad.unrated(snap.statSets, isSession, from, todayDay)
            if (unrated > 0) AnalysisNote(res.getQuantityString(R.plurals.load_unrated, unrated, unrated))
        }
        if (now == null || loadByDay.isEmpty()) {
            AnalysisNote(stringResource(if (measure == TrainingLoad.Measure.SESSION) R.string.load_empty_session else R.string.load_empty))
        } else {
            Figure(stringResource(R.string.load_acute, TrainingLoad.ACUTE_DAYS), loadText(now.acute))
            Figure(stringResource(R.string.load_chronic, TrainingLoad.CHRONIC_DAYS), loadText(now.chronic))
            Figure(stringResource(R.string.load_ratio), now.ratio?.let { stringResource(R.string.load_ratio_value, fmtNum(it, 2)) } ?: stringResource(R.string.load_not_yet), big = true)
            val ratio = now.ratio
            when {
                ratio == null -> AnalysisNote(stringResource(R.string.load_ratio_wait, TrainingLoad.CHRONIC_DAYS))
                now.spike -> AnalysisNote(
                    stringResource(R.string.load_spike, fmtNum(ratio, 2), fmtNum(TrainingLoad.SPIKE, 1)),
                    MaterialTheme.colorScheme.error
                )
                TrainingLoad.band(ratio) == TrainingLoad.Band.CAUTION -> AnalysisNote(
                    stringResource(R.string.load_caution, fmtNum(ratio, 2), fmtNum(TrainingLoad.GOOD_MAX, 1)),
                    Brand.Gold
                )
                ratio < TrainingLoad.LOW -> AnalysisNote(stringResource(R.string.load_low, fmtNum(ratio, 2)))
                else -> AnalysisNote(stringResource(R.string.load_steady, fmtNum(ratio, 2)))
            }
            val ratioPoints = load.mapNotNull { d -> d.ratio?.let { ChartPoint(d.day, it, LocalDate.ofEpochDay(d.day).format(Dates.ISO)) } }
            if (ratioPoints.isNotEmpty()) {
                ReadinessGraph(stringResource(R.string.load_ratio), "analysis:load_ratio", ratioPoints, { fmtNum(it, 2) }, goal = TrainingLoad.SPIKE, better = noBetter,
                    range = TrainingLoad.LOW to TrainingLoad.GOOD_MAX)
                AnalysisNote(stringResource(R.string.load_bands, fmtNum(TrainingLoad.LOW, 1), fmtNum(TrainingLoad.GOOD_MAX, 1), fmtNum(TrainingLoad.SPIKE, 1)))
                val spikes = load.filter { it.spike }
                AnalysisNote(
                    if (spikes.isEmpty()) stringResource(R.string.load_no_spikes, fmtNum(TrainingLoad.SPIKE, 1))
                    else res.getQuantityString(
                        R.plurals.load_spikes, spikes.size, spikes.size, fmtNum(TrainingLoad.SPIKE, 1),
                        Dates.day(LocalDate.ofEpochDay(spikes.last().day).format(Dates.ISO)), fmtNum(spikes.last().ratio ?: 0.0, 2)
                    )
                )
            }

            Figure(stringResource(R.string.load_monotony), now.monotony?.let { fmtNum(it, 2) } ?: stringResource(R.string.load_not_yet))
            now.strain?.let { Figure(stringResource(R.string.load_strain), fmtNum(shownLoad(it), 0)) }
            if ((now.monotony ?: 0.0) > TrainingLoad.HIGH_MONOTONY) {
                AnalysisNote(stringResource(R.string.load_monotony_high, fmtNum(TrainingLoad.HIGH_MONOTONY, 1)), MaterialTheme.colorScheme.error)
            }
            val monotonyPoints = load.mapNotNull { d -> d.monotony?.let { ChartPoint(d.day, it, LocalDate.ofEpochDay(d.day).format(Dates.ISO)) } }
            if (monotonyPoints.isNotEmpty()) {
                ReadinessGraph(stringResource(R.string.load_monotony), "analysis:monotony", monotonyPoints, { fmtNum(it, 2) }, goal = TrainingLoad.HIGH_MONOTONY, better = noBetter)
            }
            AnalysisNote(stringResource(R.string.load_note, TrainingLoad.ACUTE_DAYS, TrainingLoad.CHRONIC_DAYS))
        }
        // Internal against external load (#242), once any session has a load or hard minutes in the range.
        val weekCount = (if (range > 0) range / 7 else (todayDay - from + 7) / 7).toInt().coerceIn(4, 52)
        val weeks = remember(snap.setsKey, snap.libraryKey, weekCount, todayDay) { TrainingLoad.weekly(snap, todayDay, weekCount) }
        if (weeks.any { it.sessionLoad > 0 || it.hardMinutes > 0 }) {
            SectionLabel(stringResource(R.string.load_ie_title), Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm))
            InternalExternalGraph(snap, weeks)
        }
        Spacer(Modifier.padding(bottom = Spacing.xl))
    }

    checkIn?.let { d -> ReadinessSheet(snap, d) { checkIn = null } }
}
