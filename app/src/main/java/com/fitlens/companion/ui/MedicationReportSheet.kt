package com.fitlens.companion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.fitlens.companion.R
import com.fitlens.companion.data.CsvExport
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.ImportSummary
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.report.MedicationReport
import com.fitlens.companion.report.MedicationReportOptions
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The Medications report for a doctor (#205), from the Medications screen's ⋮: the date range (the last 90 days by
 * default), which medications, an optional patient name, what to include, and light or dark pages. Save or share the
 * PDF (with the symptoms rated, #223), or the same dose log as CSV. Only medication data goes in: no workouts, body values or photos.
 */
@Composable
internal fun MedicationReportSheet(snap: Snapshot, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val res = ctx.resources
    val all = remember(snap.bodyKey) { snap.medications.map { it.name } }
    var from by rememberSaveable { mutableStateOf(Dates.parse(Dates.today())!!.minusDays(89).format(Dates.ISO)) }
    var to by rememberSaveable { mutableStateOf(Dates.today()) }
    var chosen by remember { mutableStateOf(all.toSet()) }
    var patient by rememberSaveable { mutableStateOf("") }
    var schedules by rememberSaveable { mutableStateOf(true) }
    var adherence by rememberSaveable { mutableStateOf(true) }
    var doseLog by rememberSaveable { mutableStateOf(true) }
    var comments by rememberSaveable { mutableStateOf(true) }
    var graphs by rememberSaveable { mutableStateOf(true) }
    var symptoms by rememberSaveable { mutableStateOf(true) }
    var dark by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Int?>(null) }

    fun options() = MedicationReportOptions(
        from = minOf(from, to), to = maxOf(from, to), names = chosen, dark = dark, patient = patient,
        schedules = schedules, adherence = adherence, doseLog = doseLog, comments = comments, graphs = graphs,
        symptoms = symptoms
    )
    fun name(ext: String) = "FlexNotes_Medications_${options().from}_to_${options().to}.$ext"
    suspend fun csv(): String = withContext(Dispatchers.Default) {
        val o = options()
        CsvExport.medications(Store.snapshot.value ?: snap, o.from, o.to, o.names)
    }

    // The sheet stays open until the file picker answers: its launchers belong to it, so closing first would lose the result.
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val o = options()
        onDismiss()
        if (uri != null) runBusy(res.getString(R.string.mdr_busy)) {
            val s = Store.snapshot.value ?: snap
            val pages = ctx.contentResolver.openOutputStream(uri, "wt")?.use { os -> MedicationReport.create(res, s, o, os) }
                ?: return@runBusy ImportSummary(res.getString(R.string.backup_pdf_failed), false)
            ImportSummary(res.getQuantityString(R.plurals.backup_pdf_saved, pages, pages), true)
        }
    }
    val saveCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(CsvExport.MIME)) { uri ->
        onDismiss()
        if (uri != null) runBusy(res.getString(R.string.mdr_busy)) {
            val text = csv()
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            } ?: return@runBusy ImportSummary(res.getString(R.string.mdr_failed), false)
            ImportSummary(res.getString(R.string.mdr_saved), true)
        }
    }

    /** Writes [ext] into the share cache (one medications file at a time) and opens the share sheet. */
    fun share(ext: String, mime: String) {
        val o = options()
        AppScope.scope.launch {
            UiEvents.busy.value = res.getString(R.string.mdr_busy)
            val file = try {
                val s = Store.snapshot.value ?: snap
                withContext(Dispatchers.IO) {
                    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                    dir.listFiles()?.filter { it.name.startsWith("FlexNotes_Medications_") }?.forEach { it.delete() }
                    val f = File(dir, name(ext))
                    if (ext == "pdf") f.outputStream().use { os -> MedicationReport.create(res, s, o, os) }
                    else f.writeText(CsvExport.medications(s, o.from, o.to, o.names), Charsets.UTF_8)
                    f
                }
            } catch (e: Exception) {
                UiEvents.show(res.getString(R.string.mdr_failed), ResultLevel.Failure)
                null
            } finally {
                UiEvents.busy.value = null
            }
            if (file != null) shareFile(ctx, file, mime)
        }
    }

    FitSheet(
        title = stringResource(R.string.mdr_sheet_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.mdr_save_pdf),
        confirmEnabled = chosen.isNotEmpty(),
        onConfirm = { savePdf.launch(name("pdf")) },
        secondaryLabel = stringResource(R.string.mdr_share_pdf),
        onSecondary = { if (chosen.isNotEmpty()) { share("pdf", "application/pdf"); onDismiss() } }
    ) {
        Text(stringResource(R.string.mdr_sheet_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel(stringResource(R.string.mdr_dates))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GlassOutlinedButton(onClick = { picking = 0 }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.mdr_from, Dates.medium(from)))
            }
            GlassOutlinedButton(onClick = { picking = 1 }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.mdr_to, Dates.medium(to)))
            }
        }
        SectionLabel(stringResource(R.string.md_title))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            all.forEach { n ->
                FilterChip(selected = n in chosen, onClick = { chosen = if (n in chosen) chosen - n else chosen + n }, label = { Text(n) })
            }
        }
        OutlinedTextField(
            value = patient, onValueChange = { patient = it },
            label = { Text(stringResource(R.string.mdr_patient_label)) }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        SectionLabel(stringResource(R.string.mdr_include))
        ToggleRow(stringResource(R.string.mdr_current), schedules, inset = Spacing.xxs) { schedules = it }
        ToggleRow(stringResource(R.string.mdr_adherence), adherence, inset = Spacing.xxs) { adherence = it }
        ToggleRow(stringResource(R.string.mdr_graphs), graphs, inset = Spacing.xxs) { graphs = it }
        if (snap.medPlan.symptoms.isNotEmpty()) {
            ToggleRow(stringResource(R.string.mdr_symptoms), symptoms, inset = Spacing.xxs) { symptoms = it }
        }
        ToggleRow(stringResource(R.string.mdr_dose_log), doseLog, inset = Spacing.xxs) { doseLog = it }
        ToggleRow(stringResource(R.string.mdr_comments), comments, inset = Spacing.xxs) { comments = it }
        ToggleRow(stringResource(R.string.mdr_dark), dark, inset = Spacing.xxs) { dark = it }
        SectionLabel(stringResource(R.string.mdr_csv))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GlassOutlinedButton(
                onClick = { saveCsv.launch(name("csv")) }, enabled = chosen.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)
            ) { Text(stringResource(R.string.mdr_save_csv)) }
            GlassOutlinedButton(
                onClick = { share("csv", CsvExport.MIME); onDismiss() }, enabled = chosen.isNotEmpty(),
                modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)
            ) { Text(stringResource(R.string.mdr_share_csv)) }
        }
    }
    picking?.let { which ->
        PickDateDialog(if (which == 0) from else to, onDismiss = { picking = null }) { d -> if (which == 0) from = d else to = d }
    }
}
