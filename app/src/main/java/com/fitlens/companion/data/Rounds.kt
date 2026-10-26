package com.fitlens.companion.data

import android.content.res.Resources
import com.fitlens.companion.R
import kotlin.math.max
import kotlin.math.min

/**
 * The sums behind a Rounds set (#246), a Session-shaped entry: sparring, bag work or rolls in timed rounds. A set
 * stores the **rounds** in `reps`, the **rest between rounds** in `restSeconds`, the **total time** in `durationSec` and
 * the **hard rounds' time** in `liveSec` (hard rounds times the round length), so everything that reads a Session's
 * length and hard minutes (training load, calories, graphs, exports) reads a Rounds set unchanged. The round length and
 * the number of hard rounds aren't stored: they're worked out here, always from the same three numbers.
 */
object Rounds {
    /** Total time: the rounds plus the rests between them (no rest after the last round). 0 without rounds or a length. */
    fun totalSec(rounds: Int, roundSec: Int, restSec: Int): Int =
        if (rounds <= 0 || roundSec <= 0) 0 else rounds * roundSec + (rounds - 1) * max(0, restSec)

    /** One round's length from the stored total, as [totalSec] built it. 0 without rounds. */
    fun roundSec(rounds: Int, totalSec: Int, restSec: Int?): Int {
        if (rounds <= 0 || totalSec <= 0) return 0
        val work = totalSec - (rounds - 1) * max(0, restSec ?: 0)
        return max(0, Math.round(work.toDouble() / rounds).toInt())
    }

    /** One round's length in a stored set. */
    fun roundSec(s: SetRow): Int = roundSec(s.reps, s.durationSec, s.restSeconds)

    /** One round's length in a predefined set. */
    fun roundSec(s: PlannedSet): Int = roundSec(s.reps, s.durationSec, s.restSeconds)

    /** How many rounds were hard, from the stored hard time; never more than [rounds]. */
    fun hardRounds(rounds: Int, liveSec: Int?, roundSec: Int): Int =
        if (rounds <= 0 || roundSec <= 0 || liveSec == null) 0
        else min(rounds, Math.round(liveSec.toDouble() / roundSec).toInt())

    fun hardRounds(s: SetRow): Int = hardRounds(s.reps, s.liveSec, roundSec(s))

    fun hardRounds(s: PlannedSet): Int = hardRounds(s.reps, s.liveSec, roundSec(s))

    /** The hard time to store for [hardRounds] hard rounds (never more than [rounds]); null when there are none. */
    fun hardSec(rounds: Int, hardRounds: Int, roundSec: Int): Int? =
        (min(max(0, hardRounds), max(0, rounds)) * max(0, roundSec)).takeIf { it > 0 }

    /** A length of time as read in a Rounds line: "3 min", "2:30 min" or "45 s". */
    fun timeText(res: Resources, sec: Int): String = when {
        sec < 60 -> res.getString(R.string.rounds_time_s, sec)
        sec % 60 == 0 -> res.getString(R.string.rounds_time_min, sec / 60)
        else -> res.getString(R.string.rounds_time_min_s, sec / 60, sec % 60)
    }

    /** "6 × 3 min · 1 min rest · 2 hard": a Rounds set's rounds, their length, the rest between them and the hard ones. */
    fun lineParts(res: Resources, rounds: Int, roundSec: Int, restSec: Int?, hardRounds: Int): List<String> = listOfNotNull(
        res.getString(R.string.rounds_line_head, rounds, timeText(res, roundSec)).takeIf { rounds > 0 && roundSec > 0 },
        res.getString(R.string.rounds_line_rest, timeText(res, restSec ?: 0)).takeIf { (restSec ?: 0) > 0 && rounds > 1 },
        res.getString(R.string.rounds_line_hard, hardRounds).takeIf { hardRounds > 0 }
    )
}
