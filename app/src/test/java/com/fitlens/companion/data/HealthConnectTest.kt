package com.fitlens.companion.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Health Connect (#184): the primary source rule, the synced days, and how a sync's daily totals are kept: one value a
 * day from Health Connect, replaced at each sync, beside the user's own values, which are never touched. Synthetic
 * data only.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HealthConnectTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        app.deleteDatabase(Db.NAME)
    }

    @After
    fun tearDown() {
        app.deleteDatabase(Db.NAME)
    }

    private fun withDb(block: (Db) -> Unit) = Db(app).use { h -> runBlocking(Dispatchers.IO) { block(h) } }

    private fun rec(value: Double, source: String) = MRecord(0, Steps.NAME, Steps.UNIT, "2026-10-16", Steps.TIME, value, null, source)

    private fun Db.values(name: String, source: String, vararg dates: String) =
        importDao.recordsFrom(name, source, dates.toList()).sortedBy { it.date }

    private fun steps(vararg days: Pair<String, Double?>) =
        Store.HealthSeries(Steps.NAME, Steps.UNIT, days.associate { (d, v) -> d to v?.let { Store.HealthDay(it, HealthConnect.NAME) } })

    @Test
    fun healthConnectCountsOnlyWhenItIsThePrimarySource() {
        val typed = rec(3000.0, "manual")
        val hc = rec(8000.0, Sources.HEALTH_CONNECT)
        assertEquals(listOf(hc), Sources.dayValues(listOf(typed, hc), hcPrimary = true))
        assertEquals(listOf(typed), Sources.dayValues(listOf(typed, hc), hcPrimary = false))
        // A day with only one kind of value counts it, whichever source is primary.
        assertEquals(listOf(typed), Sources.dayValues(listOf(typed), hcPrimary = true))
        assertEquals(listOf(hc), Sources.dayValues(listOf(hc), hcPrimary = false))
        assertTrue(Sources.dayValues(emptyList(), hcPrimary = true).isEmpty())
    }

    @Test
    fun aSyncCoversEveryDayOfTheWindowAcrossAMonthEnd() {
        val days = HealthConnect.days(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 10, 2))
        assertEquals(listOf("2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02"), days)
        assertEquals(1, HealthConnect.days(LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16)).size)
    }

    @Test
    fun aValueNamesTheAppsItCameFrom() {
        assertEquals("Health Connect", HealthConnect.commentFor(emptyList()))
        assertEquals("Health Connect · MacrosFirst", HealthConnect.commentFor(listOf("MacrosFirst")))
    }

    @Test
    fun aSyncKeepsOneValueADayAndReplacesItNextTime() = withDb { db ->
        assertTrue(Store.writeHealthDays(db, listOf(steps("2026-10-15" to 9000.0, "2026-10-16" to 2000.0))))
        assertEquals(listOf(9000.0, 2000.0), db.values(Steps.NAME, Sources.HEALTH_CONNECT, "2026-10-15", "2026-10-16").map { it.value })
        // Later the same day: today's total has grown, yesterday's is unchanged.
        assertTrue(Store.writeHealthDays(db, listOf(steps("2026-10-15" to 9000.0, "2026-10-16" to 5500.0))))
        val after = db.values(Steps.NAME, Sources.HEALTH_CONNECT, "2026-10-15", "2026-10-16")
        assertEquals(listOf(9000.0, 5500.0), after.map { it.value })
        // Nothing new: nothing written.
        assertFalse(Store.writeHealthDays(db, listOf(steps("2026-10-15" to 9000.0, "2026-10-16" to 5500.0))))
    }

    @Test
    fun theUsersOwnValuesAreNeverTouched() = withDb { db ->
        db.bodyDao.ensure(Steps.NAME, Steps.UNIT, 999)
        db.bodyDao.addManualRecord(Steps.NAME, Steps.UNIT, "2026-10-16", "12:00:00", 4321.0, null)
        db.importDao.addRecord(Steps.NAME, Steps.UNIT, "2026-10-16", Steps.TIME, 3000.0, null, Steps.SOURCE)
        Store.writeHealthDays(db, listOf(steps("2026-10-16" to 8000.0)))
        Store.writeHealthDays(db, listOf(steps("2026-10-16" to 8500.0)))
        assertEquals(listOf(4321.0), db.values(Steps.NAME, "manual", "2026-10-16").map { it.value })
        assertEquals(listOf(3000.0), db.values(Steps.NAME, Steps.SOURCE, "2026-10-16").map { it.value })
        assertEquals(listOf(8500.0), db.values(Steps.NAME, Sources.HEALTH_CONNECT, "2026-10-16").map { it.value })
        // The phone's step counter adds to its own value, never to Health Connect's.
        assertFalse(db.importDao.firstRecordOn(Steps.NAME, "2026-10-16")!!.source == Sources.HEALTH_CONNECT)
    }

    @Test
    fun aDayEmptiedInHealthConnectLosesItsValue() = withDb { db ->
        Store.writeHealthDays(db, listOf(steps("2026-10-14" to 7000.0)))
        assertTrue(Store.writeHealthDays(db, listOf(steps("2026-10-14" to null))))
        assertTrue(db.values(Steps.NAME, Sources.HEALTH_CONNECT, "2026-10-14").isEmpty())
    }

    @Test
    fun intakeGoesIntoTheMeasurementAlreadyThereWhateverItsCapitals() = withDb { db ->
        db.bodyDao.addOwn("calories eaten", "kcal", 1)
        val kcal = Intake.Kind.KCAL
        Store.writeHealthDays(db, listOf(Store.HealthSeries(kcal.label, kcal.unit, mapOf("2026-10-16" to Store.HealthDay(2150.0, "Health Connect · MacrosFirst")), own = true)))
        val kept = db.values("calories eaten", Sources.HEALTH_CONNECT, "2026-10-16")
        assertEquals(1, kept.size)
        assertEquals(2150.0, kept[0].value, 1e-9)
        assertEquals("Health Connect · MacrosFirst", kept[0].comment)
        assertFalse(db.bodyDao.definitionNames().contains(kcal.label))
    }
}
