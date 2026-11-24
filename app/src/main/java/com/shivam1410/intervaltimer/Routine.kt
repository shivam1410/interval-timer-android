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

/**
 * A routine step: plain text you tick, one of your habits ([habitStep] strings) done with its count-along,
 * or a timer ([actStep]) — a quick timer or one of your library items, run where the step is.
 */
sealed interface RoutineStep
data class TextStep(val text: String) : RoutineStep
data class HabitStep(val habitId: String) : RoutineStep
data class ActivityStep(val activityId: String) : RoutineStep

private const val HABIT_PREFIX = "habit:"
private const val ACT_PREFIX = "act:"

/** The stored step string for habit [id]; steps stay plain strings so storage and Drive sync don't change. */
fun habitStep(id: String) = HABIT_PREFIX + id

/** The stored step string for activity [id] ("walk", "lib:<id>"): the same plain-string trick as [habitStep]. */
fun actStep(id: String) = ACT_PREFIX + id

/** The activity a timer step runs ("walk", "lib:<id>"), or null when it is gone (a library item you deleted). */
fun stepActivity(id: String): Activity? = activity(id).takeIf { it.id == id }

fun stepOf(s: String): RoutineStep = when {
    s.startsWith(HABIT_PREFIX) -> HabitStep(s.removePrefix(HABIT_PREFIX))
    s.startsWith(ACT_PREFIX) -> ActivityStep(s.removePrefix(ACT_PREFIX))
    else -> TextStep(s)
}

fun routineSteps(r: Routine): List<RoutineStep> = r.steps.map(::stepOf)

/**
 * Where to open [r] today: the first step you still have to do. Habit steps already done today (and ones
 * whose habit is gone or ended) are behind you; a text or timer step is never tracked, so it always counts as to do.
 * Everything done gives [Routine.steps].size: the finished screen.
 */
fun firstUndoneStep(r: Routine, habits: List<Habit>, today: LocalDate): Int {
    val steps = routineSteps(r)
    val i = steps.indexOfFirst { st ->
        when (st) {
            // A text or timer step keeps no record of its own, so it is always still to do.
            is TextStep, is ActivityStep -> true
            is HabitStep -> habits.firstOrNull { it.id == st.habitId && !it.ended }?.let { today !in it.done && it.stageOn(it.dayOf(today)) != null } ?: false
        }
    }
    return if (i < 0) steps.size else i
}

/** The line under a routine's name: "5 steps · 1 stretch · about 15 min". [minutes] null = no timer. */
fun routineSummary(r: Routine, minutes: Int?): String {
    val stretches = routineSteps(r).count { it is HabitStep }
    return "${r.steps.size} ${if (r.steps.size == 1) "step" else "steps"}" +
        (if (stretches > 0) " · $stretches ${if (stretches == 1) "stretch" else "stretches"}" else "") +
        (minutes?.let { " · about $it min" } ?: "")
}

/** Habits that live in a routine (not deleted): Home's deck leaves them out. */
fun habitsInRoutines(rs: List<Routine>): Set<String> =
    rs.filterNot { it.deleted }.flatMap(::routineSteps).filterIsInstance<HabitStep>().map { it.habitId }.toSet()

/** A routine not done by this many minutes after its reminder gets one more nudge. */
const val NUDGE_MIN = 30

/** The minute [r] was finished on [today], or null. */
fun doneToday(r: Routine, today: LocalDate): Int? = r.done[today]

/** Longer than this and you were not doing the step, you were somewhere else. */
const val STEP_MAX_MS = 30 * 60_000L

/**
 * A step's typical time with [sample] folded in: the past counts double, so the figure follows how long
 * you actually take without one interrupted run becoming the new normal. A sample under a second (tapped
 * straight through) or over [STEP_MAX_MS] (you walked away) was not a measurement and changes nothing.
 *
 * Steps are keyed by what they are — "Take meds", "habit:<id>", "act:walk" — not by where they sit, so
 * reordering a routine keeps what it learnt, and a step shared by two routines is timed once.
 */
fun blendStep(old: Long?, sample: Long): Long? = when {
    sample < 1_000L || sample > STEP_MAX_MS -> old
    old == null -> sample
    else -> (old * 2 + sample) / 3
}

/**
 * How long the steps in [remaining] usually take together — or null unless every one of them has been
 * timed, because a total missing a step is not an estimate, it is an underestimate presented as one.
 */
fun stepsLeftMs(remaining: List<String>, typical: Map<String, Long>): Long? {
    if (remaining.isEmpty()) return null
    var total = 0L
    for (step in remaining) total += typical[step] ?: return null
    return total
}

/**
 * How often [r] has been done lately, on the same forgiving terms as a habit's: the window is the routine's
 * own age — from the first time it was ever done — capped at [window] days. A routine you have never done
 * measures nothing, and [Density.worthShowing] keeps anything under three finishes off the screen entirely.
 */
fun routineDensity(r: Routine, today: LocalDate, window: Int = 14): Density {
    val first = r.done.keys.minOrNull() ?: return Density(0, 0)
    val of = (java.time.temporal.ChronoUnit.DAYS.between(first, today).toInt() + 1).coerceIn(0, window)
    if (of <= 0) return Density(0, 0)
    val from = today.minusDays((of - 1).toLong())
    return Density(r.done.keys.count { !it.isBefore(from) && !it.isAfter(today) }, of)
}

/**
 * The minute [r] was finished on [today], but only while nothing in it is still to do: a stretch added
 * after you finished (today's new one, say) puts the routine back on your list. The habit steps are what
 * count, because a text step keeps no record; a habit that ended or was deleted can't be done, so it is
 * never what holds the routine open.
 */
fun routineDoneToday(r: Routine, habits: List<Habit>, today: LocalDate): Int? =
    doneToday(r, today)?.takeIf { stepsLeftToday(r, habits, today) == 0 }

/** Habit steps of [r] you could still do [today]. */
fun stepsLeftToday(r: Routine, habits: List<Habit>, today: LocalDate): Int =
    routineSteps(r).filterIsInstance<HabitStep>().count { st ->
        habits.firstOrNull { it.id == st.habitId && !it.ended }?.let { today !in it.done && it.stageOn(it.dayOf(today)) != null } == true
    }

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
