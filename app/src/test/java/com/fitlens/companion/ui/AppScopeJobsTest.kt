package com.fitlens.companion.ui

import android.app.Application
import com.fitlens.companion.data.MeasurementGoals
import com.fitlens.companion.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicBoolean

/** The test teardown waits for saves the app started on AppScope, and can't hang (#373). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h3200dp-mdpi")
class AppScopeJobsTest : FunctionTest() {

    @Test
    fun theWaitLetsASlowJobFinish() {
        val done = AtomicBoolean(false)
        AppScope.scope.launch {
            withContext(Dispatchers.IO) { Thread.sleep(300) }
            done.set(true)
        }
        assertTrue(awaitAppScopeJobs())
        assertTrue(done.get())
    }

    @Test
    fun aJobWaitingInDelayFinishesBecauseTheClockMovesOn() {
        val done = AtomicBoolean(false)
        AppScope.scope.launch {
            delay(200)
            done.set(true)
        }
        assertTrue(awaitAppScopeJobs())
        assertTrue(done.get())
    }

    @Test
    fun aJobThatNeverEndsDoesNotHangTheWait() {
        val never = AppScope.scope.launch { awaitCancellation() }
        try {
            val started = System.currentTimeMillis()
            assertFalse(awaitAppScopeJobs(timeoutMs = 300))
            assertTrue(System.currentTimeMillis() - started < 3_000)
        } finally {
            never.cancel()
        }
    }

    @Test
    fun givingUpCancelsWhatIsLeftAndSaysHowMany() {
        val never = AppScope.scope.launch { awaitCancellation() }
        assertEquals(1, settleAppScopeJobs(timeoutMs = 300))
        assertTrue(never.isCancelled)
        assertEquals(0, settleAppScopeJobs(timeoutMs = 300))
    }

    @Test
    fun aGoalSavedOnAppScopeIsStoredOnceTheWaitReturns() {
        AppScope.scope.launch { Store.setMeasurementGoal("Bodyweight", "kg", MeasurementGoals.DECREASE, 0.0) }
        assertTrue(awaitAppScopeJobs())
        val def = stored().allMeasurements.first { it.name == "Bodyweight" }
        assertEquals(MeasurementGoals.DECREASE, def.goalType)
    }
}
