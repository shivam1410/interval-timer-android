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
    val demo: String = "", // built-in movement (Pose.kt DEMOS) for this stage; empty = picked from the title
)

/**
 * A habit of its own ([title]), optionally linked to a library exercise ([itemId], "" = none), done daily for the stages' total days, counted in calendar days from
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
    val title: String = "", // its own name; "" on habits made before names (they use the linked exercise's)
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

    /** The first day of a stage whose note differs from the one before: when the note is worth pointing out. */
    fun isStageStart(day: Int): Boolean {
        val i = stageIndex(day) ?: return false
        return day == stages.take(i).sumOf { it.days } + 1 && (i == 0 || stages[i - 1].note != stages[i].note)
    }

    fun clipOn(day: Int, itemClip: String): String {
        val i = stageIndex(day) ?: return itemClip
        return stages.take(i + 1).lastOrNull { it.clip.isNotEmpty() }?.clip ?: itemClip
    }

    /** Built-in movement for [day]: the stage's own, else the one the exercise's title asks for. */
    fun demoOn(day: Int, title: String): String? = stageOn(day)?.demo?.takeIf { it.isNotEmpty() } ?: demoFor(title)

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

/**
 * How often [h] has actually been done lately: the same forgiving window as the day's density, measured
 * from the habit's own start so a plan begun on Tuesday is not judged against a fortnight.
 */
fun habitDensity(h: Habit, today: LocalDate, window: Int = 14): Density {
    val of = (ChronoUnit.DAYS.between(h.start, today).toInt() + 1).coerceAtMost(window)
    if (of <= 0) return Density(0, 0)
    val from = today.minusDays((of - 1).toLong())
    return Density(h.done.count { !it.isBefore(from) && !it.isAfter(today) }, of)
}

/** The same window as [habitDensity], day by day: oldest first, today last. Empty before the habit begins. */
fun habitDays(h: Habit, today: LocalDate, window: Int = 14): List<Boolean> {
    val of = habitDensity(h, today, window).of
    return (of - 1 downTo 0).map { today.minusDays(it.toLong()) in h.done }
}

/** What to call [h]: its own name, else the linked exercise's, else "Habit". */
/** Home's deck: habits not done [today] first (in order), then today's done ones; [hidden] (in a routine) left out. */
fun habitDeck(habits: List<Habit>, today: LocalDate, hidden: Set<String> = emptySet()): List<Habit> =
    habits.filterNot { it.id in hidden }.sortedBy { today in it.done }

/**
 * A glyph for a stretch, picked from the movement it actually is. Routines carry their own emoji and
 * habits never did, so a list of both showed every stretch wearing the same leg.
 */
fun habitGlyph(h: Habit, items: List<LibraryItem>): String {
    val name = (h.title.ifBlank { items.firstOrNull { it.id == h.itemId }?.title.orEmpty() } + " " +
        h.stages.joinToString(" ") { it.demo }).lowercase()
    return when {
        "shoulder" in name || "supraspinatus" in name -> "\uD83D\uDE46"   // person gesturing
        "wall push" in name || "push" in name -> "\uD83E\uDDCD"           // person standing
        "squat" in name || "wall sit" in name || "wall-sit" in name -> "\uD83E\uDDCE"
        "glute" in name || "bridge" in name -> "\uD83C\uDF09"             // bridge
        "calf" in name || "tib" in name -> "\uD83E\uDDB5"                 // leg
        "neck" in name || "eye" in name -> "\uD83D\uDC41\uFE0F"
        "walk" in name -> "\uD83D\uDEB6"
        else -> "\uD83E\uDD38"                                             // cartwheel: a stretch
    }
}

fun habitTitle(h: Habit, items: List<LibraryItem>): String =
    h.title.ifBlank { items.firstOrNull { it.id == h.itemId && !it.deleted }?.title ?: "Habit" }

fun label(s: Stage) = when {
    s.holdSec > 0 && s.reps > 0 -> "${s.sets} × ${s.reps} · ${s.holdSec} s hold" // reps, each held (isometrics)
    s.holdSec > 0 -> "${s.sets} × ${s.holdSec} s"
    else -> "${s.sets} × ${s.reps}"
}

/** Template: the same target every day for [days]. */
fun sameEveryDay(days: Int, sets: Int, reps: Int, holdSec: Int = 0) = listOf(Stage(days, sets, reps, holdSec))

/** Template: one stage per 7 days, [start] growing by [add] each week (seconds held when [holdSec]); the last week may be short. */
fun growWeekly(days: Int, sets: Int, start: Int, add: Int, holdSec: Boolean = false): List<Stage> =
    (0 until days step 7).mapIndexed { week, from ->
        val n = start + add * week
        Stage(minOf(7, days - from), sets, if (holdSec) 0 else n, if (holdSec) n else 0)
    }

/**
 * A calendar edit: [day]'s target (or every day from it on) becomes [sets] × [reps] / [holdSec] seconds.
 * Days keep their note, clip and movement; neighbouring identical days merge back into one stage.
 */
fun setFrom(h: Habit, day: Int, sets: Int, reps: Int, holdSec: Int, onlyThisDay: Boolean): List<Stage> {
    val daily = h.stages.flatMap { s -> List(s.days) { s.copy(days = 1) } }
    val edited = daily.mapIndexed { i, s ->
        val d = i + 1
        if (d == day || (!onlyThisDay && d > day)) s.copy(sets = sets, reps = reps, holdSec = holdSec) else s
    }
    return edited.fold(emptyList()) { acc, s ->
        val last = acc.lastOrNull()
        if (last != null && last.copy(days = 1) == s) acc.dropLast(1) + last.copy(days = last.days + 1) else acc + s
    }
}

/** Tib raises, 30 days: 10 reps, then 20, then leaning forward with quads flexed, then 2 sets. */
val TIB_RAISES = listOf(
    Stage(7, 1, 10, note = "Back against the wall, toes up and down. Shins burn: that's the tibialis anterior."),
    Stage(7, 1, 20, note = "Stairs and walks should start to feel lighter."),
    Stage(7, 1, 20, note = "New: lean forward and flex your quads too. Your knee's brake and shock absorber.", demo = "tib-raise-lean"),
    Stage(9, 2, 20, note = "Two sets, strong and controlled.", demo = "tib-raise-lean"),
)

private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

private fun encodeStage(s: Stage) = listOf(s.days, s.sets, s.reps, s.holdSec, enc(s.note), enc(s.clip), enc(s.demo)).joinToString(",")

private fun decodeStage(t: String): Stage {
    val p = t.split(",")
    return Stage(p[0].toInt(), p[1].toInt(), p[2].toInt(), p[3].toInt(), dec(p[4]), dec(p[5]), p.getOrNull(6)?.let(::dec).orEmpty()) // 6 fields = before demos
}

/** One habit per line, "|"-separated; stages ";"-separated; text URL-encoded so no separator leaks. */
fun encodeHabits(all: List<Habit>): String = all.joinToString("\n") { h ->
    listOf(
        h.id, h.itemId, h.start, h.remindMin ?: -1, h.updatedAt, if (h.ended) 1 else 0,
        h.done.sorted().joinToString(","), h.stages.joinToString(";", transform = ::encodeStage), enc(h.title),
    ).joinToString("|")
}

fun decodeHabits(text: String?): List<Habit> =
    text.orEmpty().lines().mapNotNull { line ->
        val p = line.split("|")
        if (p.size != 8 && p.size != 9) return@mapNotNull null // 8 = before habits had names
        runCatching {
            Habit(
                p[0], p[1], LocalDate.parse(p[2]), p[7].split(";").filter { it.isNotEmpty() }.map(::decodeStage),
                p[3].toInt().takeIf { it >= 0 }, p[6].split(",").filter { it.isNotEmpty() }.map(LocalDate::parse).toSet(),
                p[4].toLong(), p[5] == "1", p.getOrNull(8)?.let(::dec).orEmpty(),
            )
        }.getOrNull()
    }

/**
 * When each habit was finished on one day, as "<id>=<minute of day>", comma-separated. Kept in a store of
 * its own rather than as a tenth field on the habit line: [decodeHabits] drops any line whose field count
 * it does not recognise, so a build older than this one would lose the whole habit, history and all, to
 * gain a time it cannot show. Losing the times is survivable; losing the habits is not.
 */
fun encodeTimes(byId: Map<String, Int>): String = byId.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }

fun decodeTimes(text: String?): Map<String, Int> =
    text.orEmpty().split(",").mapNotNull { part ->
        val i = part.indexOf('=')
        if (i <= 0) null else part.substring(i + 1).toIntOrNull()?.let { part.substring(0, i) to it }
    }.toMap()

/** Newest edit of each habit wins, but a Done from either phone is kept. */
fun mergeHabits(a: List<Habit>, b: List<Habit>): List<Habit> =
    (a + b).groupBy { it.id }.values.map { same -> same.maxBy { it.updatedAt }.copy(done = same.flatMap { it.done }.toSet()) }

/** Every clip something still uses: library exercises (not deleted) and habit stages. */
fun clipsWanted(items: List<LibraryItem>, habits: List<Habit>): Set<String> =
    (items.filterNot { it.deleted }.map { it.clip } + habits.flatMap { h -> h.stages.map { it.clip } }).filter { it.isNotEmpty() }.toSet()

data class ClipSync(val upload: Set<String>, val download: Set<String>, val deleteRemote: Set<String>)

/**
 * What to move for clips, after the library and habits are merged: upload what only this phone has,
 * download what only Drive has, remove from Drive what nothing uses. Unused local files stay (a clip
 * picked in a dialog that isn't saved yet looks unused).
 */
fun clipSync(wanted: Set<String>, local: Set<String>, drive: Set<String>) =
    ClipSync(upload = wanted intersect local - drive, download = wanted intersect drive - local, deleteRemote = drive - wanted)

/** Where "Count with me" is: set [set] of [sets], [done] of [of] reps (or seconds held), resting, or finished. */
data class Count(
    val set: Int, val done: Int, val of: Int, val sets: Int,
    val resting: Boolean = false, val restLeftSec: Int = 0, val finished: Boolean = false,
    val setMs: Long = 0, // ms into the current set: drives the figure in step with the count
    val holdLeftSec: Int = 0, // reps held for seconds: seconds left in this rep's hold, 0 while relaxing
)

/** Reps held for seconds: the pause after each hold before the next rep. */
const val RELAX_MS = 1_000

/** The count [ms] after starting [s]: a rep every [repMs] (one loop of its movement), a second per held second, [restMs] between sets. */
fun countAt(ms: Long, s: Stage, repMs: Int, restMs: Int): Count {
    val repHold = s.holdSec > 0 && s.reps > 0
    val of = if (s.holdSec > 0 && !repHold) s.holdSec else s.reps
    val unit = when { repHold -> s.holdSec * 1000L + RELAX_MS; s.holdSec > 0 -> 1000L; else -> repMs.toLong() }
    val setLen = of * unit
    var t = ms
    for (set in 1..s.sets) {
        if (t < setLen) {
            val holdLeft = if (repHold) ((s.holdSec * 1000L - t % unit + 999) / 1000).toInt().coerceAtLeast(0) else 0
            return Count(set, (t / unit).toInt(), of, s.sets, setMs = t, holdLeftSec = holdLeft)
        }
        t -= setLen
        if (set == s.sets) break
        if (t < restMs) return Count(set, of, of, s.sets, resting = true, restLeftSec = ((restMs - t + 999) / 1000).toInt())
        t -= restMs
    }
    return Count(s.sets, of, of, s.sets, finished = true)
}

/** When the shared habit alarm should next go off: the soonest daily reminder among running habits, or null. */
fun nextReminderAt(habits: List<Habit>, now: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): Long? =
    habits.filter { !it.ended }.mapNotNull { it.remindMin }.minOfOrNull { nextDaily(now, it, zone) }

/** The running habits whose reminder is for exactly [at] (several can share a time). */
fun dueAt(habits: List<Habit>, at: Long, zone: java.util.TimeZone = java.util.TimeZone.getDefault()): List<Habit> =
    habits.filter { h -> !h.ended && h.remindMin?.let { nextDaily(at - 1, it, zone) == at } == true }
