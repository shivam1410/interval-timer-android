package com.shivam1410.intervaltimer

// Pure nudge logic (no Android), unit-tested in NudgeTest. NudgeService reads usage and posts the nudge.

const val INSTAGRAM = "com.instagram.android"
const val YOUTUBE = "com.google.android.youtube"
const val NUDGE_COOLDOWN_MS = 30 * MIN

/** An app coming on screen ([resumed]) or leaving it, from UsageStatsManager events. */
data class AppEvent(val pkg: String, val resumed: Boolean, val at: Long)

/** Any other app coming forward, or the screen turning off: ends every watched app's time on screen. */
const val OTHER_APP = "*"

/** [recentMs]: on-screen time within the last window (twice the limit); counted even when not on screen now. */
data class Usage(val onScreen: Boolean, val recentMs: Long, val todayMs: Long)

/** Rolling window for a limit: 5 min of use within the last 10, 10 within 20, … */
fun windowFor(limitMs: Long) = 2 * limitMs

/** [events] since midnight, oldest first; other apps' events are ignored. */
fun usage(events: List<AppEvent>, pkg: String, now: Long, windowMs: Long): Usage {
    val spans = mutableListOf<LongArray>() // [from, to]
    var openFrom: Long? = null
    for (e in events) {
        val mine = e.pkg == pkg
        if (!mine && !(e.pkg == OTHER_APP && e.resumed)) continue
        if (mine && e.resumed) { if (openFrom == null) openFrom = e.at }
        else openFrom?.let { spans += longArrayOf(it, e.at); openFrom = null }
    }
    val onScreen = openFrom != null
    openFrom?.let { spans += longArrayOf(it, now) }
    val from = now - windowMs
    val recent = spans.sumOf { (maxOf(it[1], from) - maxOf(it[0], from)).coerceAtLeast(0) }
    return Usage(onScreen, recent, spans.sumOf { it[1] - it[0] })
}

/**
 * When to look again. Not open: 5 min. Open: the soonest the limit could be reached, i.e. after the
 * missing minutes (plus a random bit); past it, again in a minute.
 */
fun nextCheckMs(u: Usage, limitMs: Long, jitterMs: Long): Long = when {
    !u.onScreen -> 5 * MIN
    u.recentMs < limitMs -> limitMs - u.recentMs + jitterMs
    else -> 1 * MIN
}

fun shouldNudge(u: Usage, limitMs: Long, lastNudgeAt: Long, now: Long, watching: Boolean): Boolean =
    u.onScreen && u.recentMs >= limitMs && now - lastNudgeAt >= NUDGE_COOLDOWN_MS && !watching

/**
 * YouTube playing a real video or music: its media session is PLAYING with a title (Shorts report
 * state NONE and no title). A known length of 3 min or less still counts as a Short.
 */
fun isWatching(playing: Boolean, title: String?, durationMs: Long): Boolean =
    playing && !title.isNullOrBlank() && (durationMs <= 0 || durationMs > 3 * MIN)
