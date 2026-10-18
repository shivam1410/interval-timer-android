package com.shivam1410.intervaltimer

// Pure nudge logic (no Android), unit-tested in NudgeTest. NudgeService reads usage and posts the nudge.

const val INSTAGRAM = "com.instagram.android"
const val YOUTUBE = "com.google.android.youtube"
const val NUDGE_COOLDOWN_MS = 30 * MIN
const val NUDGE_GAP_MS = 2 * MIN

/** An app coming on screen ([resumed]) or leaving it, from UsageStatsManager events. */
data class AppEvent(val pkg: String, val resumed: Boolean, val at: Long)

/** Any other app coming forward, or the screen turning off: ends every watched app's time on screen. */
const val OTHER_APP = "*"

/** [sessionMs]: on-screen time since the app was last away for longer than the gap; 0 when not on screen. */
data class Usage(val onScreen: Boolean, val sessionMs: Long, val todayMs: Long)

/** [events] since midnight, oldest first; other apps' events are ignored. */
fun usage(events: List<AppEvent>, pkg: String, now: Long, gapMs: Long): Usage {
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
    val today = spans.sumOf { it[1] - it[0] }
    if (!onScreen) return Usage(false, 0, today)
    // Walk back from the open span, joining spans separated by short trips elsewhere.
    var session = 0L
    var nextStart = Long.MAX_VALUE
    for (s in spans.asReversed()) {
        if (nextStart != Long.MAX_VALUE && nextStart - s[1] > gapMs) break
        session += s[1] - s[0]
        nextStart = s[0]
    }
    return Usage(true, session, today)
}

/** When to look again: when the limit would run out (plus a random bit), or in 5 min if the app isn't open. */
fun nextCheckMs(u: Usage, limitMs: Long, jitterMs: Long): Long = when {
    !u.onScreen -> 5 * MIN
    u.sessionMs < limitMs -> limitMs - u.sessionMs + jitterMs
    else -> 1 * MIN
}

fun shouldNudge(u: Usage, limitMs: Long, lastNudgeAt: Long, now: Long, watching: Boolean): Boolean =
    u.onScreen && u.sessionMs >= limitMs && now - lastNudgeAt >= NUDGE_COOLDOWN_MS && !watching

/**
 * YouTube playing a real video or music: its media session is PLAYING with a title (Shorts report
 * state NONE and no title). A known length of 3 min or less still counts as a Short.
 */
fun isWatching(playing: Boolean, title: String?, durationMs: Long): Boolean =
    playing && !title.isNullOrBlank() && (durationMs <= 0 || durationMs > 3 * MIN)
