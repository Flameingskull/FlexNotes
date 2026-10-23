package com.fitlens.companion.report

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import android.graphics.pdf.PdfDocument
import com.fitlens.companion.R
import com.fitlens.companion.data.Dates
import com.fitlens.companion.data.EmergencyCard
import com.fitlens.companion.data.EmergencyDetails
import com.fitlens.companion.data.fmtNum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * The emergency card (#215) as a one-page PDF or a PNG image: the name, current medications with their doses,
 * allergies, conditions and an emergency contact. Both are drawn by [draw], light for printing by default.
 */
object EmergencyCardPdf {

    /** Writes the card as a one-page A4 PDF, scaled down to fit when it runs long. */
    suspend fun pdf(res: Resources, d: EmergencyDetails, meds: List<EmergencyCard.Line>, os: OutputStream, dark: Boolean = false, today: String = Dates.today()) =
        withContext(Dispatchers.Default) {
            val p = if (dark) DARK else LIGHT
            val doc = PdfDocument()
            try {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PW, PH, 1).create())
                val c = page.canvas
                c.drawColor(p.bg)
                // Measured first, so a long list shrinks to the page rather than running off it.
                val h = record(res, d, meds, p, today).second
                if (h > PH) c.scale(PH / h, PH / h)
                draw(c, res, d, meds, p, today)
                doc.finishPage(page)
                withContext(Dispatchers.IO) { doc.writeTo(os) }
            } finally {
                doc.close()
            }
        }

    /** Writes the card as a PNG at twice the page's size, cut to its content, for a phone's lock screen or gallery. */
    suspend fun png(res: Resources, d: EmergencyDetails, meds: List<EmergencyCard.Line>, os: OutputStream, dark: Boolean = false, today: String = Dates.today()) =
        withContext(Dispatchers.Default) {
            val p = if (dark) DARK else LIGHT
            val (pic, h) = record(res, d, meds, p, today)
            val bmp = Bitmap.createBitmap(PW * SCALE, (h * SCALE).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            try {
                val c = Canvas(bmp)
                c.drawColor(p.bg)
                c.scale(SCALE.toFloat(), SCALE.toFloat())
                c.drawPicture(pic)
                withContext(Dispatchers.IO) { bmp.compress(Bitmap.CompressFormat.PNG, 100, os) }
            } finally {
                bmp.recycle()
            }
        }

    private const val SCALE = 2
    /** Room to record into; the card is cut to the height [draw] returns. */
    private const val MAX_H = PH * 3

    private fun record(res: Resources, d: EmergencyDetails, meds: List<EmergencyCard.Line>, p: Palette, today: String): Pair<Picture, Float> {
        val pic = Picture()
        val h = draw(pic.beginRecording(PW, MAX_H), res, d, meds, p, today)
        pic.endRecording()
        return pic to h.coerceAtMost(MAX_H.toFloat())
    }

    /** Draws the card from the top of [c], [PW] points wide. Returns the height used. */
    internal fun draw(c: Canvas, res: Resources, d: EmergencyDetails, meds: List<EmergencyCard.Line>, p: Palette, today: String): Float {
        var y = M
        fun text(s: String, size: Float, color: Int = p.text, bold: Boolean = false, indent: Float = 0f) {
            val pt = paint(color, size, bold = bold)
            wrap(s, pt, CW - indent).forEach { line ->
                c.drawText(line, M + indent, y + size, pt)
                y += size + 5f
            }
        }
        fun label(s: String) {
            y += 10f
            c.drawText(s.uppercase(), M, y + 12f, paint(p.gold, 9f, bold = true, tracking = 0.25f))
            c.drawRect(M, y + 20f, M + CW, y + 20.8f, fill(p.gold))
            y += 30f
        }
        fun block(title: String, body: String) {
            label(title)
            if (body.isBlank()) text(res.getString(R.string.ec_none_recorded), 11f, p.muted) else text(body.trim(), 12f)
        }

        c.drawText("FLEXNOTES", M, y + 12f, paint(p.gold, 10f, bold = true, tracking = 0.4f))
        y += 22f
        c.drawText(res.getString(R.string.ec_title), M, y + 26f, paint(p.text, 26f, bold = true))
        y += 40f
        if (d.name.isNotBlank()) text(d.name.trim(), 15f, bold = true)

        label(res.getString(R.string.ec_current))
        if (meds.isEmpty()) text(res.getString(R.string.ec_no_medications), 11f, p.muted)
        meds.forEach { m ->
            text(m.medication, 12f, bold = true)
            val detail = if (m.scheduled) m.doses.joinToString(", ") { it.text }
                else m.last?.let { r -> res.getString(R.string.ec_as_needed, "${fmtNum(r.value)} ${r.unit}".trim(), Dates.medium(r.date)) }.orEmpty()
            if (detail.isNotEmpty()) text(detail, 10f, p.muted, indent = 10f)
            y += 4f
        }
        block(res.getString(R.string.ec_allergies), d.allergies)
        block(res.getString(R.string.ec_conditions), d.conditions)
        if (d.contact.isNotBlank()) block(res.getString(R.string.ec_contact), d.contact)

        y += 14f
        c.drawRect(M, y, M + CW, y + 0.6f, fill(p.hairline))
        y += 8f
        text(res.getString(R.string.ec_footer, Dates.medium(today)), 8f, p.muted)
        return y + M / 2
    }
}
