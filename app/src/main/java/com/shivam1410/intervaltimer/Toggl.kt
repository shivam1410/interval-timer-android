package com.shivam1410.intervaltimer

import java.time.Instant
import java.time.temporal.ChronoUnit

// Pure Toggl Track logic (no Android), unit-tested in TogglTest. TogglSync does the HTTP and storage.

/** Tag-mapping key for work blocks; activities use their own id. */
const val TOGGL_WORK = "work"
/** Mapping value meaning "don't send this one to Toggl". */
const val TOGGL_SKIP = "-"

/** What should be running in Toggl right now. [key] identifies it, so an unchanged phase isn't restarted. */
data class Track(val key: String, val description: String, val tags: List<String>, val start: Long)

/** Tag-mapping key prefix for routines ("routine:<id>"); also starts their entries' keys. */
const val TOGGL_ROUTINE = "routine:"

/** A running routine's entry, once you've picked a tag (or "No tag") for it; not sent until then, or when skipped. */
fun routineTrack(r: Routine, startedAt: Long, mapping: Map<String, String>): Track? {
    val m = mapping[TOGGL_ROUTINE + r.id] ?: return null
    if (m == TOGGL_SKIP) return null
    return Track("$TOGGL_ROUTINE${r.id}:$startedAt", r.name, listOfNotNull(m.takeIf { it.isNotBlank() }), startedAt)
}

/** Ambient sounds (Refresh) are background, not an activity: never sent. */
fun togglable(a: Activity) = a.group != "Refresh"

fun sessionKey(startedAt: Long, index: Int, what: String) = "$startedAt:$index:$what"

/**
 * Work blocks and break activities are tracked; prepare, pauses and ambient sounds aren't.
 * Tags come from [mapping] (picked in Settings); library items carry their own tag.
 */
fun desiredTrack(plan: List<Phase>, s: Session, chosen: Activity, mapping: Map<String, String>): Track? {
    if (!s.running(plan) || s.paused) return null
    fun tags(v: String?) = listOfNotNull(v?.takeIf { it.isNotBlank() && it != TOGGL_SKIP })
    return when (plan[s.index].kind) {
        Kind.PREP -> null
        Kind.WORK -> mapping[TOGGL_WORK].let { m ->
            if (m == TOGGL_SKIP) null else Track(sessionKey(s.startedAt, s.index, TOGGL_WORK), "Work", tags(m), s.runFrom)
        }
        Kind.ACTIVITY -> {
            val m = mapping[chosen.id]
            if (!togglable(chosen) || m == TOGGL_SKIP) null
            else Track(sessionKey(s.startedAt, s.index, chosen.id), chosen.name, tags(if (chosen.id.startsWith("lib:")) chosen.group else m), s.runFrom)
        }
    }
}

/** A time entry as this phone knows it; [remoteId] is set once Toggl has it. */
data class TogglEntry(
    val localId: String,
    val remoteId: Long?,
    val key: String,
    val description: String,
    val tags: List<String>,
    val start: Long,
    val stop: Long?,
    val stopSent: Boolean = false,
    val discard: Boolean = false,
)

/** Stops whatever runs when it no longer matches [desired] (at [now]) and adds the new entry. */
fun reconcile(entries: List<TogglEntry>, desired: Track?, now: Long, newId: String): List<TogglEntry> {
    val open = entries.lastOrNull { it.stop == null }
    if (desired != null && open?.key == desired.key) return entries
    val stopped = entries.map { if (it.stop == null) it.copy(stop = maxOf(now, it.start)) else it }
    return if (desired == null) stopped
    else stopped + TogglEntry(newId, null, desired.key, desired.description, desired.tags, desired.start, null)
}

/** Discard on the End dialog: that session's entries are deleted from Toggl too. */
fun discardSession(entries: List<TogglEntry>, startedAt: Long): List<TogglEntry> =
    entries.map { if (it.key.startsWith("$startedAt:")) it.copy(discard = true) else it }

enum class TogglOp { START, CREATE_DONE, STOP, DELETE, DONE }

/**
 * A phase has to last this long before it is worth a line in Toggl. Two minutes of prepare, a break you
 * cut short, a timer started by mistake — none of those are time you spent on anything.
 */
const val TOGGL_MIN_MS = 5 * MIN

/**
 * The next API call for an entry, or null when there is nothing to do yet. DONE = forget it.
 *
 * A phase is held back until it has run [TOGGL_MIN_MS]: nothing short ever reaches Toggl, rather than
 * appearing there and being deleted a minute later. Once it is old enough the entry is created with
 * its true start, so the gap costs nothing but the first few minutes of a running timer.
 */
fun nextOp(e: TogglEntry, now: Long): TogglOp? = when {
    e.discard -> if (e.remoteId != null) TogglOp.DELETE else TogglOp.DONE
    // Never sent, and over before it was worth sending.
    e.remoteId == null && e.stop != null ->
        if (e.stop - e.start >= TOGGL_MIN_MS) TogglOp.CREATE_DONE else TogglOp.DONE
    // Never sent and still running: wait until it has lasted long enough to be worth a line.
    e.remoteId == null -> if (now - e.start >= TOGGL_MIN_MS) TogglOp.START else null
    e.stop == null -> null
    !e.stopSent -> TogglOp.STOP
    else -> TogglOp.DONE
}

/** When [e] becomes worth sending, for a flush scheduled to catch it; null if it never will be. */
fun maturesAt(e: TogglEntry): Long? =
    if (!e.discard && e.remoteId == null && e.stop == null) e.start + TOGGL_MIN_MS else null

/** RFC 3339 in UTC, whole seconds, as Toggl's API takes it. */
fun togglTime(ms: Long): String = Instant.ofEpochMilli(ms).truncatedTo(ChronoUnit.SECONDS).toString()
