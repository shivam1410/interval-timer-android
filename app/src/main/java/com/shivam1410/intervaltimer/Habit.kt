package com.shivam1410.intervaltimer

import java.net.URLDecoder
import java.net.URLEncoder
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// Pure habit logic (no Android), unit-tested in HabitTest. Stored by Habits (HabitStore.kt).

/** One stretch of a habit program: [days] long, [sets] × [reps] (or × [holdSec] seconds when that's set). */
data class Stage(
    val days: Int,
    val sets: Int,
    val reps: Int,
    val holdSec: Int = 0,
    val note: String = "", // what changes this stage ("Lean forward and flex your quads too")
    val clip: String = "", // looping clip for this stage; empty = the previous stage's, then the item's
)

/**
 * One library item ([itemId]) done daily for the stages' total days, counted in calendar days from
 * [start]: a missed day still moves the program on. [done] holds the days you tapped Done.
 */
data class Habit(
    val id: String,
    val itemId: String,
    val start: LocalDate,
    val stages: List<Stage>,
    val remindMin: Int?, // minutes after midnight; null = no reminder
    val done: Set<LocalDate>,
    val updatedAt: Long,
    val ended: Boolean = false,
) {
    val totalDays get() = stages.sumOf { it.days }

    /** Day 1 is [start]. */
    fun dayOf(date: LocalDate) = ChronoUnit.DAYS.between(start, date).toInt() + 1

    fun stageIndex(day: Int): Int? {
        if (day < 1) return null
        var end = 0
        stages.forEachIndexed { i, s -> end += s.days; if (day <= end) return i }
        return null
    }

    fun stageOn(day: Int) = stageIndex(day)?.let(stages::get)

    /** The first day of a stage, when its note is worth pointing out. */
    fun isStageStart(day: Int): Boolean {
        val i = stageIndex(day) ?: return false
        return day == stages.take(i).sumOf { it.days } + 1
    }

    fun clipOn(day: Int, itemClip: String): String {
        val i = stageIndex(day) ?: return itemClip
        return stages.take(i + 1).lastOrNull { it.clip.isNotEmpty() }?.clip ?: itemClip
    }

    fun finished(today: LocalDate) = dayOf(today) > totalDays

    /** Done days that fall inside the program. */
    val doneCount get() = done.count { dayOf(it) in 1..totalDays }
}

/** The reminder's line for [today] ("1 × 20 today"), or null when there's nothing to remind about. */
fun reminderText(h: Habit, today: LocalDate): String? {
    if (h.ended || h.remindMin == null || today in h.done) return null
    val day = h.dayOf(today)
    val stage = h.stageOn(day) ?: return null
    val change = if (h.isStageStart(day) && day > 1 && stage.note.isNotBlank()) " · ${stage.note}" else ""
    return "${label(stage)} today$change"
}

fun label(s: Stage) = if (s.holdSec > 0) "${s.sets} × ${s.holdSec} s" else "${s.sets} × ${s.reps}"

/** Tib raises, 30 days: 10 reps, then 20, then leaning forward with quads flexed, then 2 sets. */
val TIB_RAISES = listOf(
    Stage(7, 1, 10, note = "Back against the wall, toes up and down. Shins burn: that's the tibialis anterior."),
    Stage(7, 1, 20, note = "Stairs and walks should start to feel lighter."),
    Stage(7, 1, 20, note = "New: lean forward and flex your quads too. Your knee's brake and shock absorber."),
    Stage(9, 2, 20, note = "Two sets, strong and controlled."),
)

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

private fun encodeStage(s: Stage) = listOf(s.days, s.sets, s.reps, s.holdSec, enc(s.note), enc(s.clip)).joinToString(",")

private fun decodeStage(t: String): Stage {
    val p = t.split(",")
    return Stage(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(), dec(p[4]), dec(p[5]))
}

/** One habit per line, "|"-separated; stages ";"-separated; text URL-encoded so no separator leaks. */
fun encodeHabits(all: List<Habit>): String = all.joinToString("\n") { h ->
    listOf(
        h.id, h.itemId, h.start, h.remindMin ?: -1, h.updatedAt, if (h.ended) 1 else 0,
        h.done.sorted().joinToString(","), h.stages.joinToString(";", transform = ::encodeStage),
    ).joinToString("|")
}

fun decodeHabits(text: String?): List<Habit> =
    text.orEmpty().lines().mapNotNull { line ->
        val p = line.split("|")
        if (p.size != 8) return@mapNotNull null
        runCatching {
            Habit(
                p[0], p[1], LocalDate.parse(p[2]), p[7].split(";").filter { it.isNotEmpty() }.map(::decodeStage),
                p[3].toInt().takeIf { it >= 0 }, p[6].split(",").filter { it.isNotEmpty() }.map(LocalDate::parse).toSet(),
                p[4].toLong(), p[5] == "1",
            )
        }.getOrNull()
    }

/** Newest edit of each habit wins, but a Done from either phone is kept. */
fun mergeHabits(a: List<Habit>, b: List<Habit>): List<Habit> =
    (a + b).groupBy { it.id }.values.map { same -> same.maxBy { it.updatedAt }.copy(done = same.flatMap { it.done }.toSet()) }
