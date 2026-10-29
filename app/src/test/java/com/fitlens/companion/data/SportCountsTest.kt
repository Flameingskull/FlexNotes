package com.fitlens.companion.data

import com.fitlens.companion.data.countParts
import com.fitlens.companion.ui.countValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SportCountsTest {
    @Test
    fun namesAreTrimmedLimitedToTwoAndStoredTogether() {
        assertEquals("Takedowns|Submissions", SportCounts.join(listOf(" Takedowns ", "", "Submissions", "Points")))
        assertNull(SportCounts.join(listOf("", "  ")))
        assertEquals(listOf("Takedowns", "Submissions"), SportCounts.parse("Takedowns|Submissions|Points"))
        assertEquals(emptyList<String>(), SportCounts.parse(null))
        assertEquals("A B", SportCounts.join(listOf("A|B")))
    }

    @Test
    fun aSetLineShowsOnlyNamedCountsAboveZero() {
        assertEquals(listOf("3 Takedowns"), countParts(listOf("Takedowns", "Submissions"), 3, 0))
        assertEquals(listOf("2 Submissions"), countParts(listOf("Takedowns", "Submissions"), null, 2))
        assertEquals(emptyList<String>(), countParts(emptyList(), 3, 4))
    }

    @Test
    fun typedCountsKeepDigitsOnly() {
        assertEquals(12, countValue("1a2"))
        assertNull(countValue(""))
    }

    @Test
    fun sportsOfEachKindSuggestCounts() {
        assertEquals(true, SportCounts.suggest(SessionKinds.COMBAT).contains("Takedowns"))
        assertEquals(emptyList<String>(), SportCounts.suggest(SessionKinds.MIND_BODY))
    }

    @Test
    fun countsEndTheCsvHeaderAndAppendTheGraphs() {
        assertEquals(listOf("Count 1 name", "Count 1", "Count 2 name", "Count 2"), CsvExport.workoutColumns("kg").takeLast(4))
        assertEquals(listOf("Total Takedowns"), com.fitlens.companion.ui.countGraphLabels(listOf("Takedowns", " ")))
    }
}
