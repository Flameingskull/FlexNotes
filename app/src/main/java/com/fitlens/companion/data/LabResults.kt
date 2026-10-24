package com.fitlens.companion.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Lab results from Health Connect's medical records (#225): each laboratory result is a FHIR `Observation`, read as
 * JSON. Only results with a number and a date are kept; a result written as text ("negative") has no place on a graph
 * and is left out. Each one is filed under the lab marker of its name (made the first time, with the record's unit and
 * reference range) by `Store.addLabResults`. The parsing here is plain Kotlin on `org.json`, tested in `LabResultsTest`.
 */
object LabResults {
    /** One lab result: [key] identifies it across syncs, so it's added once, and never again after an edit or delete. */
    data class Result(
        val key: String,
        val name: String,
        val unit: String,
        val date: String,
        val time: String,
        val value: Double,
        val refLow: Double?,
        val refHigh: Double?
    )

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /**
     * The result in the FHIR Observation [json] from data source [source], with its time in [zone], or null when it
     * isn't a dated numeric Observation.
     */
    fun parse(json: String, source: String, zone: ZoneId = ZoneId.systemDefault()): Result? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (o.optString("resourceType") != "Observation") return null
        if (o.optString("status") in setOf("cancelled", "entered-in-error")) return null
        val quantity = o.optJSONObject("valueQuantity") ?: return null
        if (!quantity.has("value")) return null
        val value = quantity.optDouble("value").takeIf { !it.isNaN() } ?: return null
        val code = o.optJSONObject("code")
        val coding = code?.optJSONArray("coding")?.firstObject()
        val name = listOfNotNull(code?.optString("text"), coding?.optString("display"), coding?.optString("code"))
            .map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val unit = (quantity.optString("unit").ifBlank { quantity.optString("code") }).trim()
        val (date, time) = whenTaken(o, zone) ?: return null
        val range = o.optJSONArray("referenceRange")?.firstObject()
        val id = o.optString("id").ifBlank { "$name|$date|$time|$value" }
        return Result(
            key = "$source/$id",
            name = name.take(80),
            unit = unit,
            date = date,
            time = time,
            value = value,
            refLow = range?.optJSONObject("low")?.number("value"),
            refHigh = range?.optJSONObject("high")?.number("value")
        )
    }

    /** When it was taken: `effectiveDateTime`, the start of `effectivePeriod`, or `issued`, as a local date and time. */
    private fun whenTaken(o: JSONObject, zone: ZoneId): Pair<String, String>? {
        val raw = listOf(
            o.optString("effectiveDateTime"),
            o.optJSONObject("effectivePeriod")?.optString("start").orEmpty(),
            o.optString("effectiveInstant"),
            o.optString("issued")
        ).firstOrNull { it.isNotBlank() } ?: return null
        return dateTime(raw.trim(), zone)
    }

    /** A FHIR date or dateTime as a local ISO date and time; a date alone is kept at midnight. Internal for tests. */
    internal fun dateTime(raw: String, zone: ZoneId): Pair<String, String>? {
        runCatching { OffsetDateTime.parse(raw) }.getOrNull()?.let {
            val local = it.atZoneSameInstant(zone).toLocalDateTime()
            return local.toLocalDate().format(Dates.ISO) to local.withNano(0).format(TIME)
        }
        runCatching { LocalDateTime.parse(raw) }.getOrNull()?.let { return it.toLocalDate().format(Dates.ISO) to it.withNano(0).format(TIME) }
        runCatching { LocalDate.parse(raw) }.getOrNull()?.let { return it.format(Dates.ISO) to "00:00:00" }
        return null
    }

    private fun JSONArray.firstObject(): JSONObject? = if (length() > 0) optJSONObject(0) else null

    private fun JSONObject.number(key: String): Double? =
        if (has(key)) optDouble(key).takeIf { !it.isNaN() } else null

    /** The keys of results already added, from [DeviceSettings.hcLabIds] (one per line). */
    fun seen(stored: String?): Set<String> = stored.orEmpty().split('\n').filter { it.isNotBlank() }.toSet()

    /** [seen] with [added] joined, as stored. */
    fun store(seen: Set<String>, added: Collection<String>): String = (seen + added).sorted().joinToString("\n")
}
