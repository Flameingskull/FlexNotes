package com.fitlens.companion.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** [Dates.plusDays] (#309): the one calendar step every date screen uses. */
class DatesPlusDaysTest {
    @Test fun movesAcrossMonthsAndYears() {
        assertEquals("2026-11-01", Dates.plusDays("2026-10-31", 1))
        assertEquals("2026-12-31", Dates.plusDays("2027-01-01", -1))
        assertEquals("2028-02-29", Dates.plusDays("2028-02-28", 1))
    }

    @Test fun zeroKeepsTheDateAndATimeIsDropped() {
        assertEquals("2026-10-29", Dates.plusDays("2026-10-29", 0))
        assertEquals("2026-10-30", Dates.plusDays("2026-10-29 18:30:00", 1))
    }

    @Test fun dayMonthShowsTheYearOnlyOutsideThisYear() {
        val year = java.time.LocalDate.now().year
        assertEquals(false, Dates.dayMonth("$year-03-03").contains(year.toString()))
        assertEquals(true, Dates.dayMonth("${year - 1}-12-30").contains((year - 1).toString()))
        assertEquals("not a date", Dates.dayMonth("not a date"))
    }

    @Test fun anUnreadableDateIsReturnedAsItWas() {
        assertEquals("not a date", Dates.plusDays("not a date", 3))
        assertEquals("", Dates.plusDays("", -1))
    }
}
