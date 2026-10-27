package com.fitlens.companion.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** Times in the phone's clock style (#280): the stored `HH:mm` stays as it is, only the reading changes. */
class ClockStyleTest {
    private val saved = Locale.getDefault()

    @Before fun english() = Locale.setDefault(Locale.US)

    @After fun restore() = Locale.setDefault(saved)

    @Test fun twentyFourHourKeepsTheStoredForm() {
        assertEquals("14:05", Dates.time("14:05", true))
        assertEquals("08:00", Dates.time("8:00", true))
    }

    @Test fun twelveHourReadsWithAmOrPm() {
        assertEquals("2:05 PM", Dates.time("14:05", false))
        assertEquals("12:00 AM", Dates.time("00:00", false))
        assertEquals("8:30 AM", Dates.time("08:30:00", false))
    }

    @Test fun anUnreadableTimeIsLeftAsItWas() {
        assertEquals("soon", Dates.time("soon", false))
        assertNull(Dates.clock("25:99"))
        assertEquals(14, Dates.clock("14:05:30")?.hour)
    }
}
