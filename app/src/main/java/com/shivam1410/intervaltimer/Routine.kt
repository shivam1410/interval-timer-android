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
    val minutes: Int? = null, // how long it should take; null = no timer, just the steps
) {
    /**
     * A timed routine runs on the quick-timer engine, so it is an activity like NSDR or a library item
     * ("routine:<id>", registered by [Routines]). An untimed one still has a length here, for when you
     * pick a timer on the start card without saving it to the routine.
     */
    fun toActivity() = Activity(
        id = "routine:$id", emoji = emoji, name = name, group = "Routine",
        hint = "${steps.size} steps", quickMin = minutes ?: DEFAULT_ROUTINE_MIN,
    )
}

/** The length a routine's timer offers when none was picked. */
const val DEFAULT_ROUTINE_MIN = 15

/** Shipped with the app; fixed ids so both phones' copies merge, updatedAt 0 so any edit of yours wins. */
val DEFAULT_ROUTINES = listOf(
    Routine("morning", "☀️", "Morning", listOf("Take meds", "Drink a glass of water", "Brush teeth", "Eat breakfast"), null, emptyMap(), 0),
    Routine("leaving", "🚪", "Leaving the house", listOf("Keys", "Wallet", "Phone", "Earbuds", "Water bottle"), null, emptyMap(), 0),
    Routine("evening", "🌙", "Evening", listOf("Charge phone", "Clothes out for tomorrow", "Lights off", "Phone away from bed"), null, emptyMap(), 0),
)

/** A routine step: plain text you tick, or one of your habits ([habitStep] strings), done with its count-along. */
sealed interface RoutineStep
data class TextStep(val text: String) : RoutineStep
data class HabitStep(val habitId: String) : RoutineStep

private const val HABIT_PREFIX = "habit:"

/** The stored step string for habit [id]; steps stay plain strings so storage and Drive sync don't change. */
fun habitStep(id: String) = HABIT_PREFIX + id

fun stepOf(s: String): RoutineStep = if (s.startsWith(HABIT_PREFIX)) HabitStep(s.removePrefix(HABIT_PREFIX)) else TextStep(s)

fun routineSteps(r: Routine): List<RoutineStep> = r.steps.map(::stepOf)

/**
 * Where to open [r] today: the first step you still have to do. Habit steps already done today (and ones
 * whose habit is gone or ended) are behind you; a text step is never tracked, so it always counts as to do.
 * Everything done gives [Routine.steps].size: the finished screen.
 */
fun firstUndoneStep(r: Routine, habits: List<Habit>, today: LocalDate): Int {
    val steps = routineSteps(r)
    val i = steps.indexOfFirst { st ->
        when (st) {
            is TextStep -> true
            is HabitStep -> habits.firstOrNull { it.id == st.habitId && !it.ended }?.let { today !in it.done } ?: false
        }
    }
    return if (i < 0) steps.size else i
}

/** Habits that live in a routine (not deleted): Home's deck leaves them out. */
fun habitsInRoutines(rs: List<Routine>): Set<String> =
    rs.filterNot { it.deleted }.flatMap(::routineSteps).filterIsInstance<HabitStep>().map { it.habitId }.toSet()

/** A routine not done by this many minutes after its reminder gets one more nudge. */
const val NUDGE_MIN = 30

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
        r.minutes ?: -1,
    ).joinToString("|")
}

fun decodeRoutines(text: String?): List<Routine> =
    text.orEmpty().lines().mapNotNull { line ->
        val p = line.split("|")
        if (p.size != 8 && p.size != 9) return@mapNotNull null // 8 = written before routines could be timed
        runCatching {
            Routine(
                p[0], dec(p[1]), dec(p[2]), p[3].split(";").filter { it.isNotEmpty() }.map(::dec), p[4].toInt().takeIf { it >= 0 },
                p[5].split(",").filter { it.isNotEmpty() }.associate { e -> e.split("=").let { LocalDate.parse(it[0]) to it[1].toInt() } },
                p[6].toLong(), p[7] == "1", p.getOrNull(8)?.toInt()?.takeIf { it > 0 },
            )
        }.getOrNull()
    }

/** Newest edit of each routine wins; a finish from either phone is kept (the earlier time if both). */
fun mergeRoutines(a: List<Routine>, b: List<Routine>): List<Routine> =
    (a + b).groupBy { it.id }.values.map { same ->
        val done = same.flatMap { it.done.entries }.groupBy({ it.key }, { it.value }).mapValues { (_, v) -> v.min() }
        same.maxBy { it.updatedAt }.copy(done = done)
    }

/** When the routine alarm should next go off: the soonest reminder among routines, or null. */
fun nextRoutineAt(rs: List<Routine>, now: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Long? =
    rs.filter { !it.deleted }.flatMap { r ->
        val m = r.remindMin ?: return@flatMap emptyList()
        val nudge = nextDaily(now, nudgeMin(m), zone).takeIf { dayOf(it, zone) !in r.done }
        listOfNotNull(nextDaily(now, m, zone), nudge)
    }.minOrNull()

// ponytail: a reminder after 23:30 nudges just after midnight and checks that new day's "done"; fine for one user.
private fun nudgeMin(m: Int) = (m + NUDGE_MIN) % (24 * 60)

private fun dayOf(at: Long, zone: java.util.TimeZone) = java.time.Instant.ofEpochMilli(at).atZone(zone.toZoneId()).toLocalDate()

/** Routines whose nudge ([NUDGE_MIN] after the reminder) is for exactly [at] and that aren't done that day. */
fun routineNudgeDue(rs: List<Routine>, at: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): List<Routine> =
    rs.filter { r -> !r.deleted && dayOf(at, zone) !in r.done && r.remindMin?.let { nextDaily(at - 1, nudgeMin(it), zone) == at } == true }

/** Routines whose reminder is for exactly [at], skipping deleted ones and ones already finished that day. */
fun routineDue(rs: List<Routine>, at: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): List<Routine> {
    val day = java.time.Instant.ofEpochMilli(at).atZone(zone.toZoneId()).toLocalDate()
    return rs.filter { r -> !r.deleted && day !in r.done && r.remindMin?.let { nextDaily(at - 1, it, zone) == at } == true }
}

/** One time on Settings › Habits & routines: the routines and the habits of their own set for [min] (null = Anytime). */
data class Slot(val min: Int?, val routines: List<Routine>, val habits: List<Habit>)

/** Routines and running habits grouped by their reminder time, earliest first, Anytime last; habits in a routine show inside it. */
fun daySlots(habits: List<Habit>, routines: List<Routine>): List<Slot> {
    val live = routines.filterNot { it.deleted }
    val inside = habitsInRoutines(live)
    val loose = habits.filter { !it.ended && it.id !in inside }
    val times = (live.map { it.remindMin } + loose.map { it.remindMin }).distinct().sortedBy { it ?: Int.MAX_VALUE }
    return times.map { m -> Slot(m, live.filter { it.remindMin == m }, loose.filter { it.remindMin == m }) }
}

/** Days in a row [r] was finished, up to [today] (or up to yesterday while today isn't done yet). */
fun routineStreak(r: Routine, today: LocalDate): Int {
    var d = if (today in r.done) today else today.minusDays(1)
    var n = 0
    while (d in r.done) { n++; d = d.minusDays(1) }
    return n
}

/** How many of the last [days] days (today included) [r] was finished. */
fun doneInLast(r: Routine, today: LocalDate, days: Int): Int = r.done.keys.count { !it.isAfter(today) && it.isAfter(today.minusDays(days.toLong())) }
