package com.fitlens.companion.ui

import com.fitlens.companion.ui.design.DropdownPill
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.TopBarAction
import com.fitlens.companion.ui.design.FitTabRow
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.data.Symptoms
import com.fitlens.companion.data.Store
import com.fitlens.companion.ui.design.rememberInPlaceOrder
import com.fitlens.companion.ui.design.ReorderToggle
import com.fitlens.companion.ui.design.DragHandle
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.background
import androidx.compose.runtime.key
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import com.fitlens.companion.ui.design.SectionLabel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.MeasurementGoals
import com.fitlens.companion.data.MeasurementDef
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.data.fmtSigned
import com.fitlens.companion.ui.design.SetCell
import com.fitlens.companion.ui.design.SetRow as SetRowView
import com.fitlens.companion.ui.design.FitCard
import com.fitlens.companion.ui.design.FitTextButton

val RANGES = listOf("1M" to 30L, "3M" to 91L, "6M" to 182L, "1Y" to 365L, "All" to 0L)

/** Points for a date range; range 0 = all. The range is anchored on the latest data point. */
fun <T> inRange(items: List<T>, range: Long, dateOf: (T) -> String): List<T> {
    if (range <= 0 || items.isEmpty()) return items
    val end = Dates.epochDay(dateOf(items.last()))
    return items.filter { Dates.epochDay(dateOf(it)) >= end - range }
}

/**
 * The Body Tracker, laid out as FitNotes's (its screenshots of 2026-09-23, #144): one screen with TRACK, HISTORY and
 * GRAPH tabs across every measurement. Track lists each enabled measurement with how long ago it was last logged and
 * its latest value and change ("Tap to record a value" when there's none); History lists every value, newest day
 * first, filtered to one measurement or All; Graph shows one measurement, chosen from a dropdown. Tapping a measurement
 * opens it ([BodyMeasurementScreen]).
 *
 * Everything is done here (#167, #169): + logs any measurement in a sheet, a History value opens in its sheet to edit,
 * move or delete (a long press deletes with Undo), and the pencil switches the tracker to its edit mode
 * ([MeasurementsEditor]: tracking, units, goals, custom measurements), so there's no separate Measurements screen.
 * [startEditing] opens it in edit mode. The Reorder toggle shows drag handles on TRACK, saved once per drag (#170).
 */
@Composable
fun BodyScreen(snap: Snapshot, nav: Nav, startEditing: Boolean = false) = BodyTracker(snap, nav, startEditing, medications = false)

/**
 * The Body tracker's layout for either half of the split (#199, #200): [snap] is the body area, or the
 * [Snapshot.medicationView] for the Medications screen, so the same tabs, panes and sheets serve both and each shows
 * only its own measurements.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BodyTracker(snap: Snapshot, nav: Nav, startEditing: Boolean, medications: Boolean) {
    val res = LocalContext.current.resources
    // Every enabled measurement, logged or not, as FitNotes lists them; lab markers after the rest (#211).
    val measurements = snap.allMeasurements.filter { it.enabled }.sortedBy { it.lab }
    var reordering by rememberSaveable { mutableStateOf(false) }
    val names = measurements.map { it.name }
    val dragOrder = rememberInPlaceOrder(names)
    var editing by rememberSaveable { mutableStateOf(startEditing) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    // The dose due today whose sheet is open (#202), by dose id and due time.
    var openDose by rememberSaveable { mutableStateOf<String?>(null) }
    // The doctor's report sheet (#205), and the symptom ratings sheet (#207).
    var reporting by rememberSaveable { mutableStateOf(false) }
    var ratingSymptoms by rememberSaveable { mutableStateOf(false) }
    var emergencyCard by rememberSaveable { mutableStateOf(false) }
    // Medications has five tabs (#227): Today, Schedules, Medications, History, Graph. It opens on Today when a dose is
    // due today (so a reminder's tap lands there), otherwise on the medication list.
    val listPage = if (medications) MED_LIST_PAGE else 0
    val pager = rememberPagerState(
        // Today first when a dose is due or something is taken as needed (#233), else the list.
        initialPage = if (
            medications && com.fitlens.companion.data.MedSchedules.dueOn(snap.medPlan, Dates.today()).isEmpty() &&
            snap.medPlan.info.values.none { it.asNeeded }
        ) MED_LIST_PAGE else 0,
        pageCount = { if (medications) 5 else 3 }
    )
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = stringResource(
                when {
                    medications && editing -> R.string.md_edit_title
                    medications -> R.string.md_title
                    editing -> R.string.body_measurements
                    else -> R.string.body_title
                }
            ),
            onBack = if (editing) ({ editing = false }) else LocalNavBack.current,
            actions = if (editing) {
                listOf(TopBarAction(Icons.Filled.Check, stringResource(if (medications) R.string.md_edit_done else R.string.body_edit_done)) { editing = false })
            } else {
                listOf(
                    TopBarAction(Icons.Filled.Add, stringResource(if (medications) R.string.md_log else R.string.day_log_value)) { adding = true },
                    TopBarAction(Icons.Filled.Edit, stringResource(if (medications) R.string.md_edit else R.string.body_edit_measurements)) { editing = true }
                )
            },
            overflow = when {
                editing -> emptyList()
                medications -> listOf(
                    MenuAction(stringResource(R.string.mdr_menu), enabled = measurements.isNotEmpty()) { reporting = true },
                    MenuAction(stringResource(R.string.ec_title)) { emergencyCard = true },
                    MenuAction(stringResource(R.string.settings_page_meds)) { nav.push(Screen.SettingsPage(SettingsSection.Medications)) }
                )
                else -> listOf(MenuAction(stringResource(R.string.tdee_title)) { nav.push(Screen.Tdee) })
            },
            trailing = {
                if (!editing && measurements.size > 1 && pager.currentPage == listPage) ReorderToggle(reordering) { reordering = it }
            }
        )
        if (editing) {
            MeasurementsEditor(snap, medications)
        } else if (measurements.isEmpty() && medications) {
            EmptyState(stringResource(R.string.md_empty_title), stringResource(R.string.md_empty_body)) {
                Row {
                    FitTextButton(onClick = { editing = true }) { Text(stringResource(R.string.md_add)) }
                    FitTextButton(onClick = { nav.push(Screen.MedSchedule(0L)) }) { Text(stringResource(R.string.md_new_schedule)) }
                }
            }
        } else if (measurements.isEmpty()) {
            EmptyState(stringResource(R.string.body_empty_title), stringResource(R.string.body_empty_body)) {
                Row {
                    FitTextButton(onClick = { editing = true }) { Text(stringResource(R.string.body_measurements)) }
                    FitTextButton(onClick = { nav.push(Screen.SettingsPage(SettingsSection.Import)) }) { Text(stringResource(R.string.body_import)) }
                }
            }
        } else {
            FitTabRow(
                titles = if (medications) medicationTabs() else bodyTabs(),
                selected = pager.currentPage,
                onSelect = { i -> scope.launch { pager.animateScrollToPage(i) } }
            )
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { pageIndex ->
                // The Body tracker's Track / History / Graph are Medications' pages 2–4.
                val page = if (medications) pageIndex - MED_LIST_PAGE else pageIndex
                when {
                    pageIndex == 0 && medications -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        medicationTodayTab(
                            snap, nav,
                            onSchedules = { scope.launch { pager.animateScrollToPage(1) } },
                            onSymptoms = { ratingSymptoms = true }
                        ) { d -> openDose = "${d.dose.id}|${d.due}" }
                    }
                    pageIndex == 1 && medications -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        medicationSchedulesTab(snap, nav)
                    }
                    page == 0 -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                        val order = dragOrder.of(names)
                        val byName = measurements.associateBy { it.name }
                        fun save() = dragOrder.end(names) { final ->
                            val units = measurements.associate { it.name to it.unit }
                            AppScope.scope.launch { Store.reorderMeasurements(final, units) }
                        }
                        val firstLab = order.firstOrNull { byName[it]?.lab == true }
                        itemsIndexed(order, key = { _, n -> n }) { i, n ->
                            val m = byName[n] ?: return@itemsIndexed
                            // Lab markers under their own heading (#211).
                            if (n == firstLab && !medications) {
                                SectionLabel(stringResource(R.string.lab_section), Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md))
                            }
                            MeasurementListRow(
                                snap, m,
                                handle = reordering,
                                onMoveUp = if (i > 0) ({ dragOrder.step(names, n, -1) }) else null,
                                onMoveDown = if (i < order.lastIndex) ({ dragOrder.step(names, n, 1) }) else null,
                                onMoveEnd = { save() }
                            ) { nav.push(if (medications) Screen.Medication(m.name) else Screen.BodyMeasurement(m.name)) }
                            HorizontalDivider(color = Brand.Hairline)
                        }
                    }
                    page == 1 -> BodyHistoryAll(snap, measurements, onDelete = { deleteBodyValue(res, it) }) { r -> open = r.id }
                    else -> {
                        var graphOf by rememberSaveable { mutableStateOf(measurements.first().name) }
                        val names = measurements.map { it.name }
                        val shown = graphOf.takeIf { it in names } ?: names.first()
                        Column {
                            // FitNotes's "GRAPH: Bodyweight" picker over the graph.
                            DropdownPill(stringResource(R.string.ex_graph), names, names.indexOf(shown), Modifier.padding(horizontal = 4.dp)) { graphOf = names[it] }
                            key(shown) { BodyGraphPane(snap, nav, shown) }
                        }
                    }
                }
            }
        }
    }
    // Back leaves edit mode first.
    androidx.activity.compose.BackHandler(enabled = editing) { editing = false }
    if (adding) MeasurementEntrySheet(snap, Dates.today()) { adding = false }
    if (reporting) MedicationReportSheet(snap, onDismiss = { reporting = false })
    if (emergencyCard) EmergencyCardSheet(snap, onDismiss = { emergencyCard = false })
    if (ratingSymptoms) SymptomSheet(snap, Dates.today()) { ratingSymptoms = false }
    openDose?.let { key ->
        com.fitlens.companion.data.MedSchedules.dueOn(snap.medPlan, Dates.today()).firstOrNull { "${it.dose.id}|${it.due}" == key }
    }?.let { d -> DueDoseSheet(snap, d, onDismiss = { openDose = null }) }
    open?.let { id -> snap.records.firstOrNull { it.id == id } }?.let { r ->
        MeasurementEntrySheet(snap, r.date, existing = r) { open = null }
    }
}

/** Medications' list tab (#227): after Today and Schedules, before History and Graph. */
private const val MED_LIST_PAGE = 2

/** Medications' five tabs (#227): Today, Schedules, Medications, History, Graph. */
@Composable
private fun medicationTabs(): List<String> = listOf(
    stringResource(R.string.md_today), stringResource(R.string.md_schedules), stringResource(R.string.md_title),
    stringResource(R.string.body_tab_history), stringResource(R.string.ex_graph)
)

/** The Track, History and Graph tabs, as the body tracker and each measurement name them. */
@Composable
internal fun bodyTabs(): List<String> =
    listOf(stringResource(R.string.body_tab_track), stringResource(R.string.body_tab_history), stringResource(R.string.ex_graph))

/** How long ago a value was logged, as FitNotes says it: "4 hours ago", "5 days ago", "1 month ago", "2 years ago". */
internal fun agoText(res: Resources, date: String, time: String): String {
    val at = runCatching { java.time.LocalDateTime.parse(date.take(10) + "T" + time.take(5).ifBlank { "12:00" }) }.getOrNull()
        ?: return Dates.day(date)
    val now = java.time.LocalDateTime.now()
    val mins = java.time.Duration.between(at, now).toMinutes().coerceAtLeast(0)
    fun n(id: Int, v: Long) = res.getQuantityString(id, v.toInt(), v.toInt())
    return when {
        mins < 1 -> res.getString(R.string.body_just_now)
        mins < 60 -> n(R.plurals.body_minutes_ago, mins)
        mins < 60 * 24 -> n(R.plurals.body_hours_ago, mins / 60)
        mins < 60 * 24 * 30 -> n(R.plurals.body_days_ago, mins / (60 * 24))
        mins < 60 * 24 * 365 -> n(R.plurals.body_months_ago, mins / (60 * 24 * 30))
        else -> n(R.plurals.body_years_ago, mins / (60 * 24 * 365))
    }
}

/**
 * One measurement on the Track tab, as FitNotes lists it: the name in bold with how long ago it was logged (or "Tap
 * to record a value"), and on the right the latest value with its unit, the change since the value before under it.
 */
@Composable
private fun MeasurementListRow(
    snap: Snapshot,
    m: MeasurementDef,
    handle: Boolean = false,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    onMoveEnd: (() -> Unit)? = null,
    onOpen: () -> Unit
) {
    val res = LocalContext.current.resources
    val recs = snap.recordsByName[m.name].orEmpty()
    val last = recs.lastOrNull()
    val prev = recs.getOrNull(recs.size - 2)
    val unit = m.unit.ifBlank { last?.unit.orEmpty() }
    val change = if (last != null && prev != null) changeText(res, prev, last) else null
    val flag = last?.let { flagText(res, m, it.value) }
    val spoken = m.name + ", " + (last?.let { res.getString(R.string.body_value_on, "${fmtNum(it.value)} $unit".trim(), Dates.day(it.date)) }
        ?: res.getString(R.string.body_nothing_logged)) + (flag?.let { ", $it" } ?: "") + (change?.let { ", $it" } ?: "")
    // TalkBack moves a measurement whether or not the handles show (#170); each move is saved at once.
    val moves = listOfNotNull(
        onMoveUp?.let { f -> CustomAccessibilityAction(res.getString(R.string.cmn_move_up)) { f(); onMoveEnd?.invoke(); true } },
        onMoveDown?.let { f -> CustomAccessibilityAction(res.getString(R.string.cmn_move_down)) { f(); onMoveEnd?.invoke(); true } }
    )
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clickable(onClickLabel = res.getString(R.string.day_open_named, m.name), onClick = onOpen)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                if (moves.isNotEmpty()) customActions = moves
            }
            .padding(start = Spacing.lg, end = if (handle) Spacing.xs else Spacing.lg, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(m.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                last?.let { agoText(res, it.date, it.time) } ?: stringResource(R.string.body_tap_to_record),
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (last != null) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = Spacing.md)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmtNum(last.value), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
                    if (unit.isNotBlank()) Text(" $unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                }
                if (change != null && prev != null) {
                    Text(change, style = MaterialTheme.typography.bodySmall, color = changeColour(m, prev.value, last.value), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Outside its reference range (#211).
                RangeFlag(m, last.value)
            }
        }
        if (handle && (onMoveUp != null || onMoveDown != null)) DragHandle(m.name, 72.dp, onMoveUp, onMoveDown, onMoveEnd)
    }
}

/**
 * The History tab, as FitNotes's (#144): a "History" filter (All, or one measurement), then every value newest first
 * under a band for its day ("Wednesday, September 23"), each with its name and time on the left and its value and
 * change on the right. Tapping a value opens it in its sheet ([onOpen], #169); a long press deletes it ([onDelete]).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BodyHistoryAll(
    snap: Snapshot,
    measurements: List<MeasurementDef>,
    onDelete: (MRecord) -> Unit,
    onOpen: (MRecord) -> Unit
) {
    val res = LocalContext.current.resources
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    val names = measurements.map { it.name }
    val all = remember(snap.bodyKey, filter) {
        names.filter { filter == null || it == filter }.flatMap { snap.recordsByName[it].orEmpty() }
            .sortedWith(compareByDescending<MRecord> { it.date.take(10) }.thenByDescending { it.time })
    }
    val byDay = remember(all) { all.groupBy { it.date.take(10) } }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "filter") {
            DropdownPill(stringResource(R.string.body_tab_history), listOf(stringResource(R.string.an_span_all)) + names, (filter?.let { names.indexOf(it) + 1 } ?: 0), Modifier.padding(horizontal = 4.dp)) { i ->
                filter = if (i == 0) null else names[i - 1]
            }
        }
        if (all.isEmpty()) {
            item(key = "none") {
                Text(stringResource(R.string.body_nothing_yet), Modifier.padding(Spacing.lg), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        byDay.forEach { (day, recs) ->
            item(key = "d$day") {
                // FitNotes's day band: the date in normal case on a slightly lighter strip.
                Text(
                    Dates.parse(day)?.format(java.time.format.DateTimeFormatter.ofPattern(
                        if (Dates.parse(day)?.year == java.time.LocalDate.now().year) "EEEE, MMMM d" else "EEEE, MMMM d, yyyy",
                        java.util.Locale.getDefault()
                    )) ?: day,
                    Modifier.fillMaxWidth().background(Brand.SurfaceHigh).padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    style = MaterialTheme.typography.titleSmall.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
                )
            }
            items(recs, key = { "r${it.id}" }) { r ->
                val history = snap.recordsByName[r.name].orEmpty()
                val prev = history.getOrNull(history.indexOfFirst { it.id == r.id } - 1)
                val def = measurements.firstOrNull { it.name == r.name }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Spacing.row)
                        .combinedClickable(
                            onClickLabel = res.getString(R.string.day_open_named, r.name),
                            onLongClickLabel = res.getString(R.string.day_delete_value),
                            onLongClick = { onDelete(r) }
                        ) { onOpen(r) }
                        .semantics(mergeDescendants = true) {}
                        .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                        if (r.time.isNotBlank()) Text(Dates.time(LocalContext.current, r.time), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(fmtNum(r.value), style = MaterialTheme.typography.titleLarge, maxLines = 1, softWrap = false)
                            if (r.unit.isNotBlank()) Text(" ${r.unit}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (prev != null) {
                            Text(changeText(res, prev, r), style = MaterialTheme.typography.bodySmall, color = changeColour(def, prev.value, r.value), maxLines = 1)
                        }
                        RangeFlag(def, r.value)
                    }
                }
                HorizontalDivider(color = Brand.Hairline)
            }
        }
    }
}

/** A measurement's Graph tab: range, trend and from-zero options, the chart with its goal line and photo days, and stats. */
@Composable
internal fun BodyGraphPane(snap: Snapshot, nav: Nav, selectedName: String) {
    val def = snap.allMeasurements.firstOrNull { it.name == selectedName }
    var rangeIdx by rememberSaveable { mutableIntStateOf(4) }
    var selectedPoint by remember(selectedName, rangeIdx) { mutableStateOf<Int?>(null) }
    var showTrend by rememberSaveable { mutableStateOf(false) }
    var fromZero by rememberSaveable { mutableStateOf(false) }
    var fullScreen by rememberSaveable { mutableStateOf(false) }
    TurnOpensGraph(fullScreen) { fullScreen = it }
    val all = remember(snap.bodyKey, selectedName) { snap.dailySeries(selectedName) }
    val shown = remember(all, rangeIdx) { inRange(all, RANGES[rangeIdx].second) { it.date } }
    // A medication's graph can overlay one symptom's ratings on a 0–10 axis at the right (#207); 0 is none.
    val symptoms = if (snap.showsMedications) snap.medPlan.symptoms else emptyList()
    // A body graph can overlay a medication's daily doses instead (#208), while Medications aren't locked (#214).
    val hideMeds = medicationsHidden()
    val medNames = remember(snap.bodyKey, hideMeds) { if (hideMeds) emptyList() else overlayMedications(snap) }
    val choices = symptoms.map { it.name } + medNames
    var symptomIdx by rememberSaveable(selectedName) { mutableIntStateOf(0) }
    val overlaid = symptoms.getOrNull(symptomIdx - 1)
    val medName = medNames.getOrNull(symptomIdx - 1 - symptoms.size)
    val overlaidMed = remember(snap.bodyKey, medName, hideMeds) { medName?.let { overlaySource(snap, it, hideMeds) } }
    val symptomOverlay = remember(snap.bodyKey, overlaid, overlaidMed, shown) {
        val from = shown.firstOrNull()?.date?.take(10)
        val to = shown.lastOrNull()?.date?.take(10)
        when {
            from == null || to == null -> null
            overlaid != null -> LineSeries(
                overlaid.name,
                Symptoms.series(snap.medPlan, overlaid.id).filter { it.date in from..to }
                    .map { ChartPoint(Dates.epochDay(it.date), it.rating.toDouble(), it.date) }
            )
            overlaidMed != null -> LineSeries(
                overlaidMed.name,
                overlaidMed.daily.filter { it.date.take(10) in from..to }.map { ChartPoint(Dates.epochDay(it.date), it.value, it.date) }
            )
            else -> null
        }
    }
    val overlayUnit = overlaidMed?.unit ?: "/10"
    // Line, bar, area or step, remembered for each measurement (#137).
    val (kind, setKind) = rememberChartKind("body:$selectedName")
    if (all.isEmpty()) {
        EmptyState(stringResource(R.string.body_graph_empty_title), stringResource(R.string.body_graph_empty_body, selectedName))
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            // Range, trend and from-zero in one compact row (#115).
            GraphOptionChips(
                rangeIdx, { rangeIdx = it },
                showTrend, { showTrend = !showTrend },
                fromZero, { fromZero = !fromZero },
                kind = kind, onKind = setKind,
                trailing = { ExpandGraphButton { fullScreen = true } }
            )
        }
        if (choices.isNotEmpty()) {
            item {
                val options = listOf(stringResource(R.string.sym_overlay_none)) + choices
                DropdownPill(stringResource(if (symptoms.isEmpty()) R.string.ex_overlay_title else R.string.sym_overlay), options, symptomIdx.coerceIn(0, options.lastIndex), Modifier.padding(horizontal = 4.dp)) {
                    symptomIdx = it
                }
            }
        }
        item {
            val points = rememberChartData(shown) {
                shown.map { ChartPoint(Dates.epochDay(it.date), it.value, it.date) }
            } ?: emptyList()
            val photoDays = remember(snap.photosKey) { snap.photosByDate.keys.map { Dates.epochDay(it) }.toSet() }
            val unit = def?.unit ?: shown.lastOrNull()?.unit ?: ""
            // A measurement with a goal judges each move by it, as its changes do (changeColour, #198).
            val better: (Double, Double) -> Boolean? = { from, to ->
                def?.let { MeasurementGoals.isImprovement(it.goalType, it.goalValue, from, to) } ?: higherIsBetter(from, to)
            }
            FitChart(
                listOf(LineSeries(selectedName, points)),
                Modifier.padding(horizontal = 8.dp),
                kind = kind,
                better = better,
                photoDays = photoDays,
                goal = if (def != null && def.goalType != 0 && def.goalValue > 0) def.goalValue else null,
                range = def?.takeIf { it.hasRange }?.let { it.refLow to it.refHigh },
                selected = selectedPoint?.let { ChartSelection(0, it) },
                onSelect = { selectedPoint = it.index; ChartHints.tapped() },
                unit = unit,
                showTrend = showTrend,
                yFromZero = fromZero,
                onExpand = { ChartHints.expanded(); fullScreen = true },
                overlay = symptomOverlay,
                overlayUnit = overlayUnit
            )
            ChartHint()
            if (fullScreen) {
                FullScreenChart(
                    selectedName,
                    onDismiss = { fullScreen = false },
                    controls = {
                        GraphOptionChips(
                            rangeIdx, { rangeIdx = it },
                            showTrend, { showTrend = !showTrend },
                            fromZero, { fromZero = !fromZero },
                            kind = kind, onKind = setKind
                        )
                    },
                    valueZoom = kind != ChartKind.BAR,
                    footer = {
                        selectedPoint?.let { points.getOrNull(it) }?.let { p ->
                            Text(
                                stringResource(R.string.an_label_value, Dates.day(p.date), "${fmtNum(p.y, 1)} $unit".trim()),
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                ) { vp, h, resetZoom ->
                    FitChart(
                        listOf(LineSeries(selectedName, points)),
                        kind = kind,
                        better = better,
                        height = h,
                        photoDays = photoDays,
                        goal = if (def != null && def.goalType != 0 && def.goalValue > 0) def.goalValue else null,
                        range = def?.takeIf { it.hasRange }?.let { it.refLow to it.refHigh },
                        selected = selectedPoint?.let { ChartSelection(0, it) },
                        onSelect = { selectedPoint = it.index },
                        unit = unit,
                        showTrend = showTrend,
                        yFromZero = fromZero,
                        viewport = vp,
                        onExpand = resetZoom,
                        overlay = symptomOverlay,
                        overlayUnit = overlayUnit
                    )
                }
            }
            Text(
                stringResource(R.string.body_graph_hint),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item {
            val sel = selectedPoint?.let { shown.getOrNull(it) }
            if (sel != null) SelectedPointCard(snap, nav, sel)
        }
        item { StatsBlock(shown, def?.unit ?: shown.lastOrNull()?.unit ?: "") }
    }
}

@Composable
private fun SelectedPointCard(snap: Snapshot, nav: Nav, r: MRecord) {
    val photo = snap.photosByDate[r.date]?.firstOrNull()
    val near = if (photo == null) nearestPhoto(snap, r.date, 7) else null
    FitCard(Modifier.fillMaxWidth().padding(12.dp).clickable { nav.push(Screen.Day(r.date)) }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val shownPhoto = photo ?: near
            if (shownPhoto != null) {
                PhotoThumb(snap, shownPhoto, Modifier.size(width = 90.dp, height = 120.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(Dates.day(r.date), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.an_label_value, r.name, "${fmtNum(r.value)} ${r.unit}".trim()), style = MaterialTheme.typography.headlineSmall)
                if (!r.comment.isNullOrBlank()) Text("“${r.comment}”", style = MaterialTheme.typography.bodySmall)
                if (photo == null && near != null) {
                    Text(stringResource(R.string.body_photo_from, Dates.day(near.date ?: "")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (photo == null) {
                    Text(stringResource(R.string.body_no_photo_week), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.body_open_day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun StatsBlock(list: List<MRecord>, unit: String) {
    if (list.isEmpty()) return
    val first = list.first()
    val last = list.last()
    val min = list.minBy { it.value }
    val max = list.maxBy { it.value }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            LabelValue(stringResource(R.string.body_stat_start, Dates.day(first.date)), "${fmtNum(first.value)} $unit", Modifier.weight(1f))
            LabelValue(stringResource(R.string.body_stat_latest, Dates.day(last.date)), "${fmtNum(last.value)} $unit", Modifier.weight(1f))
            LabelValue(stringResource(R.string.body_stat_change), "${fmtSigned(last.value - first.value)} $unit", Modifier.weight(1f), deltaColour(last.value - first.value, Color.Unspecified))
        }
        Row(Modifier.fillMaxWidth()) {
            LabelValue(stringResource(R.string.body_stat_lowest, Dates.day(min.date)), "${fmtNum(min.value)} $unit", Modifier.weight(1f))
            LabelValue(stringResource(R.string.body_stat_highest, Dates.day(max.date)), "${fmtNum(max.value)} $unit", Modifier.weight(1f))
            LabelValue(stringResource(R.string.body_stat_entries), "${list.size}", Modifier.weight(1f))
        }
        val days = Dates.epochDay(last.date) - Dates.epochDay(first.date)
        if (days >= 14) {
            val perWeek = (last.value - first.value) / days * 7
            Text(stringResource(R.string.body_avg_per_week, "${fmtSigned(perWeek, 2)} $unit".trim(), days.toInt()), style = MaterialTheme.typography.bodyMedium, color = deltaColour(perWeek, Color.Unspecified))
        }
    }
}

/**
 * A measurement's History tab, as the exercise History: each day, newest first, under its date, with the change since
 * the value before and a dot for a day with photos. Tapping a value opens it ([onOpen], a sheet in place, #169); a long
 * press deletes it ([onDelete], with Undo).
 */
@Composable
internal fun BodyHistoryPane(snap: Snapshot, name: String, onDelete: (MRecord) -> Unit, onOpen: (MRecord) -> Unit) {
    val res = LocalContext.current.resources
    val records = snap.recordsByName[name].orEmpty()
    val def = snap.allMeasurements.firstOrNull { it.name == name }
    if (records.isEmpty()) {
        EmptyState(stringResource(R.string.ex_history_empty_title), stringResource(R.string.body_history_empty_body, name))
        return
    }
    val byDate = remember(records) { records.groupBy { it.date.take(10) }.toSortedMap() }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        // A medication's dose changes come first, each with its date (#204).
        if (snap.showsMedications) doseChangeRows(snap, snap.medPlan.changesOf(name))
        byDate.entries.reversed().forEach { (d, l) ->
            item(key = d) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    // FitNotes's day heading, as on the exercise screen's History (#144).
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel(historyDay(d), Modifier.weight(1f))
                        if (snap.photosByDate.containsKey(d)) Dot(LocalChartColors.current.accent)
                    }
                    // A medication's day shows that day's symptom ratings beside its doses (#207).
                    if (snap.showsMedications) {
                        symptomLine(res, snap, d)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    l.forEachIndexed { i, r ->
                        val prev = records.getOrNull(records.indexOf(r) - 1)
                        SetRowView(
                            index = i + 1,
                            summary = "${fmtNum(r.value)} ${r.unit}",
                            cells = valueCells(res, r, def, android.text.format.DateFormat.is24HourFormat(LocalContext.current)),
                            effort = valueSite(res, r),
                            effortSpoken = valueSite(res, r),
                            comment = r.comment,
                            framed = false,
                            showIndex = false,
                            noun = stringResource(R.string.body_value),
                            onClick = { onOpen(r) },
                            onLongClick = { onDelete(r) },
                            onLongClickLabel = stringResource(R.string.day_delete_value)
                        )
                        // The change since the value before, coloured by the goal's direction (#27); the arrow and
                        // sign keep the meaning without colour.
                        if (prev != null) {
                            Text(
                                changeText(res, prev, r),
                                style = MaterialTheme.typography.bodySmall,
                                color = changeColour(def, prev.value, r.value),
                                modifier = Modifier.padding(start = 18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A value and the time it was logged, in the set-row columns the exercise screen uses, flagged High or Low when it's
 * outside [def]'s reference range (#211).
 */
internal fun valueCells(res: Resources, r: MRecord, def: MeasurementDef? = null, is24Hour: Boolean = true): List<SetCell> {
    // In the phone's 12 or 24 hour style (#280).
    val time = if (r.time.isBlank()) "" else Dates.time(r.time, is24Hour)
    return listOfNotNull(
        SetCell(fmtNum(r.value), r.unit, "${fmtNum(r.value)} ${r.unit}"),
        flagText(res, def, r.value)?.let { SetCell(it, spoken = it, unitSlot = false) },
        time.takeIf { it.isNotBlank() }?.let { SetCell(it, spoken = res.getString(R.string.body_at_time, it), unitSlot = false) }
    )
}

/**
 * An injected dose's site (#210), shown on its own line under the values rather than as a column (#238): a long site
 * name in a column would stack that row while its neighbours stayed on one line.
 */
internal fun valueSite(res: Resources, r: MRecord): String? =
    com.fitlens.companion.data.InjectionSite.of(r.site)?.let { siteName(res, it) }
