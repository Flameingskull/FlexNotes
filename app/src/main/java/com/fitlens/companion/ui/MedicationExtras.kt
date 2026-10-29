package com.fitlens.companion.ui

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.InjectionSite
import com.fitlens.companion.data.InjectionSites
import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.MedExtras
import com.fitlens.companion.data.MedInfo
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Supply
import com.fitlens.companion.data.SupplyState
import com.fitlens.companion.data.Symptoms
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.design.DayNavigator
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.SectionLabel
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.ListRowWithMenu
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs
import com.fitlens.companion.ui.design.FitAssistChip
import com.fitlens.companion.ui.design.FitFilterChip
import com.fitlens.companion.ui.design.FitTextButton

/**
 * A medication's supply (#209) and injection sites (#210), shown on its Track tab and in the Medications TODAY list.
 * The supply counts down from the last count as values are logged; the body map shows recent sites and the next one.
 */

/** What the supply says in one line: "18 g left · runs out about 28 Oct". */
internal fun supplyLine(res: Resources, s: SupplyState, unit: String): String {
    val left = res.getString(R.string.sup_left, "${fmtNum(s.left)} $unit".trim())
    val out = s.runsOut ?: return left
    return res.getString(R.string.sup_line, left, Dates.day(out))
}

/** The warnings for [s], in words (never colour alone): run out, runs low, expired, expiring. */
internal fun supplyWarnings(res: Resources, s: SupplyState, info: MedInfo): List<String> = buildList {
    when {
        s.left <= 0.0 -> add(res.getString(R.string.sup_empty))
        s.low -> add(res.getString(R.string.sup_low, Dates.day(s.runsOut ?: Dates.today())))
    }
    val expires = info.expires
    if (expires != null && s.expired) add(res.getString(R.string.sup_expired, Dates.day(expires)))
    else if (expires != null && s.expiresSoon) add(res.getString(R.string.sup_expires_soon, Dates.day(expires)))
}

/**
 * The Medications Track tab's SUPPLY alerts (#209): each medication that runs low, has run out or expires soon, under
 * TODAY. A tap opens the medication, where its supply is updated.
 */
internal fun LazyListScope.supplyAlertRows(snap: Snapshot, nav: Nav) {
    val plan = snap.medPlan
    val records = snap.medicationView.records
    val names = snap.medications.map { it.name }.toSet()
    val alerts = plan.info.values
        .filter { it.tracksSupply && it.medication in names }
        .mapNotNull { i -> Supply.of(i, plan, records)?.takeIf { it.needsAttention }?.let { i to it } }
        .sortedBy { it.first.medication.lowercase() }
    if (alerts.isEmpty()) return
    item(key = "supply-label") { SectionTitle(stringResource(R.string.sup_title)) }
    items(alerts.size, key = { "supply-${alerts[it].first.medication}" }) { i ->
        val (info, state) = alerts[i]
        val res = LocalContext.current.resources
        val unit = snap.medications.firstOrNull { it.name == info.medication }?.unit.orEmpty()
        ListRowWithMenu(
            title = info.medication,
            subtitle = (supplyWarnings(res, state, info) + supplyLine(res, state, unit)).joinToString(" · "),
            leading = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            onClick = { nav.push(Screen.Medication(info.medication)) },
            handle = false
        )
    }
}

/**
 * The bottom of a medication's Track tab: SUPPLY (#209), what's left, when it runs out, expiry and Update supply; and
 * for an injected medication, INJECTION SITES (#210), the body map of recent sites, the next one and the latest
 * sited doses ([onOpen] opens one to change its site).
 */
internal fun LazyListScope.medicationExtras(snap: Snapshot, name: String, unit: String, onSupply: () -> Unit, onOpen: (MRecord) -> Unit) {
    val info = snap.medPlan.infoOf(name)
    item(key = "supply-title") { SectionTitle(stringResource(R.string.sup_title)) }
    item(key = "supply") {
        val res = LocalContext.current.resources
        val records = snap.medicationView.records
        val state = remember(snap.bodyKey, name) { Supply.of(info, snap.medPlan, records) }
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (state == null) {
                Text(stringResource(R.string.sup_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    res.getString(R.string.sup_left, "${fmtNum(state.left)} $unit".trim()),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary
                )
                Text(
                    when {
                        state.runsOut != null -> res.getString(R.string.sup_runs_out, Dates.day(state.runsOut))
                        state.scheduled -> res.getString(R.string.sup_lasts)
                        else -> res.getString(R.string.sup_unused)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                supplyWarnings(res, state, info).forEach { WarningLine(it) }
                val expires = info.expires
                if (expires != null && !state.expired && !state.expiresSoon) {
                    Text(res.getString(R.string.sup_expires, Dates.day(expires)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                info.supplyFrom?.let {
                    Text(res.getString(R.string.sup_counted, Dates.day(it.take(10))), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            GlassOutlinedButton(onClick = onSupply, modifier = Modifier.fillMaxWidth().heightIn(min = Spacing.touch)) {
                Text(stringResource(if (state == null) R.string.sup_start else R.string.sup_update))
            }
        }
    }
    if (!info.injected) return
    item(key = "sites-title") { SectionTitle(stringResource(R.string.inj_title)) }
    item(key = "sites") {
        val res = LocalContext.current.resources
        val records = snap.medicationView.records
        val last = remember(snap.bodyKey, name) { InjectionSites.lastUsed(records, name) }
        val next = remember(snap.bodyKey, name) { InjectionSites.suggest(records, name) }
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            InjectionSiteMap(last, next, selected = null, onPick = null)
            Text(res.getString(R.string.inj_next, siteName(res, next)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    val recent = InjectionSites.recent(snap.medicationView.records, name)
    items(recent.size, key = { "site-${recent[it].id}" }) { i ->
        val r = recent[i]
        val res = LocalContext.current.resources
        ListRowWithMenu(
            title = InjectionSite.of(r.site)?.let { siteName(res, it) }.orEmpty(),
            subtitle = "${Dates.day(r.date.take(10))} · ${Dates.time(LocalContext.current, r.time)} · ${fmtNum(r.value)} ${r.unit}".trim(),
            onClick = { onOpen(r) },
            handle = false
        )
    }
}

@Composable
private fun WarningLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}

/**
 * Sets up or updates a medication's supply (#209): what's on hand now (counted down from this moment), a refill to add,
 * the warning days and the expiry date. Stop tracking clears it. Every change has Undo.
 */
@Composable
internal fun SupplySheet(snap: Snapshot, name: String, unit: String, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val info = snap.medPlan.infoOf(name)
    val left = remember(snap.bodyKey, name) { Supply.left(info, snap.medicationView.records) }
    var onHand by remember { mutableStateOf(left?.let { fmtNum(it) } ?: "") }
    var refill by remember { mutableStateOf("") }
    var warn by remember { mutableStateOf(info.warnDays.toString()) }
    var expires by remember { mutableStateOf(info.expires) }
    var picking by remember { mutableStateOf(false) }
    fun num(t: String) = t.trim().replace(',', '.').toDoubleOrNull()
    val hand = num(onHand)
    val add = if (refill.isBlank()) 0.0 else num(refill)
    val warnDays = warn.trim().toIntOrNull()
    val valid = hand != null && hand >= 0 && add != null && add >= 0 && warnDays != null && warnDays in 0..365

    fun undoable(message: Int, write: suspend () -> Unit) {
        val old = info
        AppScope.scope.launch {
            write()
            UiEvents.show(res.getString(message, name), res.getString(R.string.bm_undo)) {
                AppScope.scope.launch { MedExtras.setInfo(old) }
            }
        }
    }

    FitSheet(
        title = stringResource(R.string.sup_sheet_title, name),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.type_save),
        confirmEnabled = valid,
        onConfirm = {
            if (hand != null && add != null && warnDays != null) {
                val updated = info.copy(warnDays = warnDays, expires = expires)
                val total = hand + add
                // A new count only when the amount changed, so values already logged keep counting down.
                val recount = left == null || abs(hand - left) > 1e-9 || add > 0
                undoable(R.string.sup_saved) { if (recount) MedExtras.count(updated, total) else MedExtras.setInfo(updated) }
            }
            onDismiss()
        },
        secondaryLabel = if (info.tracksSupply) stringResource(R.string.sup_stop) else null,
        onSecondary = if (info.tracksSupply) ({
            undoable(R.string.sup_stopped) { MedExtras.setInfo(info.copy(supply = null, supplyFrom = null, expires = null)) }
            onDismiss()
        }) else null
    ) {
        OutlinedTextField(
            value = onHand, onValueChange = { onHand = it },
            label = { Text(stringResource(R.string.sup_on_hand, unit.ifBlank { "-" })) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = refill, onValueChange = { refill = it },
            label = { Text(stringResource(R.string.sup_refill, unit.ifBlank { "-" })) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = warn, onValueChange = { warn = it.filter(Char::isDigit).take(3) },
            label = { Text(stringResource(R.string.sup_warn_days)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassOutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)) {
                Text(expires?.let { stringResource(R.string.sup_expires, Dates.day(it)) } ?: stringResource(R.string.sup_set_expiry))
            }
            if (expires != null) FitTextButton(onClick = { expires = null }) { Text(stringResource(R.string.sup_clear_expiry)) }
        }
        Text(stringResource(R.string.sup_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (picking) PickDateDialog(expires ?: Dates.today(), onDismiss = { picking = false }) { expires = it }
}

// ---------- Injection sites (#210) ----------

/** "Abdomen, upper left", a site's full name. */
internal fun siteName(res: Resources, site: InjectionSite): String = res.getString(
    when (site) {
        InjectionSite.ABDOMEN_UPPER_LEFT -> R.string.inj_abd_ul
        InjectionSite.ABDOMEN_UPPER_RIGHT -> R.string.inj_abd_ur
        InjectionSite.ABDOMEN_LOWER_RIGHT -> R.string.inj_abd_lr
        InjectionSite.ABDOMEN_LOWER_LEFT -> R.string.inj_abd_ll
        InjectionSite.THIGH_LEFT -> R.string.inj_thigh_l
        InjectionSite.THIGH_RIGHT -> R.string.inj_thigh_r
        InjectionSite.ARM_LEFT -> R.string.inj_arm_l
        InjectionSite.ARM_RIGHT -> R.string.inj_arm_r
        InjectionSite.GLUTE_LEFT -> R.string.inj_glute_l
        InjectionSite.GLUTE_RIGHT -> R.string.inj_glute_r
    }
)

/** The short label inside a site's zone on the map. */
private fun siteShort(res: Resources, site: InjectionSite): String = res.getString(
    when (site) {
        InjectionSite.ABDOMEN_UPPER_LEFT -> R.string.inj_short_ul
        InjectionSite.ABDOMEN_UPPER_RIGHT -> R.string.inj_short_ur
        InjectionSite.ABDOMEN_LOWER_RIGHT -> R.string.inj_short_lr
        InjectionSite.ABDOMEN_LOWER_LEFT -> R.string.inj_short_ll
        InjectionSite.THIGH_LEFT, InjectionSite.ARM_LEFT, InjectionSite.GLUTE_LEFT -> R.string.inj_short_left
        InjectionSite.THIGH_RIGHT, InjectionSite.ARM_RIGHT, InjectionSite.GLUTE_RIGHT -> R.string.inj_short_right
    }
)

/** Sites used within these days are shaded, so the next dose goes elsewhere. */
private const val RECENT_DAYS = 7

/**
 * The body map (#210): the front as you look down at yourself (upper arms, the abdomen's four quarters, thighs) and the
 * buttocks below. Each zone shows how long ago it was used, recent ones shaded, the suggested [next] ringed in gold and
 * marked NEXT. With [onPick] each zone is a radio button, [selected] the one chosen.
 */
@Composable
internal fun InjectionSiteMap(
    lastUsed: Map<InjectionSite, String>,
    next: InjectionSite?,
    selected: InjectionSite?,
    onPick: ((InjectionSite) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val res = LocalContext.current.resources
    val today = Dates.epochDay(Dates.today())
    @Composable
    fun zone(site: InjectionSite, m: Modifier) {
        val stamp = lastUsed[site]
        val ago = stamp?.let { (today - Dates.epochDay(it.take(10))).coerceAtLeast(0).toInt() }
        val agoText = when (ago) {
            null -> res.getString(R.string.inj_never)
            0 -> res.getString(R.string.inj_today)
            else -> res.getQuantityString(R.plurals.inj_days_ago, ago, ago)
        }
        val isNext = site == next
        val isSelected = site == selected
        val shape = RoundedCornerShape(10.dp)
        val fill = when {
            isSelected -> Brand.GoldDusk
            ago != null && ago < RECENT_DAYS -> Brand.Wine
            else -> Brand.SurfaceHigh
        }
        val spoken = buildString {
            append(siteName(res, site)); append(", "); append(agoText)
            if (isNext) { append(", "); append(res.getString(R.string.inj_next_badge)) }
        }
        val pick = onPick
        Box(
            m
                .heightIn(min = Spacing.row)
                .clip(shape)
                .background(fill)
                .border(if (isSelected || isNext) 2.dp else 1.dp, if (isSelected || isNext) Brand.Gold else Brand.Hairline, shape)
                .then(
                    if (pick != null) Modifier.selectable(selected = isSelected, role = Role.RadioButton) { pick(site) }.semantics { contentDescription = spoken }
                    else Modifier.semantics { contentDescription = spoken }
                )
                .padding(Spacing.xs),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(siteShort(res, site), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                Text(
                    if (isNext) res.getString(R.string.inj_next_badge) else agoText,
                    style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                    color = if (isNext) Brand.Gold else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    val gap = Spacing.xs
    Column(modifier.fillMaxWidth().then(if (onPick != null) Modifier.selectableGroup() else Modifier), verticalArrangement = Arrangement.spacedBy(gap)) {
        Text(stringResource(R.string.inj_front), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.inj_arms), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                zone(InjectionSite.ARM_LEFT, Modifier.fillMaxWidth())
            }
            Column(Modifier.weight(2f), verticalArrangement = Arrangement.spacedBy(gap)) {
                Text(stringResource(R.string.inj_abdomen), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    zone(InjectionSite.ABDOMEN_UPPER_LEFT, Modifier.weight(1f))
                    zone(InjectionSite.ABDOMEN_UPPER_RIGHT, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    zone(InjectionSite.ABDOMEN_LOWER_LEFT, Modifier.weight(1f))
                    zone(InjectionSite.ABDOMEN_LOWER_RIGHT, Modifier.weight(1f))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("", style = MaterialTheme.typography.labelSmall)
                zone(InjectionSite.ARM_RIGHT, Modifier.fillMaxWidth())
            }
        }
        Text(stringResource(R.string.inj_thighs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
            zone(InjectionSite.THIGH_LEFT, Modifier.weight(1f))
            zone(InjectionSite.THIGH_RIGHT, Modifier.weight(1f))
        }
        Text(stringResource(R.string.inj_back), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally)) {
            zone(InjectionSite.GLUTE_LEFT, Modifier.weight(1f))
            zone(InjectionSite.GLUTE_RIGHT, Modifier.weight(1f))
        }
        Text(stringResource(R.string.inj_orientation), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A labelled body map to choose a dose's site, the suggestion chosen to begin with. */
@Composable
internal fun SitePicker(snap: Snapshot, medication: String, selected: InjectionSite?, onPick: (InjectionSite) -> Unit) {
    val records = snap.medicationView.records
    val last = remember(snap.bodyKey, medication) { InjectionSites.lastUsed(records, medication) }
    val next = remember(snap.bodyKey, medication) { InjectionSites.suggest(records, medication) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        com.fitlens.companion.ui.design.SectionLabel(stringResource(R.string.inj_site))
        InjectionSiteMap(last, next, selected, onPick)
    }
}

/** Whether [medication]'s doses record an injection site (#210). */
internal fun Snapshot.isInjected(medication: String): Boolean = showsMedications && medPlan.infoOf(medication).injected

/** Turns site recording on or off for [medication], with Undo. */
internal fun setInjected(res: Resources, snap: Snapshot, medication: String, on: Boolean) {
    val old = snap.medPlan.infoOf(medication)
    AppScope.scope.launch {
        MedExtras.setInfo(old.copy(injected = on))
        UiEvents.show(res.getString(if (on) R.string.inj_on else R.string.inj_off, medication), res.getString(R.string.bm_undo)) {
            AppScope.scope.launch { MedExtras.setInfo(old) }
        }
    }
}

// ---------- Symptoms (#207) ----------

/** Symptoms the sheet offers to add with one tap while they aren't on the list. */
private val SUGGESTED_SYMPTOMS = listOf(
    R.string.sym_nausea, R.string.sym_headache, R.string.sym_fatigue, R.string.sym_appetite, R.string.sym_site_reaction,
    R.string.sym_mood, R.string.sym_sleep
)

/** "Nausea 3/10 · Headache 1/10", the ratings of [date], or null when nothing was rated. */
internal fun symptomLine(res: Resources, snap: Snapshot, date: String): String? =
    Symptoms.ratingsOn(snap.medPlan, date)
        .takeIf { it.isNotEmpty() }
        ?.joinToString(" · ") { (s, r) -> res.getString(R.string.sym_rating, s.name, r.rating) }

/** The Medications Track tab's SYMPTOMS row (#207): today's ratings, opening the sheet to rate any day. */
internal fun LazyListScope.symptomRows(snap: Snapshot, onRate: () -> Unit) {
    item(key = "symptoms-label") { SectionTitle(stringResource(R.string.sym_title)) }
    item(key = "symptoms") {
        val res = LocalContext.current.resources
        val empty = snap.medPlan.symptoms.isEmpty()
        ListRowWithMenu(
            title = if (empty) res.getString(R.string.sym_start)
                else symptomLine(res, snap, Dates.today()) ?: res.getString(R.string.sym_none_today),
            subtitle = res.getString(if (empty) R.string.sym_start_body else R.string.sym_rate_hint),
            onClick = onRate,
            handle = false
        )
    }
}

/**
 * Rates symptoms 0–10 on any day (#207): the day's navigator, then each symptom with its 0–10 choice (tap the chosen
 * number again to clear it), saved at once. The list is the user's own: add a name (or a suggested one), and rename
 * or delete one from its menu, with Undo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SymptomSheet(snap: Snapshot, initialDate: String, onDismiss: () -> Unit) {
    val res = LocalContext.current.resources
    val plan = snap.medPlan
    var date by remember { mutableStateOf(initialDate) }
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<Long?>(null) }
    var renameText by remember { mutableStateOf("") }
    fun shift(days: Long) {
        date = (Dates.parse(date) ?: LocalDate.now()).plusDays(days).format(Dates.ISO)
    }
    fun add(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        AppScope.scope.launch { MedExtras.addSymptom(clean) }
        newName = ""
    }
    FitSheet(title = stringResource(R.string.sym_sheet_title), onDismiss = onDismiss, dismissLabel = stringResource(R.string.sym_done)) {
        DayNavigator(
            date = date,
            onPrevious = { shift(-1) },
            onNext = { shift(1) },
            onPickDate = { date = it },
            onToday = { date = Dates.today() }
        )
        if (plan.symptoms.isEmpty()) {
            Text(stringResource(R.string.sym_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        plan.symptoms.forEach { s ->
            val rating = plan.ratings.firstOrNull { it.symptomId == s.id && it.date == date }
            var menu by remember(s.id) { mutableStateOf(false) }
            Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                if (renaming == s.id) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = renameText, onValueChange = { renameText = it },
                            label = { Text(stringResource(R.string.sym_name)) }, singleLine = true, modifier = Modifier.weight(1f)
                        )
                        FitTextButton(onClick = {
                            val text = renameText
                            AppScope.scope.launch { MedExtras.renameSymptom(s.id, text) }
                            renaming = null
                        }) { Text(stringResource(R.string.type_save)) }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            rating?.let { res.getString(R.string.sym_of_ten, it.rating) } ?: res.getString(R.string.sym_not_rated),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (rating != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = res.getString(R.string.sym_menu, s.name))
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.sym_rename)) }, onClick = {
                                    menu = false
                                    renameText = s.name
                                    renaming = s.id
                                })
                                DropdownMenuItem(text = { Text(stringResource(R.string.sym_delete)) }, onClick = {
                                    menu = false
                                    val kept = plan.ratings.filter { it.symptomId == s.id }
                                    AppScope.scope.launch {
                                        MedExtras.deleteSymptom(s.id)
                                        UiEvents.show(res.getString(R.string.sym_deleted, s.name), res.getString(R.string.bm_undo)) {
                                            AppScope.scope.launch { MedExtras.restoreSymptom(s, kept) }
                                        }
                                    }
                                })
                            }
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.selectableGroup()) {
                    (0..Symptoms.MAX).forEach { n ->
                        val chosen = rating?.rating == n
                        FitFilterChip(
                            selected = chosen,
                            onClick = {
                                val d = date
                                AppScope.scope.launch {
                                    if (chosen) MedExtras.clearRating(s.id, d) else MedExtras.rate(s.id, d, n, rating?.comment)
                                }
                            },
                            label = { Text("$n") },
                            modifier = Modifier.semantics { contentDescription = res.getString(R.string.sym_rate_spoken, s.name, n) }
                        )
                    }
                }
            }
        }
        SectionLabel(stringResource(R.string.sym_add_title))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName, onValueChange = { newName = it },
                label = { Text(stringResource(R.string.sym_name)) }, singleLine = true, modifier = Modifier.weight(1f)
            )
            FitTextButton(onClick = { add(newName) }, enabled = newName.isNotBlank()) { Text(stringResource(R.string.sym_add)) }
        }
        val have = plan.symptoms.map { it.name.lowercase() }.toSet()
        val offered = SUGGESTED_SYMPTOMS.map { res.getString(it) }.filter { it.lowercase() !in have }
        if (offered.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                offered.forEach { n -> FitAssistChip(onClick = { add(n) }, label = { Text("+ $n") }) }
            }
        }
        Text(stringResource(R.string.sym_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
