package com.fitlens.companion.report

import android.content.res.Resources
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.InjectionSite
import com.fitlens.companion.data.MedSchedules
import com.fitlens.companion.data.Snapshot
import com.fitlens.companion.data.Symptoms
import com.fitlens.companion.data.fmtNum
import com.fitlens.companion.ui.siteName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** What goes into the Medications report (#205). Dates are ISO and inclusive; [names] empty means every medication. */
data class MedicationReportOptions(
    val from: String,
    val to: String,
    val names: Set<String> = emptySet(),
    val dark: Boolean = false,
    /** Printed at the top when given; nothing identifies the patient otherwise. */
    val patient: String = "",
    val schedules: Boolean = true,
    val adherence: Boolean = true,
    val doseLog: Boolean = true,
    val comments: Boolean = true,
    val graphs: Boolean = true,
    /** The symptom ratings in the range, each with its graph (#223). */
    val symptoms: Boolean = true,
    /** Body measurements to summarise and graph over the range (#213), read from the body area; empty leaves them out
     *  and keeps the report to medications only. Any one makes it an appointment summary. */
    val body: List<String> = emptyList()
) {
    val summary: Boolean get() = body.isNotEmpty()
}

/**
 * A Medications-only PDF to share with a doctor (#205), drawn with the progress report's pages ([PageWriter], light
 * for printing by default): a heading with the patient line and range, current medications and their schedules,
 * adherence per medication, a dose log (due, taken, amount, status, dose changes), comments and a graph per
 * medication, and the symptoms rated over the range (#223). With body values chosen (#213) it becomes an appointment
 * summary: each chosen measurement's readings over the range, summarised and graphed. It never holds workouts or photos,
 * and body values only when they're chosen.
 */
object MedicationReport {

    /** Writes the report to [os]. Returns the number of pages. */
    suspend fun create(res: Resources, snap: Snapshot, o: MedicationReportOptions, os: OutputStream, today: String = Dates.today()): Int =
        withContext(Dispatchers.Default) {
            val doc = PdfDocument()
            try {
                val w = PageWriter(res, doc, if (o.dark) DARK else LIGHT, res.getString(R.string.mdr_footer, Dates.medium(o.from), Dates.medium(o.to)))
                val meds = snap.medicationView
                val names = o.names.ifEmpty { snap.medications.map { it.name }.toSet() }
                val log = MedSchedules.doseLog(snap.medPlan, meds.records, o.from, o.to, today, names)
                heading(w, o, names)
                if (o.schedules) schedules(w, snap, names, today)
                if (o.adherence) adherence(w, MedSchedules.adherence(log))
                if (o.graphs) graphs(w, snap, o, names)
                if (o.symptoms) symptoms(w, snap, o)
                if (o.summary) bodyValues(w, snap, o)
                if (o.doseLog) doseLog(w, log)
                if (o.comments) comments(w, log)
                w.finish()
                withContext(Dispatchers.IO) { doc.writeTo(os) }
                w.pages
            } finally {
                doc.close()
            }
        }

    private fun label(w: PageWriter, text: String) {
        w.ensure(34f)
        w.c.drawText(text.uppercase(), M, w.y + 12f, paint(w.p.gold, 9f, bold = true, tracking = 0.25f))
        w.c.drawRect(M, w.y + 20f, M + CW, w.y + 20.8f, fill(w.p.gold))
        w.y += 32f
    }

    /** Lines of [text] wrapped to the page, starting new pages as needed. */
    private fun para(w: PageWriter, text: String, size: Float = 9.5f, muted: Boolean = false, indent: Float = 0f) {
        val pt = paint(if (muted) w.p.muted else w.p.text, size)
        wrap(text, pt, CW - indent).forEach { line ->
            w.ensure(size + 5f)
            w.c.drawText(line, M + indent, w.y + size, pt)
            w.y += size + 5f
        }
    }

    private fun heading(w: PageWriter, o: MedicationReportOptions, names: Set<String>) {
        val res = w.res
        w.newPage()
        w.c.drawText("FLEXNOTES", M, w.y + 12f, paint(w.p.gold, 10f, bold = true, tracking = 0.4f))
        w.y += 22f
        w.c.drawText(res.getString(if (o.summary) R.string.mdr_title_summary else R.string.mdr_title), M, w.y + 24f, paint(w.p.text, 24f, bold = true))
        w.y += 38f
        if (o.patient.isNotBlank()) para(w, res.getString(R.string.mdr_patient, o.patient.trim()), 11f)
        para(w, res.getString(R.string.mdr_range, Dates.medium(o.from), Dates.medium(o.to)), 10f, muted = true)
        para(w, res.getString(R.string.mdr_generated, Dates.medium(Dates.today())), 9f, muted = true)
        para(w, res.getString(R.string.mdr_medications, names.sorted().joinToString(", ").ifEmpty { "–" }), 9.5f)
        para(w, res.getString(R.string.mdr_disclaimer), 8f, muted = true)
        w.y += 8f
    }

    private fun schedules(w: PageWriter, snap: Snapshot, names: Set<String>, today: String) {
        val res = w.res
        val plan = snap.medPlan
        label(w, res.getString(R.string.mdr_current))
        // Each medication with its latest value, then each schedule that doses it and where that schedule is.
        names.sorted().forEach { n ->
            val last = snap.medicationView.recordsByName[n]?.lastOrNull()
            val head = if (last != null) res.getString(R.string.mdr_last_dose, n, "${fmtNum(last.value)} ${last.unit}".trim(), Dates.medium(last.date))
                else n
            para(w, head, 10.5f)
            plan.schedules.filter { s -> s.phases.any { p -> p.doses.any { it.medication == n } } }.forEach { s ->
                val run = plan.current(s.id, today)
                val state = when {
                    run == null -> res.getString(R.string.md_not_running)
                    run.start > today -> res.getString(R.string.md_starts, Dates.medium(run.start))
                    run.paused -> res.getString(R.string.mdr_paused)
                    else -> MedSchedules.phaseOn(s, run, today)?.let { st ->
                        res.getString(if (st.phase.on) R.string.md_phase_on else R.string.mdr_off_day, st.day, st.days)
                    } ?: res.getString(R.string.md_finished)
                }
                val phases = s.phases.joinToString(" · ") { p ->
                    val len = res.getQuantityString(
                        when (p.unit) {
                            com.fitlens.companion.data.PhaseUnit.DAYS -> R.plurals.md_days
                            com.fitlens.companion.data.PhaseUnit.WEEKS -> R.plurals.md_weeks
                            com.fitlens.companion.data.PhaseUnit.MONTHS -> R.plurals.md_months
                            com.fitlens.companion.data.PhaseUnit.YEARS -> R.plurals.md_years
                        },
                        p.length, p.length
                    )
                    res.getString(if (p.on) R.string.md_summary_on else R.string.md_summary_off, len)
                } + if (s.repeats) res.getString(R.string.mdr_repeating) else ""
                para(w, "${s.name}: $phases. $state", 9f, muted = true, indent = 12f)
                s.phases.filter { it.on }.flatMap { it.doses }.filter { it.medication == n }.distinctBy { listOf(it.time, it.amount, it.days) }.forEach { d ->
                    val days = when {
                        d.days.weekdays.isNotEmpty() -> MedSchedules.weekOrder(1).filter { it in d.days.weekdays }.joinToString(", ") { MedSchedules.weekdayShort(it) }
                        d.days.every > 1 -> res.getString(R.string.md_every_n_days, d.days.every)
                        else -> res.getString(R.string.md_every_day)
                    }
                    para(w, "${fmtNum(d.amount)} ${d.unit} · ${d.time} · $days", 9f, indent = 24f)
                }
                plan.changes.filter { c -> run != null && c.activationId == run.id && plan.dosesById[c.doseId]?.second?.medication == n }.forEach { c ->
                    val unit = plan.dosesById[c.doseId]?.second?.unit.orEmpty()
                    val text = if (c.toDate == c.fromDate) res.getString(R.string.mdr_change_day, Dates.medium(c.fromDate), "${fmtNum(c.amount)} $unit".trim())
                        else res.getString(R.string.mdr_change_rest, Dates.medium(c.fromDate), "${fmtNum(c.amount)} $unit".trim())
                    para(w, text, 9f, muted = true, indent = 24f)
                }
            }
            w.y += 4f
        }
        w.y += 6f
    }

    private fun adherence(w: PageWriter, rows: List<MedSchedules.Adherence>) {
        val res = w.res
        label(w, res.getString(R.string.mdr_adherence))
        if (rows.isEmpty()) {
            para(w, res.getString(R.string.mdr_no_schedule), 9.5f, muted = true)
            w.y += 6f
            return
        }
        val cols = floatArrayOf(M, M + 200f, M + 270f, M + 330f, M + 390f, M + 450f)
        val hp = paint(w.p.muted, 7.5f, bold = true, tracking = 0.15f)
        w.ensure(20f)
        listOf(R.string.mdr_col_medication, R.string.mdr_col_scheduled, R.string.mdr_col_taken, R.string.mdr_col_skipped, R.string.mdr_col_missed, R.string.mdr_col_adherence)
            .forEachIndexed { i, id -> w.c.drawText(res.getString(id).uppercase(), cols[i], w.y + 8f, hp) }
        w.y += 16f
        val tp = paint(w.p.text, 9.5f)
        val gp = paint(w.p.gold, 9.5f, bold = true)
        rows.forEach { a ->
            w.ensure(16f)
            w.c.drawText(a.medication, cols[0], w.y + 10f, tp)
            listOf(a.scheduled, a.taken, a.skipped, a.missed).forEachIndexed { i, v -> w.c.drawText(v.toString(), cols[i + 1], w.y + 10f, tp) }
            w.c.drawText(a.percent?.let { "$it%" } ?: "–", cols[5], w.y + 10f, gp)
            w.y += 16f
        }
        para(w, res.getString(R.string.mdr_adherence_note), 8f, muted = true)
        w.y += 6f
    }

    private fun graphs(w: PageWriter, snap: Snapshot, o: MedicationReportOptions, names: Set<String>) {
        val res = w.res
        val series = names.sorted().mapNotNull { n ->
            val pts = snap.medicationView.dailySeries(n).filter { it.date.take(10) in o.from..o.to }
            if (pts.size < 2) null else Triple(n, pts.last().unit, pts)
        }
        if (series.isEmpty()) return
        label(w, res.getString(R.string.mdr_graphs))
        series.forEach { (n, unit, pts) ->
            val h = 120f
            w.ensure(h + 30f)
            w.c.drawText(if (unit.isBlank()) n else "$n ($unit)", M, w.y + 10f, paint(w.p.text, 10f, bold = true))
            val r = RectF(M + 28f, w.y + 18f, M + CW, w.y + 18f + h)
            PdfReport.lineChart(w, pts.map { Dates.epochDay(it.date) to it.value }, emptyList(), r)
            val dp = paint(w.p.muted, 7f)
            w.c.drawText(Dates.short(pts.first().date), r.left, r.bottom + 12f, dp)
            w.rightText(Dates.short(pts.last().date), dp, r.right, r.bottom + 12f)
            w.y = r.bottom + 24f
        }
    }

    /**
     * Each symptom rated in the range (#223): how often, the average, lowest and highest rating and the latest, then
     * a small graph of the ratings when there are two or more. Nothing is drawn when no symptom was rated.
     */
    private fun symptoms(w: PageWriter, snap: Snapshot, o: MedicationReportOptions) {
        val res = w.res
        val plan = snap.medPlan
        val rated = plan.symptoms.mapNotNull { s ->
            val pts = Symptoms.series(plan, s.id).filter { it.date.take(10) in o.from..o.to }
            if (pts.isEmpty()) null else s to pts
        }
        if (rated.isEmpty()) return
        label(w, res.getString(R.string.mdr_symptoms))
        para(w, res.getString(R.string.mdr_symptoms_note, Symptoms.MAX), 8f, muted = true)
        w.y += 4f
        rated.forEach { (s, pts) ->
            val avg = pts.sumOf { it.rating }.toDouble() / pts.size
            val latest = pts.last()
            val h = if (pts.size >= 2) 70f else 0f
            w.ensure(h + 44f)
            w.c.drawText(s.name, M, w.y + 10f, paint(w.p.text, 10f, bold = true))
            w.y += 16f
            val summary = if (pts.size == 1) res.getString(R.string.mdr_symptom_one, latest.rating, Dates.medium(latest.date))
                else res.getString(
                    R.string.mdr_symptom_summary, pts.size, fmtNum(avg, 1),
                    pts.minOf { it.rating }, pts.maxOf { it.rating }, latest.rating, Dates.medium(latest.date)
                )
            para(w, summary, 9f, muted = true)
            if (h > 0f) {
                val r = RectF(M + 28f, w.y + 4f, M + CW, w.y + 4f + h)
                PdfReport.lineChart(w, pts.map { Dates.epochDay(it.date) to it.rating.toDouble() }, emptyList(), r)
                val dp = paint(w.p.muted, 7f)
                w.c.drawText(Dates.short(pts.first().date), r.left, r.bottom + 12f, dp)
                w.rightText(Dates.short(pts.last().date), dp, r.right, r.bottom + 12f)
                w.y = r.bottom + 20f
            }
            pts.filter { !it.comment.isNullOrBlank() }.forEach { rt ->
                para(w, "${Dates.medium(rt.date)} · ${rt.rating}/${Symptoms.MAX} · “${rt.comment!!.trim()}”", 8.5f, indent = 8f)
            }
            w.y += 6f
        }
    }

    /**
     * The chosen body measurements over the range (#213): how many readings, the first and the latest with the change
     * between them in the measurement's unit, the lowest and highest, then a graph when there are two or more. A
     * measurement with no reading in the range says so. [snap] must be the body area: the medication view holds none.
     */
    private fun bodyValues(w: PageWriter, snap: Snapshot, o: MedicationReportOptions) {
        val res = w.res
        label(w, res.getString(R.string.mdr_body))
        o.body.forEach { n ->
            val pts = if (snap.showsMedications) emptyList() else snap.dailySeries(n).filter { it.date.take(10) in o.from..o.to }
            val unit = pts.lastOrNull()?.unit ?: snap.allMeasurements.firstOrNull { it.name == n }?.unit.orEmpty()
            fun v(x: Double) = "${fmtNum(x, 1)} $unit".trim()
            val h = if (pts.size >= 2) 90f else 0f
            w.ensure(h + 44f)
            w.c.drawText(if (unit.isBlank()) n else "$n ($unit)", M, w.y + 10f, paint(w.p.text, 10f, bold = true))
            w.y += 16f
            if (pts.isEmpty()) {
                para(w, res.getString(R.string.mdr_body_none), 9f, muted = true)
                w.y += 6f
                return@forEach
            }
            val first = pts.first()
            val last = pts.last()
            val count = res.getQuantityString(R.plurals.mdr_body_count, pts.size, pts.size)
            val summary = if (pts.size == 1) res.getString(R.string.mdr_body_one, count, v(last.value), Dates.medium(last.date))
                else res.getString(
                    R.string.mdr_body_summary, count, v(last.value), Dates.medium(last.date),
                    "${com.fitlens.companion.data.fmtSigned(last.value - first.value, 1)} $unit".trim(), v(first.value), Dates.medium(first.date),
                    v(pts.minOf { it.value }), v(pts.maxOf { it.value })
                )
            para(w, summary, 9f, muted = true)
            if (h > 0f) {
                val r = RectF(M + 28f, w.y + 4f, M + CW, w.y + 4f + h)
                PdfReport.lineChart(w, pts.map { Dates.epochDay(it.date) to it.value }, emptyList(), r)
                val dp = paint(w.p.muted, 7f)
                w.c.drawText(Dates.short(first.date), r.left, r.bottom + 12f, dp)
                w.rightText(Dates.short(last.date), dp, r.right, r.bottom + 12f)
                w.y = r.bottom + 20f
            }
            w.y += 6f
        }
    }

    private fun doseLog(w: PageWriter, log: List<MedSchedules.LogEntry>) {
        val res = w.res
        label(w, res.getString(R.string.mdr_dose_log))
        if (log.isEmpty()) {
            para(w, res.getString(R.string.mdr_nothing), 9.5f, muted = true)
            return
        }
        // The Site column (#223) holds where an injected dose went; it stays empty for any other value.
        val cols = floatArrayOf(M, M + 68f, M + 108f, M + 150f, M + 278f, M + 368f, M + 435f)
        val hp = paint(w.p.muted, 7.5f, bold = true, tracking = 0.15f)
        fun header() {
            listOf(R.string.mdr_col_date, R.string.mdr_col_due, R.string.mdr_col_taken_at, R.string.mdr_col_medication, R.string.mdr_col_site, R.string.mdr_col_amount, R.string.mdr_col_status)
                .forEachIndexed { i, id -> w.c.drawText(res.getString(id).uppercase(), cols[i], w.y + 8f, hp) }
            w.y += 16f
        }
        w.ensure(30f)
        header()
        val tp = paint(w.p.text, 8.5f)
        val mp = paint(w.p.muted, 8.5f)
        log.forEach { e ->
            if (w.ensure(14f)) header()
            val d = e.due
            val r = e.record
            val amount = (r?.value ?: d?.amount)?.let { "${fmtNum(it)} ${r?.unit ?: d?.dose?.unit.orEmpty()}".trim() }.orEmpty()
            val status = when (e.outcome) {
                MedSchedules.Outcome.TAKEN -> res.getString(R.string.mdr_taken)
                MedSchedules.Outcome.SKIPPED -> res.getString(R.string.md_skipped)
                MedSchedules.Outcome.MISSED -> res.getString(R.string.md_missed)
                MedSchedules.Outcome.DUE -> res.getString(R.string.mdr_due)
                null -> res.getString(R.string.mdr_logged)
            } + if (d?.change != null) " · " + res.getString(R.string.mdr_changed) else ""
            w.c.drawText(Dates.short(e.date), cols[0], w.y + 9f, tp)
            w.c.drawText(d?.time.orEmpty(), cols[1], w.y + 9f, tp)
            w.c.drawText(r?.time?.take(5).orEmpty(), cols[2], w.y + 9f, tp)
            w.c.drawText(clip(e.medication, tp, cols[4] - cols[3] - 6f), cols[3], w.y + 9f, tp)
            InjectionSite.of(r?.site)?.let { w.c.drawText(clip(siteName(res, it), tp, cols[5] - cols[4] - 6f), cols[4], w.y + 9f, tp) }
            w.c.drawText(amount, cols[5], w.y + 9f, tp)
            w.c.drawText(status, cols[6], w.y + 9f, if (e.outcome == MedSchedules.Outcome.TAKEN || e.outcome == null) tp else mp)
            w.y += 14f
        }
        w.y += 8f
    }

    /** [text] cut with an ellipsis to fit [width], so a long name can't run into the next column. */
    private fun clip(text: String, p: android.graphics.Paint, width: Float): String {
        if (p.measureText(text) <= width) return text
        var end = text.length
        while (end > 1 && p.measureText(text, 0, end) + p.measureText("…") > width) end--
        return text.take(end).trimEnd() + "…"
    }

    private fun comments(w: PageWriter, log: List<MedSchedules.LogEntry>) {
        val res = w.res
        val notes = log.mapNotNull { e -> e.record?.comment?.takeIf { it.isNotBlank() }?.let { e to it } }
        if (notes.isEmpty()) return
        label(w, res.getString(R.string.mdr_comments))
        notes.forEach { (e, text) ->
            para(w, "${Dates.medium(e.date)} · ${e.medication}", 8.5f, muted = true)
            para(w, "“$text”", 9.5f, indent = 8f)
            w.y += 3f
        }
    }
}
