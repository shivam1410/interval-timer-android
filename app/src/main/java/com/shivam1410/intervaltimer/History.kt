package com.shivam1410.intervaltimer

import java.time.LocalDate

// Pure history logic (no Android), unit-tested in HistoryTest.

data class Day(val date: LocalDate, val workMs: Long, val breakMs: Long, val cycles: Int) {
    /** A day counts toward the streak once at least one full work block was finished. */
    val active get() = cycles > 0

    fun encode() = "$workMs,$breakMs,$cycles"

    operator fun plus(o: Day) = copy(workMs = workMs + o.workMs, breakMs = breakMs + o.breakMs, cycles = cycles + o.cycles)

    companion object {
        fun decode(date: String, v: String): Day? {
            val p = v.split(",")
            return runCatching { Day(LocalDate.parse(date), p[0].toLong(), p[1].toLong(), p[2].toInt()) }.getOrNull()
        }
    }
}

/** Current streak (today not being done yet doesn't break it) and best streak, in days. */
fun streaks(days: List<Day>, today: LocalDate): Pair<Int, Int> {
    val active = days.filter { it.active }.map { it.date }.toSortedSet()
    var current = 0
    var d = if (today in active) today else today.minusDays(1)
    while (d in active) {
        current++
        d = d.minusDays(1)
    }
    var best = 0
    var run = 0
    var prev: LocalDate? = null
    for (date in active) {
        run = if (prev != null && prev.plusDays(1) == date) run + 1 else 1
        best = maxOf(best, run)
        prev = date
    }
    return current to best
}

/** Two devices may log the same day; keep the fuller record per day instead of double-counting. */
fun merge(a: List<Day>, b: List<Day>): List<Day> =
    (a + b).groupBy { it.date }.values
        .map { same -> same.maxWith(compareBy({ it.workMs }, { it.cycles })) }
        .sortedBy { it.date }
