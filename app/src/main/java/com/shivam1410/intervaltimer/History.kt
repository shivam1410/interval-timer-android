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

/**
 * How often you showed up lately: [done] active days out of the [of] you have had the app.
 * Density rather than a streak, because one missed Tuesday should cost nothing.
 */
data class Density(val done: Int, val of: Int) {
    /** Two finishes are a tally, not a pattern — below that there is nothing worth printing. */
    val worthShowing get() = done >= 3
}

/** [window] days back from [today], or the whole life of the record when that is shorter. */
fun density(all: List<Day>, today: LocalDate, window: Int = 14): Density {
    val first = all.map { it.date }.minOrNull() ?: return Density(0, 0)
    val of = (java.time.temporal.ChronoUnit.DAYS.between(first, today).toInt() + 1).coerceIn(1, window)
    val from = today.minusDays((of - 1).toLong())
    return Density(all.count { it.active && !it.date.isBefore(from) && !it.date.isAfter(today) }, of)
}

/** Two devices may log the same day; keep the fuller record per day instead of double-counting. */
fun merge(a: List<Day>, b: List<Day>): List<Day> =
    (a + b).groupBy { it.date }.values
        .map { same -> same.maxWith(compareBy({ it.workMs }, { it.cycles })) }
        .sortedBy { it.date }

/** Ambient listening is logged per phone ("2026-10-18|<phoneId>" -> ms), so merging two copies takes the larger value per key. */
fun mergeAmbient(a: Map<String, Long>, b: Map<String, Long>): Map<String, Long> =
    (a.keys + b.keys).associateWith { maxOf(a[it] ?: 0, b[it] ?: 0) }

/** Per-day totals across phones; malformed keys are skipped. */
fun ambientByDate(raw: Map<String, Long>): Map<LocalDate, Long> =
    raw.entries.mapNotNull { (k, v) -> runCatching { LocalDate.parse(k.substringBefore('|')) }.getOrNull()?.let { it to v } }
        .groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.sum() }
