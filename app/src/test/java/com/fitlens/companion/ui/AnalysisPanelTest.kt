package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.R
import com.fitlens.companion.data.Analysis
import com.fitlens.companion.data.Dates
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class AnalysisPanelTest : FunctionTest() {

    @Test
    fun setsInTheRangeThatTotalZeroSayNothingToTotalNotNothingLogged() {
        openScreen(LongHistoryFixture.snapshot()) { s -> AnalysisScreen(s, nav) }
        val current = totalsGraphName(app.resources, Analysis.Metric.Workouts, Analysis.Period.Week)
        val next = totalsGraphName(app.resources, Analysis.Metric.Duration, Analysis.Period.Week)
        tap(app.getString(R.string.cmn_change_cd, app.getString(R.string.ex_graph), current))
        tap(next)
        compose.waitUntil(5_000) { shows(app.getString(R.string.an_nothing_title)) }
        assertTrue(!shows(app.getString(R.string.ch_range_empty_title)))
    }
}
