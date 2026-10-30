@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.fitlens.companion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fitlens.companion.data.BodyFat
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.InjectionSite
import com.fitlens.companion.data.InjectionSites
import com.fitlens.companion.data.MedExtras
import com.fitlens.companion.data.Medications
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.DayNavigator
import com.fitlens.companion.ui.design.FitTabRow
import com.fitlens.companion.ui.design.FitTopBar
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.GoldButton
import com.fitlens.companion.ui.design.MenuAction
import com.fitlens.companion.ui.design.StepperField
import com.fitlens.companion.ui.design.SetRow as SetRowView
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import com.fitlens.companion.ui.design.FitTextButton

private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val HHMMSS: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

/** The value stepper's step, as FitNotes's body tracker steps. */
private const val VALUE_STEP = 0.1

/** An unreadable date steps from today, as before. */
private fun shiftDay(date: String, days: Long): String =
    Dates.plusDays(if (Dates.parse(date) != null) date else LocalDate.now().format(Dates.ISO), days)

/**
 * One body measurement, as in FitNotes's body tracker: TRACK, HISTORY and GRAPH tabs laid out like the exercise screen.
 * Track has the day, a +/- value stepper, the time and a comment, Save / Clear (Update / Delete for a selected value),
 * and the values logged that day. History lists every day newest first; tapping a value opens it in its sheet right
 * there, and a long press deletes it with Undo (#169). Every value can be changed, whatever its source (#166).
 */
@Composable
fun BodyMeasurementScreen(snap: Snapshot, nav: Nav, name: String, page: Int = 0) {
    val res = LocalContext.current.resources
    val def = snap.allMeasurements.firstOrNull { it.name == name }
    val records = snap.recordsByName[name].orEmpty()
    val unit = def?.unit?.takeIf { it.isNotBlank() } ?: records.lastOrNull()?.unit ?: ""
    val label = if (unit.isNotBlank()) "$name ($unit)" else name

    var date by rememberSaveable(name) { mutableStateOf(Dates.today()) }
    var selected by rememberSaveable(name) { mutableStateOf<Long?>(null) }
    var value by rememberSaveable(name) { mutableStateOf("") }
    var timeText by rememberSaveable(name) { mutableStateOf(LocalTime.now().format(HHMM)) }
    var comment by rememberSaveable(name) { mutableStateOf("") }
    // Set once the fields have been filled, so a screen restored after rotation keeps what was typed instead of
    // refilling it from the last value (#315, as Set entry does since #304).
    var filled by rememberSaveable(name) { mutableStateOf(false) }
    val firstFill = remember { booleanArrayOf(true) }
    var editingGoal by rememberSaveable { mutableStateOf(false) }
    var editingMetric by rememberSaveable { mutableStateOf(false) }
    var editingRange by rememberSaveable { mutableStateOf(false) }
    // The History value open in its sheet (#169).
    var historyOpen by rememberSaveable(name) { mutableStateOf<Long?>(null) }
    // Body fat can be worked out from the tape measurements (#153).
    val isBodyFat = BodyFat.isBodyFat(name)
    var calculating by rememberSaveable { mutableStateOf(false) }
    // A medication's supply sheet (#209), and the site of a new injected dose (#210): null means the suggested one.
    var editingSupply by rememberSaveable { mutableStateOf(false) }
    var editingAsNeeded by rememberSaveable { mutableStateOf(false) }
    val injected = snap.isInjected(name)
    val suggestedSite = remember(snap.bodyKey, name) { InjectionSites.suggest(records, name) }
    var site by rememberSaveable(name) { mutableStateOf<InjectionSite?>(null) }

    val dayValues = remember(records, date) { records.filter { it.date.take(10) == date } }
    val chosen = selected?.let { id -> records.firstOrNull { it.id == id } }
    // A new value starts from the latest one on or before the day, as FitNotes fills in last time's value.
    val template = remember(records, date) { records.lastOrNull { it.date.take(10) <= date } ?: records.lastOrNull() }
    // A new Body fat value starts from the figure the latest measurements on or before the day give, when they give
    // one (#194), with the comment the calculator's Use writes. Seeded only when the day or its inputs change, so a
    // recomposition never overwrites what was typed.
    val estimate = if (isBodyFat) rememberBodyFatEstimate(snap, date) else null
    val lengthUnit = Settings.portable.collectAsState().value.lengthUnit
    val prefill = estimate?.percent?.let { it to estimateComment(res, estimate, lengthUnit) }

    LaunchedEffect(selected, date, records, prefill) {
        // The first run after a restore leaves the restored fields alone (#315).
        val restored = firstFill[0] && filled
        firstFill[0] = false
        if (selected != null && chosen == null) {
            selected = null
        } else if (!restored) {
            filled = true
            val source = chosen ?: template
            value = (if (chosen == null) prefill?.first else null)?.let { fmtNum(it, 1) } ?: source?.value?.let { fmtNum(it) } ?: ""
            timeText = chosen?.time?.take(5)?.takeIf { it.isNotBlank() } ?: LocalTime.now().format(HHMM)
            comment = if (chosen != null) chosen.comment.orEmpty() else prefill?.second.orEmpty()
            site = null
        }
    }

    fun save() {
        val v = value.trim().replace(',', '.').toDoubleOrNull()
        if (v == null) {
            UiEvents.show(res.getString(R.string.bm_enter_number))
            return
        }
        // The time typed in, or now when it can't be read.
        val time = (Dates.clock(timeText) ?: LocalTime.now()).format(HHMMSS)
        val c = comment.trim().ifBlank { null }
        val d = date
        val editing = chosen
        val siteKey = if (injected) (site ?: suggestedSite).key else null
        AppScope.scope.launch {
            if (editing == null) {
                Store.addManualRecord(name, unit, d, time, v, c, siteKey)
            } else {
                Store.updateRecord(editing.id, d, time, v, c)
                UiEvents.show(res.getString(R.string.bm_updated))
            }
        }
        selected = null
    }

    fun clear() {
        selected = null
        value = ""
        comment = ""
        timeText = LocalTime.now().format(HHMM)
    }

    val pager = rememberPagerState(initialPage = page.coerceIn(0, 2), pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        FitTopBar(
            title = name,
            subtitle = unit.takeIf { it.isNotBlank() },
            onBack = { nav.pop() },
            // The measurement itself is set up where it's shown (#167): its goal, and for one of your own, its details.
            overflow = listOfNotNull(
                MenuAction(stringResource(R.string.goal_generic), enabled = def != null) { editingGoal = true },
                // A reference range (#211), for body values, vitals and lab markers.
                if (!snap.showsMedications) MenuAction(stringResource(R.string.lab_range_menu), enabled = def != null) { editingRange = true } else null,
                if (def?.custom == true) MenuAction(stringResource(R.string.bm_edit_metric)) { editingMetric = true } else null,
                // Moved where it's shown (#199): the whole history goes with it, so this screen closes.
                if (def != null && (snap.showsMedications || Medications.canBeMedication(name))) {
                    MenuAction(stringResource(if (snap.showsMedications) R.string.md_move_to_body else R.string.md_move_to_meds)) {
                        moveMeasurement(res, def, toMedications = !snap.showsMedications)
                        nav.pop()
                    }
                } else null,
                // Injected medications record each dose's site (#210).
                if (def != null && snap.showsMedications) {
                    MenuAction(stringResource(if (injected) R.string.inj_menu_off else R.string.inj_menu_on)) {
                        setInjected(res, snap, name, !injected)
                    }
                } else null,
                // Taken only when needed (#233), with an optional most a day.
                if (def != null && snap.showsMedications) MenuAction(stringResource(R.string.prn_menu)) { editingAsNeeded = true } else null
            )
        )
        FitTabRow(
            titles = bodyTabs(),
            selected = pager.currentPage,
            onSelect = { i -> scope.launch { pager.animateScrollToPage(i) } }
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), verticalAlignment = androidx.compose.ui.Alignment.Top) { tab ->
            when (tab) {
                1 -> BodyHistoryPane(snap, name, onDelete = { deleteBodyValue(res, it) }) { r -> historyOpen = r.id }
                2 -> BodyGraphPane(snap, nav, name)
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    item {
                        DayNavigator(
                            date = date,
                            onPrevious = { date = shiftDay(date, -1); selected = null },
                            onNext = { date = shiftDay(date, 1); selected = null },
                            onPickDate = { date = it; selected = null },
                            onToday = { date = Dates.today(); selected = null }
                        )
                    }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            StepperField(
                                label = label,
                                value = value,
                                onValue = { value = it },
                                onStep = { dir ->
                                    val now = value.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
                                    value = fmtNum(max(0.0, now + dir * VALUE_STEP), 2)
                                }
                            )
                            if (injected) {
                                // A new value takes the chosen site; a selected one moves to a tapped site at once.
                                val editingValue = chosen
                                SitePicker(
                                    snap, name,
                                    if (editingValue != null) InjectionSite.of(editingValue.site) else site ?: suggestedSite
                                ) { picked ->
                                    if (editingValue == null) site = picked
                                    if (editingValue != null) AppScope.scope.launch { MedExtras.setSite(editingValue.id, picked) }
                                }
                            }
                            if (isBodyFat && chosen == null) {
                                BodyFatPrefillNote(estimate, date)
                                GlassOutlinedButton(onClick = { calculating = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                    Text(stringResource(R.string.bm_calculate))
                                }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TimeField(time = timeText, onTime = { timeText = it }, label = stringResource(R.string.bm_time), modifier = Modifier.weight(1f))
                                OutlinedTextField(
                                    value = comment, onValueChange = { comment = it },
                                    label = { Text(stringResource(R.string.bm_comment)) }, singleLine = true,
                                    modifier = Modifier.weight(2f)
                                )
                            }
                            if (def != null && def.goalType != 0) {
                                Text(goalText(res, def), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            // As in FitNotes: Save and Clear for a new value, Update and Delete for the selected one.
                            if (chosen == null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GoldButton(onClick = { save() }, modifier = Modifier.weight(1f).height(52.dp)) {
                                        Text(stringResource(R.string.type_save), style = MaterialTheme.typography.labelLarge)
                                    }
                                    GlassOutlinedButton(onClick = { clear() }, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.cal_clear)) }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    GoldButton(onClick = { save() }, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.bm_update)) }
                                    GlassOutlinedButton(
                                        onClick = { selected = null; deleteBodyValue(res, chosen) },
                                        modifier = Modifier.weight(1f).height(52.dp)
                                    ) { Text(stringResource(R.string.lib_delete)) }
                                }
                                FitTextButton(onClick = { selected = null }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.bm_new_instead)) }
                            }
                        }
                    }
                    item { HorizontalDivider(Modifier.padding(top = 14.dp)) }
                    if (dayValues.isEmpty()) {
                        item {
                            Text(
                                if (template == null) stringResource(R.string.bm_nothing_for, name) else stringResource(R.string.bm_nothing_day),
                                Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    dayValues.forEachIndexed { i, r ->
                        item(key = "m${r.id}") {
                            SetRowView(
                                index = i + 1,
                                summary = "${fmtNum(r.value)} ${r.unit}",
                                cells = valueCells(res, r, def, android.text.format.DateFormat.is24HourFormat(LocalContext.current)),
                                effort = valueSite(res, r),
                                effortSpoken = valueSite(res, r),
                                comment = r.comment,
                                selected = selected == r.id,
                                noun = stringResource(R.string.body_value),
                                onClick = { selected = if (selected == r.id) null else r.id },
                                onLongClick = { if (selected == r.id) selected = null; deleteBodyValue(res, r) },
                                onLongClickLabel = stringResource(R.string.day_delete_value)
                            )
                        }
                    }
                    // A medication's supply and injection sites (#209, #210).
                    if (snap.showsMedications) {
                        medicationExtras(snap, name, unit, onSupply = { editingSupply = true }) { r -> historyOpen = r.id }
                    }
                }
            }
        }
    }

    historyOpen?.let { id -> records.firstOrNull { it.id == id } }?.let { r ->
        MeasurementEntrySheet(snap, r.date, existing = r) { historyOpen = null }
    }
    if (calculating) {
        BodyFatCalculatorSheet(snap, date, onDismiss = { calculating = false }) { p, c ->
            value = fmtNum(p, 1)
            comment = c
            UiEvents.show(res.getString(R.string.bm_calculated))
        }
    }
    if (editingSupply) SupplySheet(snap, name, unit) { editingSupply = false }
    if (editingAsNeeded) AsNeededSheet(snap, name) { editingAsNeeded = false }
    if (editingGoal && def != null) MeasurementGoalSheet(def) { editingGoal = false }
    if (editingMetric && def != null) CustomMetricEditor(snap, def) { editingMetric = false }
    if (editingRange && def != null) ReferenceRangeSheet(def) { editingRange = false }
}
