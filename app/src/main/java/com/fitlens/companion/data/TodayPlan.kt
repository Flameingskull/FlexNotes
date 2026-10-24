package com.fitlens.companion.data

/**
 * Today's workout as the workout reminder (#219) and the home-screen widget (#220) name it: the day already logged
 * from a workout on [TodayWorkout.date], or else the day of the current workout that comes next.
 *
 * The current workout is the one chosen in the library's switcher ([PortableSettings.lastRoutineId]), else one whose
 * training blocks run that day, else the workout logged most recently. Null when there's no workout with days.
 */
data class TodayWorkout(
    val date: String,
    val routine: Routine,
    val day: RoutineDay,
    /** True when [day] was already logged on [date]. */
    val logged: Boolean,
    /** The training block running on [date], when the workout has blocks (#218). */
    val block: BlockState?
)

object TodayPlan {
    fun workout(snap: Snapshot, prefs: PortableSettings, date: String): TodayWorkout? {
        // Already logged from a workout today: that day, while its sets are still there.
        snap.workoutOrigins[date]?.takeIf { snap.setsByDate.containsKey(date) }?.let { o ->
            val found = o.routineDayId?.let { Routines.dayById(snap, it) }
            if (found != null) return TodayWorkout(date, found.first, found.second, logged = true, block = blockOn(found.first, date))
        }
        val withDays = snap.routines.filter { it.days.isNotEmpty() }
        if (withDays.isEmpty()) return null
        val routine = withDays.firstOrNull { it.id == prefs.lastRoutineId }
            ?: withDays.firstOrNull { r -> r.plan?.let { TrainingBlocks.on(it, date) } != null }
            ?: snap.workoutOrigins.values
                .filter { o -> snap.setsByDate.containsKey(o.date) }
                .sortedByDescending { it.date }
                .firstNotNullOfOrNull { o -> withDays.firstOrNull { it.id == o.workoutId } }
            ?: return null
        val day = Routines.nextDay(snap, routine) ?: return null
        return TodayWorkout(date, routine, day, logged = false, block = blockOn(routine, date))
    }

    private fun blockOn(routine: Routine, date: String): BlockState? = routine.plan?.let { TrainingBlocks.on(it, date) }
}
