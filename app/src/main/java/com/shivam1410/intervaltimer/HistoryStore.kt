package com.shivam1410.intervaltimer

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One SharedPreferences entry per day ("2026-10-07" -> "workMs,breakMs,cycles"), plus that day's
 * timeline (work/break stretches) in a second file. Both live in shared prefs, so Android's Google
 * backup carries them with the settings.
 */
object History {
    val days = MutableStateFlow<List<Day>>(emptyList())
    val timelines = MutableStateFlow<Map<LocalDate, List<Segment>>>(emptyMap())
    private lateinit var app: Context
    private val prefs get() = app.getSharedPreferences("history", Context.MODE_PRIVATE)
    private val timelinePrefs get() = app.getSharedPreferences("timeline", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        app = ctx.applicationContext
        days.value = prefs.all.mapNotNull { (k, v) -> (v as? String)?.let { Day.decode(k, it) } }.sortedBy { it.date }
        timelines.value = timelinePrefs.all.mapNotNull { (k, v) ->
            runCatching { LocalDate.parse(k) }.getOrNull()?.let { it to decodeSegments(v as? String) }
        }.toMap()
    }

    /** Adds a finished (or ended) session to the day it started on. */
    fun record(startedAt: Long, s: Session) {
        if (s.workMs + s.breakMs <= 0) return
        val date = dateOf(startedAt)
        val add = Day(date, s.workMs, s.breakMs, s.cycles)
        val merged = days.value.firstOrNull { it.date == date }?.plus(add) ?: add
        prefs.edit().putString(date.toString(), merged.encode()).apply()
        days.value = (days.value.filter { it.date != date } + merged).sortedBy { it.date }
        if (s.log.isNotEmpty()) replaceTimelines(mergeTimelines(timelines.value, mapOf(date to s.log)))
        Drive.sync()
    }

    /** After merging with the Drive copy. */
    fun replaceAll(all: List<Day>) {
        prefs.edit().apply { all.forEach { putString(it.date.toString(), it.encode()) } }.apply()
        days.value = all
    }

    /** After recording a session or merging with the Drive copy. */
    fun replaceTimelines(all: Map<LocalDate, List<Segment>>) {
        timelinePrefs.edit().apply { all.forEach { (date, segs) -> putString(date.toString(), encodeSegments(segs)) } }.apply()
        timelines.value = all
    }

    /** The day's stretches, plus the running session's when it started that day (so today is live). */
    fun timeline(date: LocalDate, live: Session, plan: List<Phase>, now: Long): List<Segment> {
        val running = live.running(plan) && dateOf(live.startedAt) == date
        val open = if (running) close(plan, live, now).log else emptyList()
        return (timelines.value[date].orEmpty() + open).distinct().sortedBy(Segment::start)
    }

    fun dateOf(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    fun today(): LocalDate = LocalDate.now()
}
