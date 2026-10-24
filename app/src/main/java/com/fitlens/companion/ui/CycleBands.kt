package com.fitlens.companion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.fitlens.companion.R
import com.fitlens.companion.data.Cycle
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import java.time.LocalDate

/** Each phase's shade (#222), from the chart palette: apart from each other and from the red line over gold. */
private fun Cycle.Phase.color(): Color = when (this) {
    Cycle.Phase.PERIOD -> ChartHues.Pink
    Cycle.Phase.FOLLICULAR -> ChartHues.Sky
    Cycle.Phase.OVULATION -> ChartHues.Mint
    Cycle.Phase.LUTEAL -> ChartHues.Blue
}

private fun Cycle.Phase.label(): Int = when (this) {
    Cycle.Phase.PERIOD -> R.string.cy_period
    Cycle.Phase.FOLLICULAR -> R.string.cy_follicular
    Cycle.Phase.OVULATION -> R.string.cy_ovulation
    Cycle.Phase.LUTEAL -> R.string.cy_luteal
}

/**
 * The menstrual cycle's phases as bands for a body or exercise graph (#222), worked out from the Period values (read
 * from Health Connect or typed). None when there are no Period values or the user left them off graphs.
 */
@Composable
internal fun rememberCycleBands(snap: Snapshot): List<ChartBand> {
    val prefs by Settings.portable.collectAsState()
    val res = LocalContext.current.resources
    val today = LocalDate.now()
    return remember(snap.bodyKey, prefs.cycleBandsOff, today) {
        if (prefs.cycleBandsOff) emptyList()
        else Cycle.phases(Cycle.periodDays(snap.records), today).map { s ->
            ChartBand(s.from.toEpochDay(), s.to.toEpochDay(), s.phase.color(), res.getString(s.phase.label()))
        }
    }
}
