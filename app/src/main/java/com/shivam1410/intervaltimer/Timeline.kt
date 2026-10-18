package com.shivam1410.intervaltimer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Pure timeline logic (no Android), unit-tested in TimelineTest. Drawn as the day graph in DayUi.

/**
 * One uninterrupted stretch of a session: work or break, whether it ran past the planned end, and for
 * break activities which one ([label] = activity id, "" for prepare / work / older records).
 */
data class Segment(val start: Long, val end: Long, val work: Boolean, val over: Boolean = false, val label: String = "") {
    val ms get() = end - start
}

/** "start,end,W;start,end,B+,nsdr" (+ = overtime, then the activity id when there is one). */
fun encodeSegments(segs: List<Segment>): String =
    segs.joinToString(";") { "${it.start},${it.end},${if (it.work) "W" else "B"}${if (it.over) "+" else ""}${if (it.label.isNotEmpty()) "," + it.label else ""}" }

fun decodeSegments(text: String?): List<Segment> =
    text.orEmpty().split(";").filter { it.isNotBlank() }.mapNotNull { item ->
        val p = item.split(",")
        val start = p.getOrNull(0)?.toLongOrNull()
        val end = p.getOrNull(1)?.toLongOrNull()
        val kind = p.getOrNull(2)
        if (start == null || end == null || kind == null || kind.isEmpty() || kind[0] !in "WB") null
        else Segment(start, end, work = kind[0] == 'W', over = kind.endsWith("+"), label = p.getOrNull(3).orEmpty())
    }

/** Splits the run [from, to) at [overFrom] (the planned end of a phase that holds at 0), if it crosses it. */
fun splitRun(work: Boolean, from: Long, to: Long, overFrom: Long?, label: String = ""): List<Segment> = when {
    to <= from -> emptyList()
    overFrom == null || overFrom >= to -> listOf(Segment(from, to, work, label = label))
    overFrom <= from -> listOf(Segment(from, to, work, over = true, label = label))
    else -> listOf(Segment(from, overFrom, work, label = label), Segment(overFrom, to, work, over = true, label = label))
}

/** Break time per activity id across [segs] (unlabelled stretches, e.g. prepare, left out). */
fun activityTotals(segs: List<Segment>): Map<String, Long> =
    segs.filter { !it.work && it.label.isNotEmpty() }.groupBy { it.label }.mapValues { (_, v) -> v.sumOf { it.ms } }

/** Each phone logs its own sessions, so the union (minus copies already synced both ways) is the full day. */
fun mergeTimelines(a: Map<LocalDate, List<Segment>>, b: Map<LocalDate, List<Segment>>): Map<LocalDate, List<Segment>> =
    (a.keys + b.keys).associateWith { (a[it].orEmpty() + b[it].orEmpty()).distinct().sortedBy(Segment::start) }

/** The whole hours the day graph spans: from the hour of the first start to the hour after the last end. */
fun hourSpan(segs: List<Segment>, zone: ZoneId): Pair<Long, Long> {
    fun hour(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).truncatedTo(ChronoUnit.HOURS)
    val from = hour(segs.minOf { it.start })
    val lastEnd = segs.maxOf { it.end }
    val endHour = hour(lastEnd)
    val to = if (endHour.toInstant().toEpochMilli() == lastEnd) endHour else endHour.plusHours(1)
    return from.toInstant().toEpochMilli() to to.toInstant().toEpochMilli()
}

/** Joins back-to-back stretches of the same kind (e.g. a break's prepare and its activity) for display. */
fun joined(segs: List<Segment>): List<Segment> = segs.fold(emptyList()) { acc, s ->
    val last = acc.lastOrNull()
    if (last != null && last.end == s.start && last.work == s.work && last.over == s.over) acc.dropLast(1) + last.copy(end = s.end)
    else acc + s
}

/**
 * Drops stretches under a minute, left behind by skipping through unused slots; [keepLast] keeps
 * the last one when it is still running, so today's graph doesn't wait a minute to show it.
 */
fun dropBlips(segs: List<Segment>, keepLast: Boolean): List<Segment> =
    segs.filterIndexed { i, s -> s.ms >= MIN || (keepLast && i == segs.lastIndex) }
