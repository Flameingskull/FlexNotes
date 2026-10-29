package com.fitlens.companion.data

/**
 * The decisions behind saving a set on the exercise screen (#305), kept apart from `SetEntryScreen` so they can be
 * tested. Plain values in, a plain answer out: no Compose, no Settings and no Store reads, so a caller passes in what
 * it has read. The screen keeps the side effects (navigation, messages, timers, writes).
 */
object SetEntryRules {
    /** A session shorter or longer than this (15 and 240 minutes) is asked about once before it is saved (#243). */
    const val SESSION_MIN_SEC = 15 * 60
    const val SESSION_MAX_SEC = 240 * 60

    /**
     * Nothing worth saving is entered. A Session or Rounds set ([timed]) needs a length or an effort; any other set
     * needs a weight, reps, distance, time or a custom metric (0 is a real metric value, blank is not, #14).
     */
    fun nothingToSave(timed: Boolean, hasEffort: Boolean, weightKg: Double, reps: Int, distance: Double, durationSec: Int, hasMetric: Boolean): Boolean =
        if (timed) durationSec == 0 && !hasEffort
        else weightKg == 0.0 && reps == 0 && distance == 0.0 && durationSec == 0 && !hasMetric

    /** A Session's hard or live time can't be longer than the session itself (#240). Rounds have their own check. */
    fun hardLongerThanSession(rounds: Boolean, liveSec: Int?, durationSec: Int): Boolean =
        !rounds && liveSec != null && liveSec > durationSec

    /**
     * Whether a Session or Rounds length is unusual enough to ask "Is that right?" once (#243). Under 15 or over 240
     * minutes is unusual; Rounds are short by nature, so only a total over 240 minutes is. Not asked again once
     * [confirmed], for no length at all, or when an edit leaves a saved set's length unchanged ([previousDurationSec]
     * is null for a new set). Nothing is ever blocked.
     */
    fun isUnusualLength(timed: Boolean, rounds: Boolean, durationSec: Int, previousDurationSec: Int?, confirmed: Boolean): Boolean {
        if (!timed || confirmed || durationSec <= 0 || previousDurationSec == durationSec) return false
        return if (rounds) durationSec > SESSION_MAX_SEC else durationSec < SESSION_MIN_SEC || durationSec > SESSION_MAX_SEC
    }

    /** Where a saved set leaves a superset: [nextExercise] opens next (null outside a group), [endOfRound] starts the rest. */
    class SupersetStep(val endOfRound: Boolean, val nextExercise: Long?)

    /**
     * Saving a set in a superset moves on to the next exercise of the group, round-robin, as FitNotes does (#18). The
     * rest timer starts only after the round's last exercise (#20). With no group, or a group of one, the rest starts
     * and nothing moves on. An exercise missing from its [members] never starts the rest or moves on.
     */
    fun supersetStep(members: List<Long>, current: Long): SupersetStep {
        val at = members.indexOf(current)
        val endOfRound = members.size < 2 || at == members.lastIndex
        val next = if (members.size > 1 && at >= 0) members[(at + 1) % members.size] else null
        return SupersetStep(endOfRound, next)
    }

    /**
     * The first set of today can start the workout timer (#12), when Settings turns that on and no time is already
     * recorded for the day.
     */
    fun startsWorkoutTimer(firstOfDay: Boolean, isToday: Boolean, autoSetting: Boolean, alreadyTimed: Boolean): Boolean =
        firstOfDay && isToday && autoSetting && !alreadyTimed

    /**
     * With auto-select next on, saving an edit selects the following set of the day, ready to adjust (#97). [setIds]
     * are the day's sets of the exercise in order; null at the last set, with the setting off, or for an unknown set.
     */
    fun nextSelected(setIds: List<Long>, savedId: Long, autoSelectNext: Boolean): Long? {
        if (!autoSelectNext) return null
        val at = setIds.indexOf(savedId)
        return if (at < 0) null else setIds.getOrNull(at + 1)
    }
}
