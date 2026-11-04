package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher

/**
 * Proves [GraphRangeEmpty] shows the right text and the [onShowAll] button works, and that [FullScreenChart]
 * resets its zoom when the range changes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class GraphRangeEmptyTest : FunctionTest() {

    @Test
    fun theEmptyRangeNamesTheLastLoggedDayAndOffersAllTime() {
        var shownAll = 0
        openScreen(LongHistoryFixture.snapshot()) { GraphRangeEmpty("2024-06-24") { shownAll++ } }
        assertShows(app.getString(R.string.ch_range_empty_title))
        assertShows(app.getString(R.string.ch_range_empty_body, Dates.day("2024-06-24")))
        tap(app.getString(R.string.ch_range_show_all))
        assertEquals(1, shownAll)
    }

    @Test
    fun withNoKnownDayItFallsBackToNoData() {
        openScreen(LongHistoryFixture.snapshot()) { GraphRangeEmpty(null) {} }
        assertShows(app.getString(R.string.ch_no_data))
        assertShows(app.getString(R.string.ch_range_show_all))
    }

    @Test
    fun fullScreenKeepsItsZoomUntilTheRangeChanges() {
        var seen = ChartViewport()
        var key by mutableIntStateOf(0)
        openScreen(LongHistoryFixture.snapshot()) {
            FullScreenChart("Zoom test", onDismiss = {}, resetKey = key) { vp, _, _ -> seen = vp }
        }
        val label = app.getString(R.string.ch_zoom_in)
        val zoomable = SemanticsMatcher("has the zoom in action") { n ->
            n.config.getOrNull(SemanticsActions.CustomActions)?.any { it.label == label } == true
        }
        val zoomIn = compose.onNode(zoomable).fetchSemanticsNode().config[SemanticsActions.CustomActions].first { it.label == label }
        assertTrue(seen.isFull)
        compose.runOnUiThread { zoomIn.action() }
        compose.waitForIdle()
        assertFalse("the chart is zoomed in", seen.isFull)
        compose.runOnIdle { key = 1 }
        compose.waitForIdle()
        assertTrue("a new range starts from the whole chart", seen.isFull)
    }
}
