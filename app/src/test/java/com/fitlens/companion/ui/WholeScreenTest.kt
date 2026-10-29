package com.fitlens.companion.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import com.fitlens.companion.R
import com.fitlens.companion.data.Db
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Whole-screen checks (#323), the first review area of the human-first review (#251): each screen is drawn from the
 * seeded phone in [ScreenFixture] at the width of a small phone (320dp) and a large one (411dp), at normal and 200%
 * font. Every drawing is run through the layout rules in [LayoutChecks] (the same ones [PhoneSizeTest] holds the
 * design components to) and compared with a golden image, `screen_<name>_<width>dp[_font2].png`, kept in
 * `src/test/screenshots/` and recorded, verified and gated by the same rules as [ScreenshotTest].
 *
 * The window itself is the phone's width (the subclasses set it), so sheets, menus and drawers, which are windows of
 * their own, are laid out at that width too. A sheet or menu is opened by clicking the control that opens it, which
 * also proves the control can be found.
 *
 * Every problem in every variant is collected and reported once at the end of each test, after the goldens are
 * captured, so one run lists everything. A problem already filed as an issue goes in [KNOWN]; any other fails the test.
 *
 * Screens covered so far:
 * - #251: the day log, with its menu, workout comment, workout timer, rest timer and the copy, move and share sheets;
 *   and the exercise screen's Track, History and Graph tabs, set comment, exercise comment and workout drawer.
 * - #252: the exercise library (categories, a category opened, search, the workout switcher, a workout's days), the
 *   exercise editor for a new and an existing exercise and the custom type sheet, the exercise info sheet, the
 *   exercise overview sheet (History and Graph tabs, opened from the day log card's long-press menu), and the
 *   exercise details screen's Records, Stats and Goals tabs. The exercise comment editor with its earlier notes is
 *   the #251 exercise comment test. The details screen is drawn directly: it is pushed from the exercise screen's
 *   trophy and the library's menu, and the harness's Nav is not attached to a back stack.
 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
abstract class WholeScreenChecks(private val width: Int) {

    @get:Rule val compose = createComposeRule()

    private lateinit var app: Application
    private val found = mutableListOf<String>()
    private val nav = Nav()

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase(Db.NAME)
        Store.init(app)
        Settings.init(app)
        runBlocking { Settings.awaitLoaded() }
        AppScope.init(app)
        // The phone's clock stands still, so no golden depends on the real date.
        Dates.clockSource = ScreenFixture.clock
    }

    @After
    fun tearDown() {
        Dates.clockSource = java.time.Clock.systemDefaultZone()
        RestTimer.stop()
        Settings.showWith(PortableSettings())
        Store.db.close()
        app.deleteDatabase(Db.NAME)
    }

    private fun str(id: Int, vararg args: Any): String = if (args.isEmpty()) app.getString(id) else app.getString(id, *args)

    // Built once per test: a screen is given the same snapshot on every recomposition, as the app does.
    private val snap: Snapshot by lazy { ScreenFixture.snapshot() }
    private val timerSnap: Snapshot by lazy { ScreenFixture.snapshot(timerRunning = true) }

    // ---- the day log ------------------------------------------------------------------------------------------

    @Test fun dayLog() = shoot("Day log", "day_log") { DayScreen(snap, nav, DAY) }

    @Test fun dayLogMarkingSetsComplete() =
        shoot("Day log, marking sets complete", "day_log_ticks", PortableSettings().copy(markComplete = true)) {
            DayScreen(snap, nav, DAY)
        }

    @Test fun dayLogTimerRunning() = shoot("Day log, workout timer running", "day_timer_running") {
        DayScreen(timerSnap, nav, DAY)
    }

    @Test fun dayLogRestRunning() {
        startRest()
        shoot("Day log, rest timer running", "day_rest_running") { DayScreen(snap, nav, DAY) }
    }

    @Test fun dayMenu() = shoot("Day log menu", "day_menu", steps = { openMenu() }) { DayScreen(snap, nav, DAY) }

    @Test fun dayMenuTimerRunning() = shoot("Day log menu, timer running", "day_menu_timer", steps = { openMenu() }) {
        DayScreen(timerSnap, nav, DAY)
    }

    @Test fun dayWorkoutComment() = shoot("Workout comment", "day_comment", steps = { tap("Good session overall", substring = true) }) {
        DayScreen(snap, nav, DAY)
    }

    @Test fun dayRestSheet() {
        startRest()
        shoot("Rest timer sheet", "day_rest_sheet", steps = { tap("Rest timer, ", substring = true) }) { DayScreen(snap, nav, DAY) }
    }

    @Test fun dayWorkoutTime() = shoot("Workout time sheet", "day_time_sheet", steps = { fromMenu(R.string.day_workout_time) }) {
        DayScreen(snap, nav, DAY)
    }

    @Test fun dayCopyChoice() = shoot("Copy workout", "day_copy_choice", steps = { fromMenu(R.string.day_copy_workout) }) {
        DayScreen(snap, nav, DAY)
    }

    @Test fun dayCopyToDay() = shoot(
        "Copy this workout", "day_copy_sheet",
        steps = { fromMenu(R.string.day_copy_workout); tap(str(R.string.wk_copy_this)) }
    ) { DayScreen(snap, nav, DAY) }

    @Test fun dayMoveToDay() = shoot(
        "Move this workout", "day_move_sheet",
        steps = { fromMenu(R.string.day_copy_workout); tap(str(R.string.wk_move_this)) }
    ) { DayScreen(snap, nav, DAY) }

    @Test fun dayShare() = shoot("Share workout", "day_share_sheet", steps = { fromMenu(R.string.day_share_workout) }) {
        DayScreen(snap, nav, DAY)
    }

    // ---- the exercise screen (set entry) ----------------------------------------------------------------------

    private val ticks = PortableSettings().copy(markComplete = true)

    @Test fun setEntryTrack() = shoot("Set entry, Track", "set_track", ticks) {
        SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_BENCH)
    }

    @Test fun setEntryTrackLongName() = shoot("Set entry, Track, long exercise name", "set_track_long", ticks) {
        SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_ROMANIAN)
    }

    @Test fun setEntryTrackPounds() = shoot("Set entry, Track, pounds", "set_track_lbs", ticks) {
        SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_RAISE)
    }

    @Test fun setEntryTrackDistance() = shoot("Set entry, Track, distance and time", "set_track_distance", ticks) {
        SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_RUN)
    }

    @Test fun setEntryTrackSession() = shoot("Set entry, Track, Session", "set_track_session", ticks) {
        SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_SESSION)
    }

    @Test fun setEntryHistory() = shoot("Set entry, History", "set_history") {
        SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_BENCH, page = 1)
    }

    @Test fun setEntryGraph() = shoot("Set entry, Graph", "set_graph") {
        SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_BENCH, page = 2)
    }

    @Test fun setComment() = shoot("Set comment box", "set_comment", steps = { tap("Edit comment on", substring = true) }) {
        SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_BENCH)
    }

    @Test fun exerciseComment() = shoot("Exercise comment", "set_exercise_comment", steps = { tap("Elbows tucked in", substring = true) }) {
        SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_BENCH)
    }

    @Test fun workoutDrawer() = shoot("Workout drawer", "set_drawer", steps = { tap(str(R.string.set_drawer_open)) }) {
        SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_BENCH)
    }

    @Test fun setEntryRestRunning() {
        startRest()
        shoot("Set entry, rest timer running", "set_rest_running") { SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_BENCH) }
    }

    // ---- the exercise library and the exercise's own screens (#252) -------------------------------------------

    private val libraryName = ScreenFixture.ROMANIAN
    private val legs = "Legs and posterior chain"

    @Test fun libraryCategories() = shoot("Library, categories", "lib_categories") { ExerciseLibraryScreen(snap, nav, DAY) }

    @Test fun libraryCategoryOpened() = shoot("Library, a category opened", "lib_category", steps = { tap(legs) }) {
        ExerciseLibraryScreen(snap, nav, DAY)
    }

    @Test fun librarySearch() = shoot("Library, search", "lib_search", steps = { typeText("dumbbell") }) {
        ExerciseLibraryScreen(snap, nav, DAY)
    }

    @Test fun libraryWorkoutSwitcher() = shoot("Library, workout switcher", "lib_switcher", steps = { tap(str(R.string.lib_all_exercises)) }) {
        ExerciseLibraryScreen(snap, nav, DAY)
    }

    @Test fun libraryWorkoutDays() =
        shoot("Library, a workout's days", "lib_workout", PortableSettings().copy(lastRoutineId = 1L)) {
            ExerciseLibraryScreen(snap, nav, DAY)
        }

    @Test fun libraryNewExercise() = shoot("New exercise", "lib_new_exercise", steps = { tap(str(R.string.lib_new_exercise)) }) {
        ExerciseLibraryScreen(snap, nav, DAY)
    }

    @Test fun libraryEditExercise() = shoot(
        "Edit exercise, long name", "lib_edit_exercise",
        steps = { tap(legs); tap(str(R.string.lib_options_for, libraryName)); tap(str(R.string.lib_edit)) }
    ) { ExerciseLibraryScreen(snap, nav, DAY) }

    @Test fun libraryCustomType() = shoot(
        "Custom exercise type", "lib_custom_type",
        steps = { tap(str(R.string.lib_new_exercise)); tap(str(R.string.types_new), scroll = true) }
    ) { ExerciseLibraryScreen(snap, nav, DAY) }

    @Test fun exerciseInfo() = shoot("Exercise info", "set_info", steps = { tap(str(R.string.set_exercise_info)) }) {
        SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_ROMANIAN)
    }

    @Test fun exerciseHistoryLongName() = shoot("Exercise History, long name", "set_history_long") {
        SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_ROMANIAN, page = 1)
    }

    @Test fun exerciseGraphPounds() = shoot("Exercise Graph, pounds", "set_graph_lbs") {
        SetEntryScreen(snap, nav, DAY, ScreenFixture.EX_RAISE, page = 2)
    }

    @Test fun exerciseOverview() = shoot(
        "Exercise overview, History", "overview_history",
        steps = { longPress(ScreenFixture.BENCH); tap(str(R.string.day_ex_overview)) }
    ) { DayScreen(snap, nav, DAY) }

    @Test fun exerciseOverviewGraph() = shoot(
        "Exercise overview, Graph", "overview_graph",
        steps = { longPress(ScreenFixture.BENCH); tap(str(R.string.day_ex_overview)); tap(str(R.string.set_tab_graph)) }
    ) { DayScreen(snap, nav, DAY) }

    @Test fun exerciseRecords() = shoot("Exercise Records", "detail_records") {
        ExerciseDetailScreen(snap, nav, ScreenFixture.EX_BENCH, 0)
    }

    @Test fun exerciseStatsLongName() = shoot("Exercise Stats, long name", "detail_stats") {
        ExerciseDetailScreen(snap, nav, ScreenFixture.EX_ROMANIAN, 1)
    }

    @Test fun exerciseGoals() = shoot("Exercise Goals", "detail_goals") {
        ExerciseDetailScreen(snap, nav, ScreenFixture.EX_BENCH, 2)
    }

    @Test fun exerciseGoalsPounds() = shoot("Exercise Goals, pounds", "detail_goals_lbs") {
        ExerciseDetailScreen(snap, nav, ScreenFixture.EX_RAISE, 2)
    }

    // ---- how a screen is drawn and checked --------------------------------------------------------------------

    /**
     * A rest of 1:15 left, paused, so the countdown on screen is the same on every run (a running one changes with
     * the real time). Started before the screen is drawn.
     */
    private fun startRest() {
        RestTimer.start(app, 90)
        RestTimer.pause()
        RestTimer.adjust(-15)
    }

    private fun openMenu() = tap(str(R.string.cmn_more_options))

    private fun fromMenu(item: Int) {
        openMenu()
        tap(str(item))
    }

    /**
     * Clicks the control with this text or description, noting a problem when it can't be found or clicked. With
     * [scroll] the control is first scrolled into view in the sheet or list it sits in.
     */
    private fun tap(label: String, substring: Boolean = false, scroll: Boolean = false) =
        withControl(label, substring, "click") {
            if (scroll) runCatching { performScrollTo() }
            performClick()
        }

    /** A long press on the control with this text or description (a card whose menu opens that way). */
    private fun longPress(label: String, substring: Boolean = false) =
        withControl(label, substring, "press") { performTouchInput { longClick() } }

    /** Types [text] into the screen's text field. */
    private fun typeText(text: String) {
        try {
            compose.onNode(hasSetTextAction()).performTextInput(text)
            compose.waitForIdle()
        } catch (e: Throwable) {
            found += "Could not type \"$text\": ${e.message?.lineSequence()?.firstOrNull()}"
        }
    }

    private fun withControl(label: String, substring: Boolean, verb: String, action: SemanticsNodeInteraction.() -> Unit) {
        try {
            val control = hasClickAction() and (hasText(label, substring, true) or hasContentDescription(label, substring, true))
            val matches = compose.onAllNodes(control)
            if (matches.fetchSemanticsNodes().isEmpty()) {
                found += "Could not find \"$label\" to $verb"
                return
            }
            matches[0].action()
            compose.waitForIdle()
        } catch (e: Throwable) {
            found += "Could not $verb \"$label\": ${e.message?.lineSequence()?.firstOrNull()}"
        }
    }

    /**
     * Draws [content] once at the window's width, then at normal and 200% font: for each, [steps] first (once, at the
     * start), then the layout checks and the golden. Everything found is reported when the test ends.
     */
    private fun shoot(
        title: String,
        name: String,
        preferences: PortableSettings = PortableSettings(),
        steps: () -> Unit = {},
        content: @Composable () -> Unit
    ) {
        GOLDEN_DIR.mkdirs()
        Settings.showWith(preferences)
        var fontScale by mutableStateOf(1f)
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                FlexNotesTheme {
                    Box(Modifier.testTag(TAG).fillMaxSize().background(Brand.Black)) { content() }
                }
            }
        }
        compose.waitForIdle()
        var stepsDone = false
        for ((scale, suffix, percent) in FONTS) {
            compose.runOnIdle { fontScale = scale }
            compose.waitForIdle()
            if (!stepsDone) {
                stepsDone = true
                steps()
            }
            val label = "${width}dp$suffix"
            val where = "$title [$width dp, $percent% font]"
            try {
                found += LayoutChecks.problems(compose, where, TAG, scrollingScreen = true)
            } catch (e: Throwable) {
                found += "$where: the layout checks could not run: $e"
            }
            golden("screen_${name}_$label.png", where)
        }
        report()
    }

    /** Compares the screen with its golden, or records one when there is none (see [ScreenshotTest]). */
    private fun golden(file: String, where: String) {
        val golden = File(GOLDEN_DIR, file)
        val exists = golden.exists()
        // The release build sets SCREENSHOT_STRICT: a screen with no committed golden would otherwise pass unchecked.
        if (!exists && System.getenv("SCREENSHOT_STRICT") == "true") {
            found += "$where: no golden for $file; record it on a PR run and commit it from the screenshots artifact"
            return
        }
        val options = RoborazziOptions(
            taskType = if (exists) RoborazziTaskType.Verify else RoborazziTaskType.Record,
            // Anti-aliasing can move a handful of pixels between runs; a real layout change moves far more.
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f)
        )
        try {
            captureScreenRoboImage(golden.path, roborazziOptions = options)
        } catch (e: Throwable) {
            found += "$where: the screen differs from its golden $file: ${e.message?.lineSequence()?.firstOrNull()}"
        }
    }

    private fun report() {
        val problems = found.distinct()
        val fresh = problems.filter { p -> KNOWN.keys.none { p.startsWith(it) } }
        val known = problems - fresh.toSet()
        val text = buildString {
            append("Whole-screen checks at ${width}dp (#323):\n")
            fresh.forEach { append(it).append('\n') }
            if (known.isNotEmpty()) {
                append("Already filed:\n")
                known.forEach { p -> append(p).append("  (#").append(KNOWN.entries.first { p.startsWith(it.key) }.value).append(")\n") }
            }
        }
        assertTrue(text, fresh.isEmpty())
    }

    private companion object {
        const val TAG = "whole-screen"
        const val DAY = ScreenFixture.DATE
        /** Relative to the module directory, the working directory Gradle runs unit tests in. */
        val GOLDEN_DIR = File("src/test/screenshots")

        /** The font scale, the golden name's suffix and the percentage for the message. */
        val FONTS = listOf(Triple(1f, "", 100), Triple(2f, "_font2", 200))

        /**
         * Problems already filed as issues: the start of the problem line (it begins with the screen, width and font)
         * to the issue number. These are reported but don't fail the build, so a filed finding never blocks a release
         * while any new one still does. Empty until the first run's report is triaged.
         */
        val KNOWN: Map<String, Int> = emptyMap()
    }
}

/** A small phone: 320dp wide, the smallest FlexNotes supports. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w320dp-h1000dp-xhdpi")
class WholeScreen320Test : WholeScreenChecks(320)

/** A large phone: 411dp wide. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h1000dp-xhdpi")
class WholeScreen411Test : WholeScreenChecks(411)
