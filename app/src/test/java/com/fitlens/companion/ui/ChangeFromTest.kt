package com.fitlens.companion.ui

import com.fitlens.companion.data.MRecord
import com.fitlens.companion.data.PortableSettings
import com.fitlens.companion.data.Sources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a body value's change is measured from, with two values on one day (#372). */
class ChangeFromTest {

    private fun rec(id: Long, date: String, time: String, value: Double) =
        MRecord(id, "Bodyweight", "kg", date, time, value, null, Sources.FLEXNOTES)

    private val history = listOf(
        rec(1, "2026-10-28", "07:00:00", 90.0),
        rec(2, "2026-10-29", "07:00:00", 89.0),
        rec(3, "2026-10-29", "18:00:00", 88.0)
    )

    @Test
    fun byDefaultTheChangeIsFromTheLastValueOnAnEarlierDay() {
        assertTrue(PortableSettings().changeFromEarlierDay)
        assertEquals(1L, previousValue(history, history[2], true)?.id)
        assertEquals(1L, previousValue(history, history[1], true)?.id)
        assertNull(previousValue(history, history[0], true))
    }

    @Test
    fun theOtherChoiceIsTheValueJustBefore() {
        assertEquals(2L, previousValue(history, history[2], false)?.id)
        assertEquals(1L, previousValue(history, history[1], false)?.id)
        assertNull(previousValue(history, history[0], false))
    }
}
