package com.fitlens.companion.ui

import com.fitlens.companion.data.Dates
import java.time.LocalDate

/** The first and last day (epoch days, both included) a graph's time axis covers (#328). */
data class ChartPeriod(val from: Long, val to: Long)

/** The graph range menu's last entry, All time. Entries 0 to 3 are 1, 3, 6 and 12 calendar months (`RANGES`' order). */
internal const val RANGE_ALL = 4

/** How many calendar months range [rangeIdx] covers: 1, 3, 6 or 12. 0 is all time (and any index that isn't a range). */
internal fun rangeMonths(rangeIdx: Int): Int = when (rangeIdx) {
    0 -> 1
    1 -> 3
    2 -> 6
    3 -> 12
    else -> 0
}

/**
 * The days range [rangeIdx] covers: from [today] counted back whole calendar months, to [today]. Null for All time,
 * which runs from the first value ever logged to the latest. Every graph uses this, so "3 months" means the same
 * period on every screen (#328).
 */
internal fun rangePeriod(rangeIdx: Int, today: LocalDate = Dates.todayDate()): ChartPeriod? {
    val months = rangeMonths(rangeIdx)
    if (months == 0) return null
    return ChartPeriod(today.minusMonths(months.toLong()).toEpochDay(), today.toEpochDay())
}

/** The first day of range [rangeIdx] as `yyyy-MM-dd`, or null for All time. */
internal fun rangeStartIso(rangeIdx: Int, today: LocalDate = Dates.todayDate()): String? =
    rangePeriod(rangeIdx, today)?.let { LocalDate.ofEpochDay(it.from).format(Dates.ISO) }

/** The first epoch day of range [rangeIdx]; for All time, [earliest] (the first day anything was logged). */
internal fun rangeStartDay(rangeIdx: Int, earliest: Long, today: LocalDate = Dates.todayDate()): Long =
    rangePeriod(rangeIdx, today)?.from ?: earliest

/** The [items] whose day ([dayOf], an epoch day) falls in [period], both ends included; all of them when [period] is null. */
internal fun <T> inPeriod(items: List<T>, period: ChartPeriod?, dayOf: (T) -> Long): List<T> =
    if (period == null) items else items.filter { dayOf(it) in period.from..period.to }
