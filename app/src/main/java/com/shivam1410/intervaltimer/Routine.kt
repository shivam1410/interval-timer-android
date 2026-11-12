package com.shivam1410.intervaltimer

import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate

// Routines: a named list of short steps done one at a time (Morning, Leaving the house, Evening…).
// Pure logic, unit-tested in RoutineTest; stored by Routines (RoutineStore.kt).

/** [done] maps each day it was finished to the minute of day (so "did I already?" has an answer). */
data class Routine(
    val id: String,
    val emoji: String,
    val name: String,
    val steps: List<String>,
    val remindMin: Int?, // daily reminder, minutes after midnight; null = none
    val done: Map<LocalDate, Int>,
    val updatedAt: Long,
    val deleted: Boolean = false,
)

/** Shipped with the app; fixed ids so both phones' copies merge, updatedAt 0 so any edit of yours wins. */
val DEFAULT_ROUTINES = listOf(
    Routine("morning", "☀️", "Morning", listOf("Take meds", "Drink a glass of water", "Brush teeth", "Eat breakfast"), null, emptyMap(), 0),
    Routine("leaving", "🚪", "Leaving the house", listOf("Keys", "Wallet", "Phone", "Earbuds", "Water bottle"), null, emptyMap(), 0),
    Routine("evening", "🌙", "Evening", listOf("Charge phone", "Clothes out for tomorrow", "Lights off", "Phone away from bed"), null, emptyMap(), 0),
)

/** The minute [r] was finished on [today], or null. */
fun doneToday(r: Routine, today: LocalDate): Int? = r.done[today]

/** 494 -> "8:14". */
fun fmtMinute(m: Int) = "%d:%02d".format(m / 60, m % 60)

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

/** One routine per line, "|"-separated; steps ";"-separated and every text URL-encoded, so no separator leaks. */
fun encodeRoutines(all: List<Routine>): String = all.joinToString("\n") { r ->
    listOf(
        r.id, enc(r.emoji), enc(r.name), r.steps.joinToString(";", transform = ::enc), r.remindMin ?: -1,
        r.done.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }, r.updatedAt, if (r.deleted) 1 else 0,
    ).joinToString("|")
}

fun decodeRoutines(text: String?): List<Routine> =
    text.orEmpty().lines().mapNotNull { line ->
        val p = line.split("|")
        if (p.size != 8) return@mapNotNull null
        runCatching {
            Routine(
                p[0], dec(p[1]), dec(p[2]), p[3].split(";").filter { it.isNotEmpty() }.map(::dec), p[4].toInt().takeIf { it >= 0 },
                p[5].split(",").filter { it.isNotEmpty() }.associate { e -> e.split("=").let { LocalDate.parse(it[0]) to it[1].toInt() } },
                p[6].toLong(), p[7] == "1",
            )
        }.getOrNull()
    }

/** Newest edit of each routine wins; a finish from either phone is kept (the earlier time if both). */
fun mergeRoutines(a: List<Routine>, b: List<Routine>): List<Routine> =
    (a + b).groupBy { it.id }.values.map { same ->
        val done = same.flatMap { it.done.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, v) -> v.min() }
        same.maxBy { it.updatedAt }.copy(done = done)
    }
