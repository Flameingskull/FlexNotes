package com.fitlens.companion.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.EmergencyCard
import com.fitlens.companion.data.EmergencyDetails
import com.fitlens.companion.data.ImportSummary
import com.fitlens.companion.data.Settings
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Store
import com.fitlens.companion.report.EmergencyCardPdf
import com.fitlens.companion.ui.design.FitSheet
import com.fitlens.companion.ui.design.GlassOutlinedButton
import com.fitlens.companion.ui.design.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/**
 * The emergency card (#215), from the Medications screen's ⋮: the name on the card, allergies, conditions and an
 * emergency contact (kept in the settings, so they travel with a backup), over the current medications worked out
 * by [EmergencyCard.current]. Save or share it as a one-page PDF or as an image. Nothing leaves the phone otherwise.
 */
@Composable
internal fun EmergencyCardSheet(snap: Snapshot, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val res = ctx.resources
    val saved = remember { EmergencyDetails.of(Settings.currentPortable()) }
    var name by rememberSaveable { mutableStateOf(saved.name) }
    var allergies by rememberSaveable { mutableStateOf(saved.allergies) }
    var conditions by rememberSaveable { mutableStateOf(saved.conditions) }
    var contact by rememberSaveable { mutableStateOf(saved.contact) }
    var dark by rememberSaveable { mutableStateOf(false) }
    val meds = remember(snap.bodyKey) {
        EmergencyCard.current(snap.medPlan, snap.medicationView.records, snap.medications.map { it.name })
    }

    fun details() = EmergencyDetails(name.trim(), allergies.trim(), conditions.trim(), contact.trim())
    /** Keeps what was typed for next time, then closes. */
    fun close() {
        val d = details()
        if (d != saved) Settings.updatePortable {
            it.copy(
                emergencyName = d.name.ifBlank { null }, emergencyAllergies = d.allergies.ifBlank { null },
                emergencyConditions = d.conditions.ifBlank { null }, emergencyContact = d.contact.ifBlank { null }
            )
        }
        onDismiss()
    }
    fun fileName(ext: String) = "FlexNotes_Emergency_card_${Dates.today()}.$ext"
    suspend fun write(ext: String, os: OutputStream, d: EmergencyDetails, dark: Boolean) {
        val s = Store.snapshot.value ?: snap
        val lines = EmergencyCard.current(s.medPlan, s.medicationView.records, s.medications.map { it.name })
        if (ext == "pdf") EmergencyCardPdf.pdf(res, d, lines, os, dark) else EmergencyCardPdf.png(res, d, lines, os, dark)
    }

    // The sheet stays open until the file picker answers: its launchers belong to it, so closing first would lose the result.
    fun saver(ext: String): (android.net.Uri?) -> Unit = { uri ->
        val d = details()
        val dk = dark
        close()
        if (uri != null) runBusy(res.getString(R.string.ec_busy)) {
            ctx.contentResolver.openOutputStream(uri, "wt")?.use { os -> write(ext, os, d, dk) }
                ?: return@runBusy ImportSummary(res.getString(R.string.ec_failed), false)
            ImportSummary(res.getString(R.string.ec_saved), true)
        }
    }
    val savePdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"), saver("pdf"))
    val savePng = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png"), saver("png"))

    /** Writes the card into the share cache (one card at a time) and opens the share sheet. */
    fun share(ext: String, mime: String) {
        val d = details()
        val dk = dark
        close()
        AppScope.scope.launch {
            UiEvents.busy.value = res.getString(R.string.ec_busy)
            val file = try {
                withContext(Dispatchers.IO) {
                    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
                    dir.listFiles()?.filter { it.name.startsWith("FlexNotes_Emergency_card_") }?.forEach { it.delete() }
                    File(dir, fileName(ext)).also { f -> f.outputStream().use { os -> write(ext, os, d, dk) } }
                }
            } catch (e: Exception) {
                UiEvents.show(res.getString(R.string.ec_failed), ResultLevel.Failure)
                null
            } finally {
                UiEvents.busy.value = null
            }
            if (file != null) shareFile(ctx, file, mime)
        }
    }

    FitSheet(
        title = stringResource(R.string.ec_title),
        onDismiss = { close() },
        confirmLabel = stringResource(R.string.ec_save_pdf),
        onConfirm = { savePdf.launch(fileName("pdf")) },
        secondaryLabel = stringResource(R.string.ec_share_pdf),
        onSecondary = { share("pdf", "application/pdf") }
    ) {
        Text(stringResource(R.string.ec_sheet_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionLabel(stringResource(R.string.ec_current))
        if (meds.isEmpty()) {
            Text(stringResource(R.string.ec_no_medications), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        meds.forEach { m ->
            val detail = if (m.scheduled) m.doses.joinToString(", ") { it.text }
                else m.last?.let { r -> stringResource(R.string.ec_as_needed, "${com.fitlens.companion.data.fmtNum(r.value)} ${r.unit}".trim(), Dates.medium(r.date)) }.orEmpty()
            Text(m.medication, style = MaterialTheme.typography.titleSmall)
            if (detail.isNotEmpty()) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionLabel(stringResource(R.string.ec_details))
        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.ec_name_label)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = allergies, onValueChange = { allergies = it }, label = { Text(stringResource(R.string.ec_allergies)) },
            placeholder = { Text(stringResource(R.string.ec_allergies_hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = conditions, onValueChange = { conditions = it }, label = { Text(stringResource(R.string.ec_conditions)) },
            placeholder = { Text(stringResource(R.string.ec_conditions_hint)) }, minLines = 2, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = contact, onValueChange = { contact = it }, label = { Text(stringResource(R.string.ec_contact)) },
            placeholder = { Text(stringResource(R.string.ec_contact_hint)) }, modifier = Modifier.fillMaxWidth()
        )
        ToggleRow(stringResource(R.string.ec_dark), dark, inset = Spacing.xxs) { dark = it }
        SectionLabel(stringResource(R.string.ec_image))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            GlassOutlinedButton(onClick = { savePng.launch(fileName("png")) }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.ec_save_image))
            }
            GlassOutlinedButton(onClick = { share("png", "image/png") }, modifier = Modifier.weight(1f).heightIn(min = Spacing.touch)) {
                Text(stringResource(R.string.ec_share_image))
            }
        }
    }
}
